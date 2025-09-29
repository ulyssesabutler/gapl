#
# Copyright (c) 2015 Noa Zilberman
# Modified by Salvator Galea
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

# Vivado Launch Script
#### Change design settings here #######
set design $::env(NF_PROJECT_NAME) 
set top top
set device xc7vx690t-3-ffg1761
set proj_dir ./project
set public_repo_dir $::env(SUME_FOLDER)/lib/hw/
set xilinx_repo_dir $::env(XILINX_PATH)/data/ip/xilinx/
set repo_dir ./ip_repo
set bit_settings $::env(CONSTRAINTS)/generic_bit.xdc 
set project_constraints ./constraints/nf_sume_general.xdc
set nf_10g_constraints ./constraints/nf_sume_10g.xdc

#####################################
# Read IP Addresses and export registers
#####################################
source ./tcl/$::env(NF_PROJECT_NAME)_defines.tcl
source ./tcl/export_registers.tcl
#####################################
# set IP paths
#####################################
#####################################
# Project Settings
#####################################
create_project -name ${design} -force -dir "./${proj_dir}" -part ${device}
set_property source_mgmt_mode DisplayOnly [current_project]
set_property top ${top} [current_fileset]
puts "Creating User Datapath reference project"
#####################################
# Project Constraints
#####################################
create_fileset -constrset -quiet constraints
file copy ${public_repo_dir}/ ${repo_dir}
set_property ip_repo_paths ${repo_dir} [current_fileset]
add_files -fileset constraints -norecurse ${bit_settings}
add_files -fileset constraints -norecurse ${project_constraints}
add_files -fileset constraints -norecurse ${nf_10g_constraints}
set_property is_enabled true [get_files ${project_constraints}]
set_property is_enabled true [get_files ${bit_settings}]
set_property is_enabled true [get_files ${nf_10g_constraints}]
set_property constrset constraints [get_runs synth_1]
set_property constrset constraints [get_runs impl_1]
 
#####################################
# Project 
#####################################
update_ip_catalog
create_ip -name switch_output_port_lookup -vendor NetFPGA -library NetFPGA -module_name output_port_lookup_ip
set_property generate_synth_checkpoint false [get_files output_port_lookup_ip.xci]
reset_target all [get_ips output_port_lookup_ip]
generate_target all [get_ips output_port_lookup_ip]
create_ip -name input_arbiter -vendor NetFPGA -library NetFPGA -module_name input_arbiter_ip
set_property generate_synth_checkpoint false [get_files input_arbiter_ip.xci]
reset_target all [get_ips input_arbiter_ip]
generate_target all [get_ips input_arbiter_ip]
create_ip -name output_queues -vendor NetFPGA -library NetFPGA -module_name output_queues_ip
set_property generate_synth_checkpoint false [get_files output_queues_ip.xci]
reset_target all [get_ips output_queues_ip]
generate_target all [get_ips output_queues_ip]


#create the IPI Block Diagram
source ./tcl/control_sub.tcl

source ./create_ip/nf_10ge_interface.tcl
create_ip -name nf_10ge_interface -vendor NetFPGA -library NetFPGA -module_name nf_10g_interface_ip
set_property generate_synth_checkpoint false [get_files nf_10g_interface_ip.xci]
reset_target all [get_ips nf_10g_interface_ip]
generate_target all [get_ips nf_10g_interface_ip]


source ./create_ip/nf_10ge_interface_shared.tcl
create_ip -name nf_10ge_interface_shared -vendor NetFPGA -library NetFPGA -module_name nf_10g_interface_shared_ip
set_property generate_synth_checkpoint false [get_files nf_10g_interface_shared_ip.xci]
reset_target all [get_ips nf_10g_interface_shared_ip]
generate_target all [get_ips nf_10g_interface_shared_ip]
 
#Add a clock wizard
create_ip -name clk_wiz -vendor xilinx.com -library ip -version 6.0 -module_name clk_wiz_ip
set_property -dict [list CONFIG.PRIM_IN_FREQ {200.00} CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {100.000} CONFIG.USE_SAFE_CLOCK_STARTUP {true} CONFIG.RESET_TYPE {ACTIVE_LOW} CONFIG.CLKIN1_JITTER_PS {50.0} CONFIG.CLKOUT1_DRIVES {BUFGCE} CONFIG.CLKOUT2_DRIVES {BUFGCE} CONFIG.CLKOUT3_DRIVES {BUFGCE} CONFIG.CLKOUT4_DRIVES {BUFGCE} CONFIG.CLKOUT5_DRIVES {BUFGCE} CONFIG.CLKOUT6_DRIVES {BUFGCE} CONFIG.CLKOUT7_DRIVES {BUFGCE} CONFIG.MMCM_CLKFBOUT_MULT_F {5.000} CONFIG.MMCM_CLKIN1_PERIOD {5.0} CONFIG.MMCM_CLKOUT0_DIVIDE_F {10.000} CONFIG.RESET_PORT {resetn} CONFIG.CLKOUT1_JITTER {98.146} CONFIG.CLKOUT1_PHASE_ERROR {89.971}] [get_ips clk_wiz_ip]
set_property generate_synth_checkpoint false [get_files clk_wiz_ip.xci]
reset_target all [get_ips clk_wiz_ip]
generate_target all [get_ips clk_wiz_ip]

