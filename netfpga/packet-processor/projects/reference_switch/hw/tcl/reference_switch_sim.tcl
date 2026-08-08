#
# Copyright (c) 2015 Georgina Kalogeridou
# All rights reserved.
#
# This software was developed by Stanford University and the University of Cambridge Computer Laboratory 
# under National Science Foundation under Grant No. CNS-0855268,
# the University of Cambridge Computer Laboratory under EPSRC INTERNET Project EP/H040536/1 and
# by the University of Cambridge Computer Laboratory under DARPA/AFRL contract FA8750-11-C-0249 ("MRC2"), 
# as part of the DARPA MRC research programme.
#
# @NETFPGA_LICENSE_HEADER_START@
#
# Licensed to NetFPGA C.I.C. (NetFPGA) under one or more contributor
# license agreements.  See the NOTICE file distributed with this work for
# additional information regarding copyright ownership.  NetFPGA licenses this
# file to you under the NetFPGA Hardware-Software License, Version 1.0 (the
# "License"); you may not use this file except in compliance with the
# License.  You may obtain a copy of the License at:
#
#   http://www.netfpga-cic.org
#
# Unless required by applicable law or agreed to in writing, Work distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations under the License.
#
# @NETFPGA_LICENSE_HEADER_END@
#

# Set variables.
set design $::env(NF_PROJECT_NAME)
set top top_sim
set sim_top top_tb
set device  xc7vx690t-3-ffg1761
set proj_dir ./project_sim
# GAPL: kept separate from create_project.tcl's ./project so that running a simulation
# (whose Makefile target always cleans this dir first) doesn't destroy a synthesized/
# implemented bitstream project, and vice versa. Keep this in sync with hw/Makefile's
# simclean target if this ever changes.
set public_repo_dir $::env(SUME_FOLDER)/lib/hw/
set xilinx_repo_dir $::env(XILINX_PATH)/data/ip/xilinx/
set repo_dir ./ip_repo
set bit_settings $::env(CONSTRAINTS)/generic_bit.xdc 
set project_constraints $::env(NF_DESIGN_DIR)/hw/constraints/nf_sume_general.xdc
set nf_10g_constraints $::env(NF_DESIGN_DIR)/hw/constraints/nf_sume_10g.xdc


set test_name [lindex $argv 0] 

#####################################
# Read IP Addresses and export registers
#####################################
source $::env(NF_DESIGN_DIR)/hw/tcl/$::env(NF_PROJECT_NAME)_defines.tcl

# Build project.
#
# GAPL: reuse an existing sim project instead of paying for a full create_project +
# create_ip/generate_target of every sim IP + control_sub's block design on every single
# invocation - see hw/Makefile's simcleantestio (used by sim/simgui instead of simclean) for the
# other half of this: it no longer deletes project_sim/ip_repo between runs. Every create_ip block
# below is existence-guarded (`get_ips -quiet <name>`) so this is safe whether reusing or creating
# fresh. gapl_kernel_ip is the one deliberate exception - see its own comment below.
set project_file "$::env(NF_DESIGN_DIR)/hw/${proj_dir}/${design}.xpr"
if {[file exists $project_file]} {
    puts "GAPL: found existing sim project, reusing: $project_file"
    open_project $project_file
} else {
    create_project -name ${design} -force -dir "$::env(NF_DESIGN_DIR)/hw/${proj_dir}" -part ${device}
    set_property source_mgmt_mode DisplayOnly [current_project]
    set_property top ${top} [current_fileset]
    puts "Creating User Datapath reference project"

    create_fileset -constrset -quiet constraints
    add_files -fileset constraints -norecurse ${bit_settings}
    add_files -fileset constraints -norecurse ${project_constraints}
    add_files -fileset constraints -norecurse ${nf_10g_constraints}
    set_property is_enabled true [get_files ${project_constraints}]
    set_property is_enabled true [get_files ${bit_settings}]
    set_property is_enabled true [get_files ${project_constraints}]
}

