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
# GAPL: no reset_run here - Vivado's own run staleness tracking (source/constraint/IP content
# hashes since the last successful synth_1) already makes launch_runs a fast no-op when nothing
# changed. Force-resetting unconditionally on every build was throwing that away.
launch_runs synth_1 -jobs ${jobs}
wait_on_run synth_1

exit