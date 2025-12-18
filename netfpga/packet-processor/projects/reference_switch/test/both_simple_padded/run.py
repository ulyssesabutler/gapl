#!/usr/bin/env python3

#
# Copyright (c) 2015 University of Cambridge
# Copyright (c) 2015 Neelakandan Manihatty Bojan, Georgina Kalogeridou
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
# Author:
#        Modified by Neelakandan Manihatty Bojan, Georgina Kalogeridou

import logging
logging.getLogger("scapy.runtime").setLevel(logging.ERROR)

from NFTest import *
import sys
import os
from scapy.layers.all import Ether, IP, TCP
from reg_defines_reference_switch import *
import javaproperties

phy2loop0 = ('../connections/conn', [])
nftest_init(sim_loop = [], hw_config = [phy2loop0])
nftest_start()

routerMAC = []
routerIP = []
for i in range(4):
    routerMAC.append("00:ca:fe:00:00:0%d"%(i+1))
    routerIP.append("192.168.%s.40"%i)

with open("../../../../gradle.properties") as f:
    netfpgaProps = javaproperties.load(f)

programName = netfpgaProps.get("programName")

with open("../../../../src/" + programName + "/test.properties") as f:
    testProps = javaproperties.load(f)

test_inputs = testProps.get("testInputs")
print("Test Inputs: " + test_inputs)
test_inputs_hex_strings = [part.strip() for part in test_inputs.split(",") if part.strip()]
test_inputs_bodies = [bytes.fromhex(h) for h in test_inputs_hex_strings]

sent_pkts = []
for test_case in test_inputs_bodies:
    pkt = make_padded_UDP_pkt(src_MAC="aa:bb:cc:dd:ee:ff", dst_MAC=routerMAC[0],
                      EtherType=0x800, src_IP="192.168.0.1", dst_IP="192.168.1.1",
                      src_port=1, dest_port=1, body=test_case)

    pkt.time = ((1*(1e-8)) + (2e-6))
    sent_pkts.append(pkt)

nftest_send_phy('nf0', sent_pkts)


test_expected_outputs = testProps.get("testExpectedOutputs")
print("Test Expected Outputs: " + test_expected_outputs)
text_expected_outputs_hex_strings = [part.strip() for part in test_expected_outputs.split(",") if part.strip()]
test_expected_outputs_bodies = [bytes.fromhex(h) for h in text_expected_outputs_hex_strings]

expected_pkts = []
for test_case in test_expected_outputs_bodies:
    pkt = make_padded_UDP_pkt(src_MAC="aa:bb:cc:dd:ee:ff", dst_MAC=routerMAC[0],
                              EtherType=0x800, src_IP="192.168.0.1", dst_IP="192.168.1.1",
                              src_port=1, dest_port=1, body=test_case)

    pkt.time = ((1*(1e-8)) + (2e-6))
    expected_pkts.append(pkt)

# TODO: What packets are we expecting?
nftest_expect_phy('nf1', expected_pkts)
nftest_expect_phy('nf2', expected_pkts)
nftest_expect_phy('nf3', expected_pkts)

nftest_barrier()
nftest_finish([])




