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

# GAPL: wait_on_run does NOT throw on a failed run - it just returns once the run finishes,
# regardless of pass/fail (confirmed against real Vivado: a failed impl_1 run produced only a
# WARNING, and the calling script went on to exit 0, silently reporting success to make/Gradle for
# a build that never actually completed). Every wait_on_run below must be followed by this check,
# or a real failure gets swallowed instead of failing the build.
proc gapl_fail_if_run_failed {run_name} {
    set progress [get_property PROGRESS [get_runs $run_name]]
    if {$progress ne "100%"} {
        error "GAPL: run '$run_name' did not complete successfully (progress: $progress) - see its runme.log"
    }
}

open_project project/${design}.xpr

# GAPL: identifier_ip's source .coe (create_ip/id_rom16x32.coe) embeds a fresh Unix timestamp on
# every `make identifier` invocation, by design (see create_project.tcl) - deliberately NOT
# refreshed here. Tried an explicit reset_target/generate_target + reset_run/launch_runs of
# identifier_ip_synth_1 (mirroring gapl_kernel_ip below), but confirmed against real Vivado this
# left the project in a broken state (synth_1's own top-level elaboration failed outright with
# "module 'identifier_ip' not found," worse than the stale-timestamp status quo) - the interaction
# wasn't understood well enough to trust under time pressure, so this is left as an explicit,
# low-risk trade-off instead: identifier_ip's embedded build-ID timestamp reflects whenever the
# project/shell was last fully (re)created, not literally "this exact build." It's an informational
# stamp, not load-bearing for GAPL kernel functionality or netfpga infra correctness - if that
# trade-off stops being acceptable, revisit why the refresh sequence above broke elaboration before
# reintroducing it.

# GAPL: refresh gapl_kernel_ip's own cached IP output products if the packaged core (which
# Gradle's packageCoreGaplKernel regenerates every time the selected application changes) is newer
# than what this project's .xci last generated from - otherwise the OOC sub-run below would
# resynthesize Vivado's locally-cached copy of the *previous* application's HDL, not the new one.
# Moved here from run_synth.tcl: with makeSynthShell now skipping synth_1 entirely on a pure
# application switch (see below), run_synth.tcl may not run at all, so this must happen wherever
# the application is actually guaranteed to be picked up - immediately before the sub-run relaunch.
# Must stay conditional, not unconditional, for the same reason it originally had to be in
# run_synth.tcl: regenerating output products touches timestamps/content even when nothing
# underneath actually changed, which cascades into unwanted resynthesis elsewhere if done blindly.
update_ip_catalog
if {[llength [get_ips gapl_kernel_ip -quiet]] > 0} {
    set gapl_kernel_component "$::env(SUME_FOLDER)/lib/hw/contrib/cores/gapl_kernel_v1_0_0/component.xml"
    set gapl_kernel_xci [get_property IP_FILE [get_ips gapl_kernel_ip]]
    if {[file mtime $gapl_kernel_component] > [file mtime $gapl_kernel_xci]} {
        puts "GAPL: gapl_kernel_ip's packaged core changed since this project's .xci was generated - refreshing"
        upgrade_ip [get_ips gapl_kernel_ip]
        reset_target all [get_ips gapl_kernel_ip]
        generate_target all [get_ips gapl_kernel_ip]
    } else {
        puts "GAPL: gapl_kernel_ip is already up to date with its packaged core - skipping refresh"
    }
}

# GAPL: makeSynthShell (see netfpga/build.gradle.kts) skips relaunching synth_1 - and therefore its
# ~12 min of static-shell resynthesis - whenever nothing in the static shell changed, reusing the
# existing synth_1/top.dcp (which still references gapl_kernel_ip as a checkpointed black box,
# exactly like a freshly-synthesized one would). That means the currently-installed application's
# GAPL kernel netlist is only guaranteed current if its own OOC sub-run is refreshed here,
# independently of synth_1 - confirmed against real Vivado that this sub-run can be
# reset/relaunched/waited on in complete isolation (synth_1's own STATUS is untouched by it), and
# that the impl_1 link_design step below picks up the refreshed checkpoint automatically with zero
# "could not resolve black box cell" warnings.
puts "GAPL: refreshing gapl_kernel_ip_synth_1 to pick up the currently installed application"
reset_run gapl_kernel_ip_synth_1
launch_runs gapl_kernel_ip_synth_1
wait_on_run gapl_kernel_ip_synth_1
gapl_fail_if_run_failed gapl_kernel_ip_synth_1

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

# GAPL: see run_synth.tcl - no unconditional reset_run here either, for the same reason, but the
# same NEEDS_REFRESH guard applies: relaunching gapl_kernel_ip_synth_1 above is expected to mark
# impl_1 stale (its netlist input just changed), and launch_runs hard-errors rather than silently
# reusing stale output when that's set - confirmed against real Vivado this happens even though
# impl_1's own STATUS still reads "Complete".
if {[get_property NEEDS_REFRESH [get_runs impl_1]]} {
    puts "GAPL: impl_1 needs a reset (Vivado's own staleness tracking) - resetting before relaunch"
    reset_run impl_1
}
launch_runs impl_1 -to_step write_bitstream -jobs ${jobs}
wait_on_run impl_1
gapl_fail_if_run_failed impl_1

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
