#!/usr/bin/env python3
#
# Copyright (c) 2015 University of Cambridge
# Copyright (c) 2015 Neelakandan Manihatty Bojan, Georgina Kalogeridou, Salvator Galea
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
from RegressRouterLib import *
import sys
import os
import random
from scapy.layers.all import Ether, IP, TCP
from reg_defines_reference_router import *

phy2loop0 = ('../connections/conn', [])
nftest_init(sim_loop = [], hw_config = [phy2loop0])
nftest_start()

if isHW():
	# asserting the reset_counter to 1 for clearing the registers
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_RESET(), 0x1)
	nftest_regwrite(SUME_INPUT_ARBITER_0_RESET(), 0x1)
	nftest_regwrite(SUME_OUTPUT_QUEUES_0_RESET(), 0x1)
	nftest_regwrite(SUME_NF_10G_INTERFACE_SHARED_0_RESET(), 0x1)
	nftest_regwrite(SUME_NF_10G_INTERFACE_1_RESET(), 0x1)
	nftest_regwrite(SUME_NF_10G_INTERFACE_2_RESET(), 0x1)
	nftest_regwrite(SUME_NF_10G_INTERFACE_3_RESET(), 0x1)

# Write and read command for the indirect register access
WR_IND_COM		= 0x0001
RD_IND_COM		= 0x0011
NUM_PORTS 		= 4

dest_MACs	= ["00:ca:fe:00:00:02", "00:ca:fe:00:00:01", "00:ca:fe:00:00:04", "00:ca:fe:00:00:03"]
routerMAC	= ["00:ca:fe:00:00:01", "00:ca:fe:00:00:02", "00:ca:fe:00:00:03", "00:ca:fe:00:00:04"]
routerIP 	= ["192.168.0.40", "192.168.1.40", "192.168.2.40", "192.168.3.40"]

ALLSPFRouters	= "224.0.0.5"

# Clear all tables in a hardware test (not needed in software)
if isHW():
	nftest_invalidate_all_tables()
else:
	simReg.regDelay(3000) # Give enough time to initiliaze all the memories

# Write the mac and IP addresses
for port in range(4):
	nftest_add_dst_ip_filter_entry (port, routerIP[port])
	nftest_set_router_MAC ('nf%d'%port, routerMAC[port])

nftest_add_dst_ip_filter_entry (4, ALLSPFRouters)

# router mac 0
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_0_HI(), 0xca)
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_0_LOW(), 0xfe000001)
# router mac 1
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_1_HI(), 0xca)
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_1_LOW(), 0xfe000002)
# router mac 2
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_2_HI(), 0xca)
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_2_LOW(), 0xfe000003)
# router mac 3
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_3_HI(), 0xca)
nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_MAC_3_LOW(), 0xfe000004)

# add LPM and ARP entries for each port
for i in range(NUM_PORTS):
	i_plus_1	= i + 1
	subnetIP 	= "192.168." + str(i_plus_1) + ".1"
	subnetMask	= "255.255.255.225"
	nextHopIP	= "192.168.5." + str(i_plus_1)
	outPort		= 1 << (2 * i)
	nextHopMAC	= dest_MACs[i]

	# add an entry in the routing table
	nftest_add_LPM_table_entry(i, subnetIP, subnetMask, nextHopIP, outPort)
	# add and entry in the ARP table
	nftest_add_ARP_table_entry(i, nextHopIP, nextHopMAC)


num = SUME_OUTPUT_PORT_LOOKUP_0_MEM_IP_ARP_CAM_DEPTH() - 1
# ARP table
mac_hi	= 0xca
mac_lo	= [0xfe000002, 0xfe000001, 0xfe000004, 0xfe000003]
router_ip = [0xc0a80501, 0xc0a80502, 0xc0a80503, 0xc0a80504]

for i in range(num):
	#nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), int("0x10000000",16) | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), SUME_OUTPUT_PORT_LOOKUP_0_MEM_IP_ARP_CAM_ADDRESS() | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTCOMMAND(), RD_IND_COM)
	# ARP MAC
	# |- -INDIRECTREPLY_A_HI 32bit- -INDIRECTREPLY_A_LOW 32bit- -INDIRECTREPLY_B_HI 32bit- -INDIRECTREPLY_B_LOW 32bit- -|
	# |-- 		mac_hi 		-- 		mac_lo 	      -- 	0x0000 		   -- 		IP    --|
	if i < 4:
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_HI(),	mac_hi)
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_LOW(),	mac_lo[i])
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_LOW(),	router_ip[i])
	else:
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_HI(),	0)
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_LOW(),	0)
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_LOW(),	0)

# Routing table
router_ip 	= [0xc0a80101, 0xc0a80201, 0xc0a80301, 0xc0a80401]
subnet_mask	= [0xffffffe1, 0xffffffe1, 0xffffffe1, 0xffffffe1]
arp_port	= [1, 4]
next_hop_ip	= [0xc0a80501, 0xc0a80502, 0xc0a80503, 0xc0a80504]

for i in range(num):
	#nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), int("0x00000000",16) | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), SUME_OUTPUT_PORT_LOOKUP_0_MEM_IP_LPM_TCAM_ADDRESS() | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTCOMMAND(), RD_IND_COM)
	# |- -INDIRECTREPLY_A_HI 32bit- -INDIRECTREPLY_A_LOW 32bit- -INDIRECTREPLY_B_HI 32bit- -INDIRECTREPLY_B_LOW 32bit- -|
	# |-- 		IP 		-- 	next_IP 	     -- 	mask 		 -- 	next_port      --|	
	if i < 2:
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_LOW(),	arp_port[i])
	if i < 4:
		# Router IP
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_HI(),	router_ip[i])
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_LOW(),	next_hop_ip[i])
		# Router subnet mask
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_HI(),	subnet_mask[i])
	else:
		# Router IP
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_HI(),	0)
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_A_LOW(),	0)
		# Router subnet mask
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_HI(),	0xffffffff)

# IP filter
num	= SUME_OUTPUT_PORT_LOOKUP_0_MEM_DEST_IP_CAM_DEPTH() - 1
filters	= [0xc0a80028, 0xc0a80128, 0xc0a80228, 0xc0a80328, 0xe0000005]

for i in range(num):
	#nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), int("0x20000000",16) | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTADDRESS(), SUME_OUTPUT_PORT_LOOKUP_0_MEM_DEST_IP_CAM_ADDRESS() | i)
	nftest_regwrite(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTCOMMAND(), RD_IND_COM)
	# |- -INDIRECTREPLY_A_HI 32bit- -INDIRECTREPLY_A_LOW 32bit- -INDIRECTREPLY_B_HI 32bit- -INDIRECTREPLY_B_LOW 32bit- -|
	# |-- 		0x0000 		-- 		0x0000 	      -- 	0x0000 		   -- 		IP     --|	
	if i < 5:
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_LOW(), filters[i])
	else:
		nftest_regread_expect(SUME_OUTPUT_PORT_LOOKUP_0_INDIRECTREPLY_B_LOW(), 0)


mres=[]
nftest_finish(mres)