# GAPL: unlike project_sim itself, ip_repo is NOT persisted across runs - it's just a disposable
# local mirror of lib/hw/ for Vivado's ip_repo_paths mechanism, and it's how a freshly-packaged
# gapl_kernel_ip (or any other core) actually reaches this project's IP catalog. Tried `-force`
# instead of delete+recreate first, on the assumption it'd make the copy idempotent against an
# already-populated ip_repo/ from a prior run - confirmed against real Vivado that it doesn't:
# `file copy -force` still errors ("file already exists") when a destination subdirectory of the
# same name already exists, the same nested-directory-merge quirk documented on preCleanPaths in
# netfpga/build.gradle.kts. Deleting first sidesteps it entirely, and is cheap (local disk copy).
file delete -force ${repo_dir}
file copy ${public_repo_dir}/ ${repo_dir}
set_property ip_repo_paths ${repo_dir} [current_fileset]

update_ip_catalog
if {[get_ips -quiet output_port_lookup_ip] eq ""} {
    create_ip -name switch_output_port_lookup -vendor NetFPGA -library NetFPGA -module_name output_port_lookup_ip
    set_property -dict [list CONFIG.C_BASEADDR $OUTPUT_PORT_LOOKUP_BASEADDR] [get_ips output_port_lookup_ip]
    set_property generate_synth_checkpoint false [get_files output_port_lookup_ip.xci]
    reset_target all [get_ips output_port_lookup_ip]
    generate_target all [get_ips output_port_lookup_ip]
}
if {[get_ips -quiet input_arbiter_ip] eq ""} {
    create_ip -name input_arbiter -vendor NetFPGA -library NetFPGA -module_name input_arbiter_ip
    set_property -dict [list CONFIG.C_BASEADDR $INPUT_ARBITER_BASEADDR] [get_ips input_arbiter_ip]
    set_property generate_synth_checkpoint false [get_files input_arbiter_ip.xci]
    reset_target all [get_ips input_arbiter_ip]
    generate_target all [get_ips input_arbiter_ip]
}
if {[get_ips -quiet output_queues_ip] eq ""} {
    create_ip -name output_queues -vendor NetFPGA -library NetFPGA -module_name output_queues_ip
    set_property -dict [list CONFIG.C_BASEADDR $OUTPUT_QUEUES_BASEADDR] [get_ips output_queues_ip]
    set_property generate_synth_checkpoint false [get_files output_queues_ip.xci]
    reset_target all [get_ips output_queues_ip]
    generate_target all [get_ips output_queues_ip]
}

#Add ID block
if {[get_ips -quiet identifier_ip] eq ""} {
    create_ip -name blk_mem_gen -vendor xilinx.com -library ip -version 8.4 -module_name identifier_ip
    set_property -dict [list CONFIG.Interface_Type {AXI4} CONFIG.AXI_Type {AXI4_Lite} CONFIG.AXI_Slave_Type {Memory_Slave} CONFIG.Use_AXI_ID {false} CONFIG.Load_Init_File {true} CONFIG.Coe_File {/../../../../../../create_ip/id_rom16x32.coe} CONFIG.Fill_Remaining_Memory_Locations {true} CONFIG.Remaining_Memory_Locations {DEADDEAD} CONFIG.Memory_Type {Simple_Dual_Port_RAM} CONFIG.Use_Byte_Write_Enable {true} CONFIG.Byte_Size {8} CONFIG.Assume_Synchronous_Clk {true} CONFIG.Write_Width_A {32} CONFIG.Write_Depth_A {1024} CONFIG.Read_Width_A {32} CONFIG.Operating_Mode_A {READ_FIRST} CONFIG.Write_Width_B {32} CONFIG.Read_Width_B {32} CONFIG.Operating_Mode_B {READ_FIRST} CONFIG.Enable_B {Use_ENB_Pin} CONFIG.Register_PortA_Output_of_Memory_Primitives {false} CONFIG.Register_PortB_Output_of_Memory_Primitives {false} CONFIG.Use_RSTB_Pin {true} CONFIG.Reset_Type {ASYNC} CONFIG.Port_A_Write_Rate {50} CONFIG.Port_B_Clock {100} CONFIG.Port_B_Enable_Rate {100}] [get_ips identifier_ip]
    set_property generate_synth_checkpoint false [get_files identifier_ip.xci]
    reset_target all [get_ips identifier_ip]
    generate_target all [get_ips identifier_ip]
}

