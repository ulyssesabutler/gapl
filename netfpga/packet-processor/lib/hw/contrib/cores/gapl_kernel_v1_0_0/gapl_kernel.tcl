# GAPL kernel IP packaging script
#
# Packages gapl_wrapper.v (static AXI-Stream boundary - see
# projects/reference_switch/hw/hdl/gapl_wrapper.v) together with the currently-installed,
# per-application kernel Verilog (hdl/kernel/) as one Vivado IP-XACT core.
#
# Unlike every other core in this tree, this one is meant to be instantiated with
# generate_synth_checkpoint left enabled (see create_project.tcl's create_ip call for it) - Vivado
# synthesizes it once and caches the checkpoint, reused on every top-level build where
# the kernel hasn't changed.
#
# Measured (see gapl_kernel_ip_synth_1/runme.log): this synthesis step itself only takes ~2 minutes
# - despite the kernel Verilog's large file size, it is NOT the dominant cost of a ~30-40 minute build.
# The NetFPGA control_sub block design (PCIe hard IP, MicroBlaze, AXI crossbars, DMA - 30+ IP
# sub-runs) dominates instead. Caching the kernel alone caps the achievable win at ~2 minutes; it
# does not make switching applications meaningfully fast on its own. Caching control_sub (the
# static, non-GAPL side) is the change that would actually matter - see brainstorming/todo.md.

set design     gapl_kernel
# gapl_wrapper (GAPL kernels) or hls_wrapper (HLS kernels) - chosen per kernel type by
# packageCoreGaplKernel. Both have identical ports, so the packaged IP's interface doesn't change.
set top        [expr {[info exists ::env(KERNEL_WRAPPER_TOP)] ? $::env(KERNEL_WRAPPER_TOP) : "gapl_wrapper"}]
set device     xc7vx690t-3-ffg1761
set proj_dir   ./ip_proj
set ip_version 1.00
set lib_name   GAPL

create_project -name ${design} -force -dir "./${proj_dir}" -part ${device} -ip
set_property source_mgmt_mode All [current_project]
set_property top ${top} [current_fileset]
set_property ip_repo_paths $::env(SUME_FOLDER)/lib/hw/ [current_fileset]
puts "Creating GAPL Kernel IP"

update_ip_catalog

# The kernel is whatever packageCoreGaplKernel copied into hdl/kernel/ (the installed kernel
# directory, see installedKernelDir in netfpga/build.gradle.kts) - read all of it rather than
# naming a file, so a kernel spread over several files needs no change here. Sorted only so
# Vivado sees the same order every run.
set kernel_sources [lsort [glob -nocomplain "./hdl/kernel/*.v"]]
if {[llength $kernel_sources] == 0} {
    error "No kernel Verilog found in ./hdl/kernel/ - run packageCoreGaplKernel through Gradle"
}
foreach kernel_source $kernel_sources {
    read_verilog $kernel_source
}
# The wrapper plus the static NetFPGA infra utility modules it instantiates internally - this IP's
# synthesis is its own isolated scope, so it needs its own copies. packageCoreGaplKernel rebuilds
# hdl/ from scratch with exactly the selected wrapper's files, so everything here belongs.
set wrapper_sources [lsort [glob -nocomplain "./hdl/*.v"]]
if {![file exists "./hdl/${top}.v"]} {
    error "${top}.v not found in ./hdl/ - run packageCoreGaplKernel through Gradle"
}
foreach wrapper_source $wrapper_sources {
    read_verilog $wrapper_source
}
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1

ipx::package_project
set_property name ${design} [ipx::current_core]
set_property library ${lib_name} [ipx::current_core]
set_property vendor_display_name {GAPL} [ipx::current_core]
set_property vendor {GAPL} [ipx::current_core]
set_property supported_families {{virtex7} {Production}} [ipx::current_core]
set_property taxonomy {{/GAPL}} [ipx::current_core]
set_property version ${ip_version} [ipx::current_core]
# A new, increasing revision on every packaging (it's only rerun when the kernel or wrapper
# changed). Without it every packaging is gapl_kernel 1.0 revision 1, so a project that already has
# gapl_kernel_ip (reference_switch_sim.tcl's reused project_sim, run_impl.tcl's hw/project) can't
# tell the core changed: upgrade_ip then reports "No IP was identified for upgrade" and keeps the old
# core's file list - harmless while only file contents changed between GAPL applications, but a
# GAPL <-> HLS switch changes which files exist (gapl_wrapper.v vs hls_wrapper.v, ...), and IP
# generation fails with "Failed to copy file ... it does not exist". Seconds since the epoch fits
# the revision's 32-bit integer until 2038.
set_property core_revision [clock seconds] [ipx::current_core]
set_property display_name ${design} [ipx::current_core]
set_property description {Compiled GAPL or HLS kernel, wrapped in the static AXI-Stream boundary} [ipx::current_core]

# axis_queue.v (one of the util/ dependencies above) instantiates fallthrough_small_fifo, an
# already-separately-packaged IP core (lib/hw/std/cores/fallthrough_small_fifo_v1_0_0/) - not raw
# source, so it needs the same subcore reference every other consumer of it uses (e.g.
# axis_fifo.tcl, nf_axis_converter.tcl), not a file copy. Only gapl_wrapper's dependencies include
# axis_queue.v (via processor_controller.v); hls_wrapper's don't, so it gets no subcore.
if {[file exists "./hdl/axis_queue.v"]} {
    ipx::add_subcore NetFPGA:NetFPGA:fallthrough_small_fifo:1.00 [ipx::get_file_groups xilinx_anylanguagesynthesis -of_objects [ipx::current_core]]
    ipx::add_subcore NetFPGA:NetFPGA:fallthrough_small_fifo:1.00 [ipx::get_file_groups xilinx_anylanguagebehavioralsimulation -of_objects [ipx::current_core]]
}

ipx::infer_user_parameters [ipx::current_core]

ipx::check_integrity [ipx::current_core]
ipx::save_core [ipx::current_core]
update_ip_catalog
close_project

file delete -force ${proj_dir}