create_ip -name proc_sys_reset -vendor xilinx.com -library ip -version 5.0 -module_name proc_sys_reset_ip
set_property -dict [list CONFIG.C_EXT_RESET_HIGH {0} CONFIG.C_AUX_RESET_HIGH {0}] [get_ips proc_sys_reset_ip]
set_property -dict [list CONFIG.C_NUM_PERP_RST {1} CONFIG.C_NUM_PERP_ARESETN {1}] [get_ips proc_sys_reset_ip]
set_property generate_synth_checkpoint false [get_files proc_sys_reset_ip.xci]
reset_target all [get_ips proc_sys_reset_ip]
generate_target all [get_ips proc_sys_reset_ip]


#Add ID block
create_ip -name blk_mem_gen -vendor xilinx.com -library ip -version 8.4 -module_name identifier_ip
set_property -dict [list CONFIG.Interface_Type {AXI4} CONFIG.AXI_Type {AXI4_Lite} CONFIG.AXI_Slave_Type {Memory_Slave} CONFIG.Use_AXI_ID {false} CONFIG.Load_Init_File {true} CONFIG.Coe_File {/../../../../../../create_ip/id_rom16x32.coe} CONFIG.Fill_Remaining_Memory_Locations {true} CONFIG.Remaining_Memory_Locations {DEADDEAD} CONFIG.Memory_Type {Simple_Dual_Port_RAM} CONFIG.Use_Byte_Write_Enable {true} CONFIG.Byte_Size {8} CONFIG.Assume_Synchronous_Clk {true} CONFIG.Write_Width_A {32} CONFIG.Write_Depth_A {4096} CONFIG.Read_Width_A {32} CONFIG.Operating_Mode_A {READ_FIRST} CONFIG.Write_Width_B {32} CONFIG.Read_Width_B {32} CONFIG.Operating_Mode_B {READ_FIRST} CONFIG.Enable_B {Use_ENB_Pin} CONFIG.Register_PortA_Output_of_Memory_Primitives {false} CONFIG.Register_PortB_Output_of_Memory_Primitives {false} CONFIG.Use_RSTB_Pin {true} CONFIG.Reset_Type {ASYNC} CONFIG.Port_A_Write_Rate {50} CONFIG.Port_B_Clock {100} CONFIG.Port_B_Enable_Rate {100}] [get_ips identifier_ip]
set_property generate_synth_checkpoint false [get_files identifier_ip.xci]
reset_target all [get_ips identifier_ip]
generate_target all [get_ips identifier_ip]



read_verilog "./hdl/axi_clocking.v"

read_verilog "./hdl/jpeg_decoder/jpeg_mcu_id.v"
read_verilog "./hdl/jpeg_decoder/jpeg_mcu_proc.v"
read_verilog "./hdl/jpeg_decoder/jpeg_bitbuffer.v"
read_verilog "./hdl/jpeg_decoder/jpeg_output_fifo.v"
read_verilog "./hdl/jpeg_decoder/jpeg_output_cx_ram.v"
read_verilog "./hdl/jpeg_decoder/jpeg_output_y_ram.v"
read_verilog "./hdl/jpeg_decoder/jpeg_output.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dqt.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_fifo.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_y.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_transpose_ram.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_transpose.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_x.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_ram_dp.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct_ram.v"
read_verilog "./hdl/jpeg_decoder/jpeg_idct.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dht_std_cx_ac.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dht_std_cx_dc.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dht_std_y_ac.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dht_std_y_dc.v"
read_verilog "./hdl/jpeg_decoder/jpeg_dht.v"
read_verilog "./hdl/jpeg_decoder/jpeg_input.v"
read_verilog "./hdl/jpeg_decoder/jpeg_core.v"

read_verilog "./hdl/jpeg_to_bitmap/jpeg_to_bitmap.v"

read_verilog "./hdl/util/first_null_index.v"
read_verilog "./hdl/util/copy_into_empty.v"
read_verilog "./hdl/util/byte_to_bit_mask.v"
read_verilog "./hdl/util/apply_byte_mask.v"
read_verilog "./hdl/util/modular_add.v"
read_verilog "./hdl/util/population_count.v"
read_verilog "./hdl/util/last_one_detector.v"
read_verilog "./hdl/util/one_hot_to_count.v"
read_verilog "./hdl/util/find_last_bit.v"
read_verilog "./hdl/util/variable_width_queue.v"
read_verilog "./hdl/util/fallthrough_variable_width_queue.v"

read_verilog "./hdl/axis/axis_data_width_converter.v"
read_verilog "./hdl/axis/axis_flattener.v"
read_verilog "./hdl/axis/axis_queue_flattener.v"
read_verilog "./hdl/axis/axis_transmission_splitter.v"
read_verilog "./hdl/axis/axis_transmission_combiner.v"

read_verilog "./hdl/tensor_scaler/uint8_to_float32.v"
read_verilog "./hdl/tensor_scaler/small_axis_image_to_tensor_scaler.v"
read_verilog "./hdl/tensor_scaler/image_to_tensor_scaler.v"
read_verilog "./hdl/tensor_scaler/image_to_tensor_splitter_scaler.v"

read_verilog "./hdl/channel/extract_channel_from_transmission.v"
read_verilog "./hdl/channel/extract_channel_from_transmission_with_offset.v"
read_verilog "./hdl/channel/extract_channel_from_stream.v"
read_verilog "./hdl/channel/color_channel.v"

read_verilog "./hdl/reshape/manifold.v"
read_verilog "./hdl/reshape/arbiter.v"
read_verilog "./hdl/reshape/reshaper.v"

read_verilog "./hdl/packet_processor/packet_splitter.v"
read_verilog "./hdl/packet_processor/packet_combiner.v"
read_verilog "./hdl/packet_processor/packet_constructor.v"
read_verilog "./hdl/packet_processor/network_packet_processor.v"

read_verilog "./hdl/nf_datapath.v"
read_verilog "./hdl/top.v"

exit