if {[get_ips -quiet clk_wiz_ip] eq ""} {
    create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name clk_wiz_ip
    set_property -dict [list CONFIG.PRIM_IN_FREQ {200.00} CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {100.000} CONFIG.USE_SAFE_CLOCK_STARTUP {true} CONFIG.RESET_TYPE {ACTIVE_LOW} CONFIG.CLKIN1_JITTER_PS {50.0} CONFIG.CLKOUT1_DRIVES {BUFGCE} CONFIG.CLKOUT2_DRIVES {BUFGCE} CONFIG.CLKOUT3_DRIVES {BUFGCE} CONFIG.CLKOUT4_DRIVES {BUFGCE} CONFIG.CLKOUT5_DRIVES {BUFGCE} CONFIG.CLKOUT6_DRIVES {BUFGCE} CONFIG.CLKOUT7_DRIVES {BUFGCE} CONFIG.MMCM_CLKFBOUT_MULT_F {5.000} CONFIG.MMCM_CLKIN1_PERIOD {5.0} CONFIG.MMCM_CLKOUT0_DIVIDE_F {10.000} CONFIG.RESET_PORT {resetn} CONFIG.CLKOUT1_JITTER {98.146} CONFIG.CLKOUT1_PHASE_ERROR {89.971}] [get_ips clk_wiz_ip]
    set_property generate_synth_checkpoint false [get_files clk_wiz_ip.xci]
    reset_target all [get_ips clk_wiz_ip]
    generate_target all [get_ips clk_wiz_ip]
}

if {[get_ips -quiet barrier_ip] eq ""} {
    create_ip -name barrier -vendor NetFPGA -library NetFPGA -module_name barrier_ip
    reset_target all [get_ips barrier_ip]
    generate_target all [get_ips barrier_ip]
}

if {[get_ips -quiet axis_sim_record_ip0] eq ""} {
    create_ip -name axis_sim_record -vendor NetFPGA -library NetFPGA -module_name axis_sim_record_ip0
    set_property -dict [list CONFIG.OUTPUT_FILE $::env(NF_DESIGN_DIR)/test/nf_interface_0_log.axi] [get_ips axis_sim_record_ip0]
    reset_target all [get_ips axis_sim_record_ip0]
    generate_target all [get_ips axis_sim_record_ip0]
}

if {[get_ips -quiet axis_sim_record_ip1] eq ""} {
    create_ip -name axis_sim_record -vendor NetFPGA -library NetFPGA -module_name axis_sim_record_ip1
    set_property -dict [list CONFIG.OUTPUT_FILE $::env(NF_DESIGN_DIR)/test/nf_interface_1_log.axi] [get_ips axis_sim_record_ip1]
    reset_target all [get_ips axis_sim_record_ip1]
    generate_target all [get_ips axis_sim_record_ip1]
}

if {[get_ips -quiet axis_sim_record_ip2] eq ""} {
    create_ip -name axis_sim_record -vendor NetFPGA -library NetFPGA -module_name axis_sim_record_ip2
    set_property -dict [list CONFIG.OUTPUT_FILE $::env(NF_DESIGN_DIR)/test/nf_interface_2_log.axi] [get_ips axis_sim_record_ip2]
    reset_target all [get_ips axis_sim_record_ip2]
    generate_target all [get_ips axis_sim_record_ip2]
}

if {[get_ips -quiet axis_sim_record_ip3] eq ""} {
    create_ip -name axis_sim_record -vendor NetFPGA -library NetFPGA -module_name axis_sim_record_ip3
    set_property -dict [list CONFIG.OUTPUT_FILE $::env(NF_DESIGN_DIR)/test/nf_interface_3_log.axi] [get_ips axis_sim_record_ip3]
    reset_target all [get_ips axis_sim_record_ip3]
    generate_target all [get_ips axis_sim_record_ip3]
}

