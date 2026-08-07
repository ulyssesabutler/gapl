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

# GAPL: this script is now only ever invoked by Gradle's makeSynthShell task (see
# netfpga/build.gradle.kts), which only reruns when something in the *static* shell changed - never
# for a pure GAPL application switch. The gapl_kernel_ip .xci/output-products refresh that used to
# live here (for the case of switching applications on an already-created project) moved to
# run_impl.tcl, since that's the script now guaranteed to run on every build regardless of whether
# the shell itself needed resynthesizing.
#
# GAPL: no unconditional reset_run here - Vivado's own run staleness tracking (source/constraint/IP
# content hashes since the last successful synth_1) already makes launch_runs a fast no-op when
# nothing changed within a single already-open project. Force-resetting unconditionally on every
# build was throwing that away. But launch_runs will hard-error ("needs to be reset before
# launching") rather than silently reusing stale output when Vivado's own NEEDS_REFRESH tracking
# is set - and since this script now only runs when Gradle's makeSynthShell already determined the
# static shell changed, that's an expected case here (not the "nothing changed" case the no-op path
# above exists for), so reset when Vivado itself says a reset is required.
if {[get_property NEEDS_REFRESH [get_runs synth_1]]} {
    puts "GAPL: synth_1 needs a reset (Vivado's own staleness tracking) - resetting before relaunch"
    reset_run synth_1
}
launch_runs synth_1 -jobs ${jobs}
wait_on_run synth_1
gapl_fail_if_run_failed synth_1

exit