#
# Copyright (c) 2020 Marcin Wójcik
# All rights reserved.
#
# This software was developed by the University of Cambridge Computer
# Laboratory and supported by the UK's Engineering and Physical Sciences
# Research Council (EPSRC) under the EARL: sdn EnAbled MeasuRement for alL
# project (Project Reference EP/P025374/1).
#
# @NETFPGA_LICENSE_HEADER_START@
#
# Licensed to NetFPGA C.I.C. (NetFPGA) under one or more contributor
# license agreements. See the NOTICE file distributed with this work for
# additional information regarding copyright ownership. NetFPGA licenses this
# file to you under the NetFPGA Hardware-Software License, Version 1.0 (the
# "License"); you may not use this file except in compliance with the
# License. You may obtain a copy of the License at:
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
set jobs   [lindex $argv 1]

open_project project/${design}.xpr

# GAPL: incremental compile. synth_1 has no skip-if-unchanged behavior of its own (see
# brainstorming/todo.md's investigation), so impl_1 always sees a "new" netlist from synth_1
# regardless of whether the underlying logic actually changed - meaning without this, impl_1 would
# always do a full place & route from scratch too. Vivado's incremental_checkpoint reuses placement
# for logic that matches a previous routed design, even against a nominally-new netlist, which is
# exactly this situation. Stored outside the run directory (which gets recreated each time impl_1
# is relaunched) so it survives across builds.
set incremental_dcp_dir "../incremental"
set incremental_dcp "${incremental_dcp_dir}/last_routed.dcp"
if {[file exists $incremental_dcp]} {
    puts "GAPL: found previous routed checkpoint, using as incremental_checkpoint: $incremental_dcp"
    set_property incremental_checkpoint $incremental_dcp [get_runs impl_1]
} else {
    puts "GAPL: no previous routed checkpoint found - full place & route this time"
}

# GAPL: see run_synth.tcl - no unconditional reset_run here either, for the same reason.
launch_runs impl_1 -to_step write_bitstream -jobs ${jobs}
wait_on_run impl_1

# GAPL: preserve the routed checkpoint for the next build's incremental_checkpoint.
set routed_dcp_matches [glob -nocomplain "project/${design}.runs/impl_1/*_routed.dcp"]
if {[llength $routed_dcp_matches] > 0} {
    file mkdir $incremental_dcp_dir
    file copy -force [lindex $routed_dcp_matches 0] $incremental_dcp
    puts "GAPL: saved routed checkpoint for next build's incremental_checkpoint: $incremental_dcp"
} else {
    puts "GAPL: WARNING - no routed checkpoint found in impl_1's run directory, incremental_checkpoint won't be available next time"
}

exit