if {[get_ips -quiet axis_sim_record_ip4] eq ""} {
    create_ip -name axis_sim_record -vendor NetFPGA -library NetFPGA -module_name axis_sim_record_ip4
    set_property -dict [list CONFIG.OUTPUT_FILE $::env(NF_DESIGN_DIR)/test/dma_0_log.axi] [get_ips axis_sim_record_ip4]
    reset_target all [get_ips axis_sim_record_ip4]
    generate_target all [get_ips axis_sim_record_ip4]
}

if {[get_ips -quiet axis_sim_stim_ip0] eq ""} {
    create_ip -name axis_sim_stim -vendor NetFPGA -library NetFPGA -module_name axis_sim_stim_ip0
    set_property -dict [list CONFIG.input_file $::env(NF_DESIGN_DIR)/test/nf_interface_0_stim.axi] [get_ips axis_sim_stim_ip0]
    generate_target all [get_ips axis_sim_stim_ip0]
}

if {[get_ips -quiet axis_sim_stim_ip1] eq ""} {
    create_ip -name axis_sim_stim -vendor NetFPGA -library NetFPGA -module_name axis_sim_stim_ip1
    set_property -dict [list CONFIG.input_file $::env(NF_DESIGN_DIR)/test/nf_interface_1_stim.axi] [get_ips axis_sim_stim_ip1]
    generate_target all [get_ips axis_sim_stim_ip1]
}

if {[get_ips -quiet axis_sim_stim_ip2] eq ""} {
    create_ip -name axis_sim_stim -vendor NetFPGA -library NetFPGA -module_name axis_sim_stim_ip2
    set_property -dict [list CONFIG.input_file $::env(NF_DESIGN_DIR)/test/nf_interface_2_stim.axi] [get_ips axis_sim_stim_ip2]
    generate_target all [get_ips axis_sim_stim_ip2]
}

if {[get_ips -quiet axis_sim_stim_ip3] eq ""} {
    create_ip -name axis_sim_stim -vendor NetFPGA -library NetFPGA -module_name axis_sim_stim_ip3
    set_property -dict [list CONFIG.input_file $::env(NF_DESIGN_DIR)/test/nf_interface_3_stim.axi] [get_ips axis_sim_stim_ip3]
    generate_target all [get_ips axis_sim_stim_ip3]
}

if {[get_ips -quiet axis_sim_stim_ip4] eq ""} {
    create_ip -name axis_sim_stim -vendor NetFPGA -library NetFPGA -module_name axis_sim_stim_ip4
    set_property -dict [list CONFIG.input_file $::env(NF_DESIGN_DIR)/test/dma_0_stim.axi] [get_ips axis_sim_stim_ip4]
    generate_target all [get_ips axis_sim_stim_ip4]
}

if {[get_ips -quiet axi_sim_transactor_ip] eq ""} {
    create_ip -name axi_sim_transactor -vendor NetFPGA -library NetFPGA -module_name axi_sim_transactor_ip
    set_property -dict [list CONFIG.STIM_FILE $::env(NF_DESIGN_DIR)/test/reg_stim.axi CONFIG.EXPECT_FILE $::env(NF_DESIGN_DIR)/test/reg_expect.axi CONFIG.LOG_FILE $::env(NF_DESIGN_DIR)/test/reg_stim.log] [get_ips axi_sim_transactor_ip]
    reset_target all [get_ips axi_sim_transactor_ip]
    generate_target all [get_ips axi_sim_transactor_ip]
}

update_ip_catalog

# GAPL: control_sub is a static block design (MicroBlaze control subsystem), independent of the
# selected GAPL application - guarded the same way as the create_ip blocks above, since
# control_sub_sim.tcl's own create_bd_design would error on a reused project that already has it.
# Checks the same thing control_sub_sim.tcl's own vendored boilerplate checks internally
# (get_files -quiet ${design_name}.bd, see its own "USE CASES" comment) - get_bd_designs was tried
# first and confirmed against real Vivado NOT to reflect a block design already on disk in a
# reopened project (it errored with "Design <control_sub> already exists" despite the guard).
if {[get_files -quiet control_sub.bd] eq ""} {
    source $::env(NF_DESIGN_DIR)/hw/tcl/control_sub_sim.tcl
}

