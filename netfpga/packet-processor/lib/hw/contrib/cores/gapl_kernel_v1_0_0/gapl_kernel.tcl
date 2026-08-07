# GAPL kernel IP packaging script
#
# Packages gapl_wrapper.v (static AXI-Stream boundary - see
# projects/reference_switch/hw/hdl/gapl_wrapper.v) together with the currently-installed,
# per-application GAPLprocessor.v as one Vivado IP-XACT core.
#
# Unlike every other core in this tree, this one is meant to be instantiated with
# generate_synth_checkpoint left enabled (see create_project.tcl's create_ip call for it) - Vivado
# synthesizes it once and caches the checkpoint, reused on every top-level build where
# GAPLprocessor.v hasn't changed.
#
# Measured (see gapl_kernel_ip_synth_1/runme.log): this synthesis step itself only takes ~2 minutes
# - despite GAPLprocessor.v's large file size, it is NOT the dominant cost of a ~30-40 minute build.
# The NetFPGA control_sub block design (PCIe hard IP, MicroBlaze, AXI crossbars, DMA - 30+ IP
# sub-runs) dominates instead. Caching the kernel alone caps the achievable win at ~2 minutes; it
# does not make switching applications meaningfully fast on its own. Caching control_sub (the
# static, non-GAPL side) is the change that would actually matter - see brainstorming/todo.md.

set design     gapl_kernel
set top        gapl_wrapper
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

read_verilog "./hdl/GAPLprocessor.v"
read_verilog "./hdl/gapl_wrapper.v"
# gapl_wrapper.v instantiates these static NetFPGA infra utility modules internally - this IP's
# synthesis is its own isolated scope, so it needs its own copies (see build.gradle.kts's
# packageCoreGaplKernel task, which copies them in alongside the two files above).
read_verilog "./hdl/axis_pad_output.v"
read_verilog "./hdl/axis_mutual_exclusion.v"
read_verilog "./hdl/axis_queue.v"
read_verilog "./hdl/processor_controller.v"
read_verilog "./hdl/reverse_bytes.v"
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
set_property display_name ${design} [ipx::current_core]
set_property description {Compiled GAPL kernel, wrapped in the static AXI-Stream boundary} [ipx::current_core]

# axis_queue.v (one of the util/ dependencies above) instantiates fallthrough_small_fifo, an
# already-separately-packaged IP core (lib/hw/std/cores/fallthrough_small_fifo_v1_0_0/) - not raw
# source, so it needs the same subcore reference every other consumer of it uses (e.g.
# axis_fifo.tcl, nf_axis_converter.tcl), not a file copy.
ipx::add_subcore NetFPGA:NetFPGA:fallthrough_small_fifo:1.00 [ipx::get_file_groups xilinx_anylanguagesynthesis -of_objects [ipx::current_core]]
ipx::add_subcore NetFPGA:NetFPGA:fallthrough_small_fifo:1.00 [ipx::get_file_groups xilinx_anylanguagebehavioralsimulation -of_objects [ipx::current_core]]

ipx::infer_user_parameters [ipx::current_core]

ipx::check_integrity [ipx::current_core]
ipx::save_core [ipx::current_core]
update_ip_catalog
close_project

file delete -force ${proj_dir}
