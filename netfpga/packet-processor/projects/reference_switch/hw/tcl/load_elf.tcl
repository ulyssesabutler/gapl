#
# Copyright (c) 2015 University of Cambridge
# All rights reserved.
#
#
#  Description:
#        Vivado TCL script to insert compiled elf files into the project
#        and associate it with the microblaze in the system. The script generates
#        bitstreams with microblaze BRAM initialized with the elf file.
#        useage:
#        $ vivado -source tcl/load_elf.tcl 
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


set design [lindex $argv 0]
set ws "SDK_Workspace"

# GAPL: wait_on_run does not throw on a failed run (see run_impl.tcl for how this was found to
# silently swallow a real failure) - check explicitly so a failed impl_1 relaunch here fails the
# build instead of falling through to write_bitstream against a stale/incomplete design.
proc gapl_fail_if_run_failed {run_name} {
    set progress [get_property PROGRESS [get_runs $run_name]]
    if {$progress ne "100%"} {
        error "GAPL: run '$run_name' did not complete successfully (progress: $progress) - see its runme.log"
    }
}

# open project
puts "\nOpening $design XPR project\n"
open_project project/$design.xpr

set bd_file [get_files -regexp -nocase {.*sub*.bd}]
set elf_file ../sw/embedded/$ws/$design/app/Debug/app.elf

puts "\nOpening $design BD project\n"
open_bd_design $bd_file

# insert elf if it is not inserted yet
# GAPL: this used to exit here once the ELF was associated (true after the very first build of a
# given project), skipping everything below - including write_bitstream ../bitfiles/$design.bit,
# the step that actually publishes the final bitstream. Confirmed directly: after that first
# build, bitfiles/reference_switch.bit stayed byte-for-byte identical across every subsequent
# rebuild regardless of how many times the selected application's logic actually changed,
# because the ELF (generic MicroBlaze software, not application-specific) never changes between
# GAPL app switches, so this check kept taking the early-exit path forever. The ELF-association
# skip only makes sense for the add_files step itself (adding it twice would be redundant/error),
# not for regenerating the bitstream - so only that part stays conditional now.
if {[llength [get_files app.elf]]} {
	puts "ELF File [get_files app.elf] is already associated"
} else {
	add_files -norecurse -force ${elf_file}
	set_property SCOPED_TO_REF [current_bd_design] [get_files -all -of_objects [get_fileset sources_1] ${elf_file}]
	set_property SCOPED_TO_CELLS nf_mbsys/mbsys/microblaze_0 [get_files -all -of_objects [get_fileset sources_1] ${elf_file}]
}

# Create bitstream with up-to-date elf files
reset_run impl_1 -prev_step
launch_runs impl_1 -to_step write_bitstream
wait_on_run impl_1
gapl_fail_if_run_failed impl_1
open_run impl_1
write_bitstream -force ../bitfiles/$design.bit

exit