read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/axi_clocking.v"

# util/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/first_null_index.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/copy_into_empty.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/modular_add.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/population_count.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/last_one_detector.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/one_hot_to_count.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/find_last_bit.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/ones_complement_addition.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/ones_complement_sum.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/processor_controller.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/reverse_bytes.v"

# util/datatype/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/datatype/reverse_byte_order.v"

# util/masking/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/masking/byte_to_bit_mask.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/masking/apply_byte_mask.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/masking/generate_mask.v"

# util/variable_width_queue/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/variable_width_queue/variable_width_queue.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/variable_width_queue/fallthrough_variable_width_queue.v"

# util/axis/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_1_to_3_manifold.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_3_to_1_arbiter.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_data_width_converter.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_flattener.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_mask_back.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_queue.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_pad_output.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_transmission_splitter.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_transmission_combiner.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_parser.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_trim_front.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_user_tracker.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/axis/axis_mutual_exclusion.v"

# util/network_header/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/network_header/eth_hdr_constructor.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/network_header/ip_hdr_constructor.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/util/network_header/udp_hdr_constructor.v"

# packet_processor/
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/packet_processor/packet_parser.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/packet_processor/packet_packer.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/packet_processor/packet_processor.v"

# GAPL: Ensure gradle.properties, reference_switch_sim.tcl, create_project.tcl are updated together
#
# Mirrors create_project.tcl's gapl_kernel_ip creation - packet_processor.v instantiates the
# packaged IP by that name (see its own comment at the instantiation site), not gapl_wrapper
# directly, so just reading gapl_wrapper.v raw (as this file used to do) leaves "gapl_kernel_ip"
# undefined and elaboration fails with "Module <gapl_kernel_ip> not found". This was missed when
# the kernel got packaged as an IP for create_project.tcl's synthesis-checkpoint flow.
#
# Unlike every other IP above, this one is NOT existence-guarded before reset_target/generate_target
# - its content varies per GAPL application (packageCoreGaplKernel in netfpga/build.gradle.kts only
# reruns when the selected application actually changes), so on a reused project, skipping this the
# way the static IPs above are skipped would silently keep simulating whichever application's kernel
# was packaged when this IP was first created in this project - the same class of stale-kernel bug
# already found and fixed once for the hw build (brainstorming/netfpga-partial-synthesis.md, Phase
# 1). create_ip itself is still guarded (it errors if already present), but reset_target/
# generate_target always rerun to pick up whatever packageCoreGaplKernel most recently produced.
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/GAPLprocessor.v"
if {[get_ips -quiet gapl_kernel_ip] eq ""} {
    create_ip -name gapl_kernel -vendor GAPL -library GAPL -module_name gapl_kernel_ip
}
reset_target all [get_ips gapl_kernel_ip]
generate_target all [get_ips gapl_kernel_ip]

read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/nf_datapath.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/top_sim.v"
read_verilog "$::env(NF_DESIGN_DIR)/hw/hdl/top_tb.v"

update_compile_order -fileset sources_1
update_compile_order -fileset sim_1

set_property top ${sim_top} [get_filesets sim_1]
set_property include_dirs ${proj_dir} [get_filesets sim_1]
set_property simulator_language Mixed [current_project]
set_property verilog_define { {SIMULATION=1} } [get_filesets sim_1]
set_property -name xsim.more_options -value {-testplusarg TESTNAME=basic_test} -objects [get_filesets sim_1]
set_property runtime {} [get_filesets sim_1]
set_property target_simulator xsim [current_project]
set_property compxlib.compiled_library_dir {} [current_project]
set_property top_lib xil_defaultlib [get_filesets sim_1]
update_compile_order -fileset sim_1

# workaround to avoid invoking default python2 in vivado
unset env(PYTHONHOME)
set output [exec python3 $::env(NF_DESIGN_DIR)/test/${test_name}/run.py]
puts $output

launch_simulation -simset sim_1 -mode behavioral
run 100us




