//
// Copyright (c) 2015 University of Cambridge
// All rights reserved.
//
// This software was developed by
// Stanford University and the University of Cambridge Computer Laboratory
// under National Science Foundation under Grant No. CNS-0855268,
// the University of Cambridge Computer Laboratory under EPSRC INTERNET Project EP/H040536/1 and
// by the University of Cambridge Computer Laboratory under DARPA/AFRL contract FA8750-11-C-0249 ("MRC2"),
// as part of the DARPA MRC research programme.
//
// @NETFPGA_LICENSE_HEADER_START@
//
// Licensed to NetFPGA C.I.C. (NetFPGA) under one or more
// contributor license agreements.  See the NOTICE file distributed with this
// work for additional information regarding copyright ownership.  NetFPGA
// licenses this file to you under the NetFPGA Hardware-Software License,
// Version 1.0 (the "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at:
//
//   http://www.netfpga-cic.org
//
// Unless required by applicable law or agreed to in writing, Work distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations under the License.
//
// @NETFPGA_LICENSE_HEADER_END@
//
// Author        Sergio Lopez-Buedo (sergio@naudit.es)
// Revision      Jose Fernando Zazo Rollon (josefernando.zazo@naudit.es)

`timescale 1ns / 1ps
`default_nettype none

module pcie_controller #(
  parameter C_NUMBER_INTERFACES  = 1, // By default 2 engines: 1 S2C, 1 C2S (1 bidirectional interface)
  parameter C_USE_VIRTUALIZATION = 0, // If false (0), the number of devices MUST be set to 1
  parameter C_NUMBER_DEVICES     = 1
) (
  // Clock and Reset
  input  wire                                                  pcie_clk                    ,
  input  wire                                                  pcie_reset                  ,
  // Requester Interface
  output wire                                                  s_axis_rq_tlast             ,
  output wire [                                         255:0] s_axis_rq_tdata             ,
  output wire [                                          59:0] s_axis_rq_tuser             ,
  output wire [                                           7:0] s_axis_rq_tkeep             ,
  output wire                                                  s_axis_rq_tvalid            ,
  input  wire                                                  s_axis_rq_tready            ,
  input  wire [                                         255:0] m_axis_rc_tdata             ,
  input  wire [                                          74:0] m_axis_rc_tuser             ,
  input  wire                                                  m_axis_rc_tlast             ,
  input  wire [                                           7:0] m_axis_rc_tkeep             ,
  input  wire                                                  m_axis_rc_tvalid            ,
  output wire                                                  m_axis_rc_tready            ,
  // Completer Interface
  input  wire [                                         255:0] m_axis_cq_tdata             ,
  input  wire [                                          84:0] m_axis_cq_tuser             ,
  input  wire                                                  m_axis_cq_tlast             ,
  input  wire [                                           7:0] m_axis_cq_tkeep             ,
  input  wire                                                  m_axis_cq_tvalid            ,
  output wire                                                  m_axis_cq_tready            ,
  output wire [                                         255:0] s_axis_cc_tdata             ,
  output wire [                                          32:0] s_axis_cc_tuser             ,
  output wire                                                  s_axis_cc_tlast             ,
  output wire [                                           7:0] s_axis_cc_tkeep             ,
  output wire                                                  s_axis_cc_tvalid            ,
  input  wire                                                  s_axis_cc_tready            ,
  // MSI-x Interrupts
  input  wire [                                           1:0] cfg_interrupt_msix_enable   ,
  input  wire [                                           1:0] cfg_interrupt_msix_mask     ,
  input  wire [                                           5:0] cfg_interrupt_msix_vf_enable,
  input  wire [                                           5:0] cfg_interrupt_msix_vf_mask  ,
  output wire [                                          31:0] cfg_interrupt_msix_data     ,
  output wire [                                          63:0] cfg_interrupt_msix_address  ,
  output wire                                                  cfg_interrupt_msix_int      ,
  input  wire                                                  cfg_interrupt_msix_sent     ,
  input  wire                                                  cfg_interrupt_msix_fail     ,
  // Credits
  input  wire [                                           1:0] pcie_tfc_nph_av             ,
  input  wire [                                           1:0] pcie_tfc_npd_av             ,
  // AXI4-Lite Master Interface
  input  wire                                                  m_axi_lite_aclk             ,
  input  wire                                                  m_axi_lite_aresetn          ,
  input  wire                                                  m_axi_lite_arready          ,
  output wire                                                  m_axi_lite_arvalid          ,
  output wire [                                          31:0] m_axi_lite_araddr           ,
  output wire [                                           2:0] m_axi_lite_arprot           ,
  output wire                                                  m_axi_lite_rready           ,
  input  wire                                                  m_axi_lite_rvalid           ,
  input  wire [                                          31:0] m_axi_lite_rdata            ,
  input  wire [                                           1:0] m_axi_lite_rresp            ,
  input  wire                                                  m_axi_lite_awready          ,
  output wire                                                  m_axi_lite_awvalid          ,
  output wire [                                          31:0] m_axi_lite_awaddr           ,
  output wire [                                           2:0] m_axi_lite_awprot           ,
  input  wire                                                  m_axi_lite_wready           ,
  output wire                                                  m_axi_lite_wvalid           ,
  output wire [                                          31:0] m_axi_lite_wdata            ,
  output wire [                                           3:0] m_axi_lite_wstrb            ,
  output wire                                                  m_axi_lite_bready           ,
  input  wire                                                  m_axi_lite_bvalid           ,
  input  wire [                                           1:0] m_axi_lite_bresp            ,
  output wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] s2c_tvalid                  ,
  input  wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] s2c_tready                  ,
  output wire [256*(C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] s2c_tdata                   ,
  output wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] s2c_tlast                   ,
  output wire [ 32*(C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] s2c_tkeep                   ,
  output wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] c2s_tready                  ,
  input  wire [256*(C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] c2s_tdata                   ,
  input  wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] c2s_tlast                   ,
  input  wire [    (C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] c2s_tvalid                  ,
  input  wire [ 32*(C_NUMBER_INTERFACES*C_NUMBER_DEVICES)-1:0] c2s_tkeep
);

  wire [255:0] m0_axis_cq_tdata ;
  wire [ 84:0] m0_axis_cq_tuser ;
  wire         m0_axis_cq_tlast ;
  wire [ 31:0] m0_axis_cq_tkeep ;
  wire         m0_axis_cq_tvalid;
  wire         m0_axis_cq_tready;

  wire [255:0] m1_axis_cq_tdata ;
  wire [ 84:0] m1_axis_cq_tuser ;
  wire         m1_axis_cq_tlast ;
  wire [ 31:0] m1_axis_cq_tkeep ;
  wire         m1_axis_cq_tvalid;
  wire         m1_axis_cq_tready;

  axis_broadcaster_0 axis_broadcaster_0_i (
    .aclk         (pcie_clk                             ), // input wire aclk
    .aresetn      (~pcie_reset                          ), // input wire aresetn
    .s_axis_tvalid(m_axis_cq_tvalid                     ), // input wire s_axis_tvalid
    .s_axis_tready(m_axis_cq_tready                     ), // output wire s_axis_tready
    .s_axis_tdata (m_axis_cq_tdata                      ), // input wire [255 : 0] s_axis_tdata
    .s_axis_tkeep ({24'b0,m_axis_cq_tkeep}              ), // input wire [31 : 0] s_axis_tkeep
    .s_axis_tlast (m_axis_cq_tlast                      ), // input wire s_axis_tlast
    .s_axis_tuser (m_axis_cq_tuser                      ), // input wire [84 : 0] s_axis_tuser
    .m_axis_tvalid({m1_axis_cq_tvalid,m0_axis_cq_tvalid}), // output wire [1 : 0] m_axis_tvalid
    .m_axis_tready({m1_axis_cq_tready,m0_axis_cq_tready}), // input wire [1 : 0] m_axis_tready
    .m_axis_tdata ({m1_axis_cq_tdata,m0_axis_cq_tdata}  ), // output wire [511 : 0] m_axis_tdata
    .m_axis_tkeep ({m1_axis_cq_tkeep,m0_axis_cq_tkeep}  ), // output wire [63 : 0] m_axis_tkeep
    .m_axis_tlast ({m1_axis_cq_tlast,m0_axis_cq_tlast}  ), // output wire [1 : 0] m_axis_tlast
    .m_axis_tuser ({m1_axis_cq_tuser,m0_axis_cq_tuser}  )  // output wire [169 : 0] m_axis_tuser
  );


  wire [255:0] s0_axis_cc_tdata ;
  wire [ 32:0] s0_axis_cc_tuser ;
  wire         s0_axis_cc_tlast ;
  wire [  7:0] s0_axis_cc_tkeep ;
  wire         s0_axis_cc_tvalid;
  wire         s0_axis_cc_tready;

  wire [255:0] s1_axis_cc_tdata ;
  wire [ 32:0] s1_axis_cc_tuser ;
  wire         s1_axis_cc_tlast ;
  wire [  7:0] s1_axis_cc_tkeep ;
  wire         s1_axis_cc_tvalid;
  wire         s1_axis_cc_tready;

  wire [31:0] s_axis_cc_tkeep_big;


  wire        en  ;
  wire [23:0] addr;
  wire [63:0] dout;
  wire [63:0] din ;
  wire [ 7:0] we  ;
  wire        ack ;


  axis_switch_0 axis_switch_0_i (
    .aclk          (pcie_clk                                       ), // input wire aclk
    .aresetn       (~pcie_reset                                    ), // input wire aresetn
    .s_axis_tvalid ({s1_axis_cc_tvalid,s0_axis_cc_tvalid}          ), // input wire [1 : 0] s_axis_tvalid
    .s_axis_tready ({s1_axis_cc_tready,s0_axis_cc_tready}          ), // output wire [1 : 0] s_axis_tready
    .s_axis_tdata  ({s1_axis_cc_tdata,s0_axis_cc_tdata}            ), // input wire [511 : 0] s_axis_tdata
    .s_axis_tkeep  ({24'b0,s1_axis_cc_tkeep,24'b0,s0_axis_cc_tkeep}), // input wire [63 : 0] s_axis_tkeep
    .s_axis_tlast  ({s1_axis_cc_tlast,s0_axis_cc_tlast}            ), // input wire [1 : 0] s_axis_tlast
    .s_axis_tuser  ({s1_axis_cc_tuser,s0_axis_cc_tuser}            ), // input wire [65 : 0] s_axis_tuser
    .m_axis_tvalid (s_axis_cc_tvalid                               ), // output wire [0 : 0] m_axis_tvalid
    .m_axis_tready (s_axis_cc_tready                               ), // input wire [0 : 0] m_axis_tready
    .m_axis_tdata  (s_axis_cc_tdata                                ), // output wire [255 : 0] m_axis_tdata
    .m_axis_tkeep  (s_axis_cc_tkeep_big                            ), // output wire [31 : 0] m_axis_tkeep
    .m_axis_tlast  (s_axis_cc_tlast                                ), // output wire [0 : 0] m_axis_tlast
    .m_axis_tuser  (s_axis_cc_tuser                                ), // output wire [32 : 0] m_axis_tuser
    .s_req_suppress(2'b0                                           ), // input wire [1 : 0] s_req_suppress
    .s_decode_err  (                                               )  // output wire [1 : 0] s_decode_err
  );

  assign s_axis_cc_tkeep = s_axis_cc_tkeep_big[7:0];

  wire [31:0] m_axi_lite_awaddr_pcie;
  wire [31:0] m_axi_lite_araddr_pcie;
  pcie_to_axi_reg_requester pcie_to_axi_req_i (
    .user_clk          (pcie_clk              ),
    .reset_n           (~pcie_reset           ),

    .m_axis_cq_tdata   (m1_axis_cq_tdata      ),
    .m_axis_cq_tlast   (m1_axis_cq_tlast      ),
    .m_axis_cq_tvalid  (m1_axis_cq_tvalid     ),
    .m_axis_cq_tuser   (m1_axis_cq_tuser      ),
    .m_axis_cq_tkeep   (m1_axis_cq_tkeep[7:0] ),
    .m_axis_cq_tready  (m1_axis_cq_tready     ),

    .s_axis_cc_tdata   (s1_axis_cc_tdata      ),
    .s_axis_cc_tkeep   (s1_axis_cc_tkeep      ),
    .s_axis_cc_tlast   (s1_axis_cc_tlast      ),
    .s_axis_cc_tvalid  (s1_axis_cc_tvalid     ),
    .s_axis_cc_tuser   (s1_axis_cc_tuser      ),
    .s_axis_cc_tready  (s1_axis_cc_tready     ),

    .m_axi_lite_aclk   (m_axi_lite_aclk       ),
    .m_axi_lite_aresetn(m_axi_lite_aresetn    ),
    .md_error          (                      ),
    .m_axi_lite_arready(m_axi_lite_arready    ),
    .m_axi_lite_arvalid(m_axi_lite_arvalid    ),
    .m_axi_lite_araddr (m_axi_lite_araddr_pcie),
    .m_axi_lite_arprot (m_axi_lite_arprot     ),
    .m_axi_lite_rready (m_axi_lite_rready     ),
    .m_axi_lite_rvalid (m_axi_lite_rvalid     ),
    .m_axi_lite_rdata  (m_axi_lite_rdata      ),
    .m_axi_lite_rresp  (m_axi_lite_rresp      ),
    .m_axi_lite_awready(m_axi_lite_awready    ),
    .m_axi_lite_awvalid(m_axi_lite_awvalid    ),
    .m_axi_lite_awaddr (m_axi_lite_awaddr_pcie),
    .m_axi_lite_awprot (m_axi_lite_awprot     ),
    .m_axi_lite_wready (m_axi_lite_wready     ),
    .m_axi_lite_wvalid (m_axi_lite_wvalid     ),
    .m_axi_lite_wdata  (m_axi_lite_wdata      ),
    .m_axi_lite_wstrb  (m_axi_lite_wstrb      ),
    .m_axi_lite_bready (m_axi_lite_bready     ),
    .m_axi_lite_bvalid (m_axi_lite_bvalid     ),
    .m_axi_lite_bresp  (m_axi_lite_bresp      )
  );

  wire       [63:0] dout_dma, dout_translator;
  wire              ack_translator, ack_dma;
  localparam        c_translator_addr = 64'h00000000000001FF;
  localparam        c_translator_addr_width = 16;

  assign ack  = addr[c_translator_addr_width-1:0] == c_translator_addr[c_translator_addr_width-1:0] ? ack_translator  : ack_dma;
  assign dout = addr[c_translator_addr_width-1:0] == c_translator_addr[c_translator_addr_width-1:0] ? dout_translator : dout_dma;

  pcie_address_translator #(.C_TRANSLATOR_ADDR(c_translator_addr[c_translator_addr_width-1:0]),
                            .C_ADDR_WIDTH_IFACE(c_translator_addr_width)
  ) pcie_address_translator_i (
    .USER_CLK         (pcie_clk              ),
    .RESET_N          (~pcie_reset           ),
    .M_AXI_LITE_ACLK   (m_axi_lite_aclk      ),
    .M_AXI_LITE_ARESETN(m_axi_lite_aresetn   ),
    .M_AXI_LITE_AWADDR(m_axi_lite_awaddr_pcie),
    .M_AXI_LITE_ARADDR(m_axi_lite_araddr_pcie),
    .S_AXI_LITE_AWADDR(m_axi_lite_awaddr     ),
    .S_AXI_LITE_ARADDR(m_axi_lite_araddr     ),
    .S_MEM_IFACE_EN   (en                    ),
    .S_MEM_IFACE_ADDR (addr                  ),
    .S_MEM_IFACE_DOUT (dout_translator       ),
    .S_MEM_IFACE_DIN  (din                   ),
    .S_MEM_IFACE_WE   (we                    ),
    .S_MEM_IFACE_ACK  (ack_translator        )
  );

  pcie_completer_to_bram #(.BAR(0)) pcie_completer_to_bram_i (
    .user_clk        (pcie_clk             ), // input
    .reset_n         (~pcie_reset          ), // input

    .m_axis_cq_tdata (m0_axis_cq_tdata     ), // input  [255:0]
    .m_axis_cq_tlast (m0_axis_cq_tlast     ), // input
    .m_axis_cq_tvalid(m0_axis_cq_tvalid    ), // input
    .m_axis_cq_tuser (m0_axis_cq_tuser     ), // input  [84:0]
    .m_axis_cq_tkeep (m0_axis_cq_tkeep[7:0]), // input  [8:0]
    .m_axis_cq_tready(m0_axis_cq_tready    ), // output

    .s_axis_cc_tdata (s0_axis_cc_tdata     ), // output [255:0]
    .s_axis_cc_tkeep (s0_axis_cc_tkeep     ), // output [8:0]
    .s_axis_cc_tlast (s0_axis_cc_tlast     ), // output
    .s_axis_cc_tvalid(s0_axis_cc_tvalid    ), // output
    .s_axis_cc_tuser (s0_axis_cc_tuser     ), // output [32:0]
    .s_axis_cc_tready(s0_axis_cc_tready    ), // input

    .en              (en                   ), //  output reg
    .addr            (addr                 ), // output reg   [23:0]
    .dout            (dout                 ), // input wire   [63:0]
    .din             (din                  ), // output reg   [63:0]
    .we              (we                   ), // output reg   [7:0]
    .ack             (ack                  )  // input wire
  );



  wire [21:0] m_axis_rc_treadyarray;
  wire        s_axis_rq_treadybit  ;

  assign m_axis_rc_tready    = m_axis_rc_treadyarray[0];
  assign s_axis_rq_treadybit = s_axis_rq_tready && pcie_tfc_nph_av > 2'b01 && pcie_tfc_npd_av > 2'b01; // At least 1 credit.
  dma_sriov_top #(
    .C_NUMBER_INTERFACES    (C_NUMBER_INTERFACES ),
    .C_USE_VIRTUALIZATION   (C_USE_VIRTUALIZATION),
    .C_NUMBER_DEVICES       (C_NUMBER_DEVICES    ),
    .C_LOG2_MAX_PAYLOAD     (7                   ), //128
    .C_LOG2_MAX_READ_REQUEST(7                   )  //128
  ) dma_sriov_top_i (
    .CLK                         (pcie_clk                    ),
    .RST_N                       (~pcie_reset                 ),


    .M_AXIS_CQ_TDATA             (m0_axis_cq_tdata            ), // input  [255:0]
    .M_AXIS_CQ_TLAST             (m0_axis_cq_tlast            ), // input
    .M_AXIS_CQ_TVALID            (m0_axis_cq_tvalid           ), // input
    .M_AXIS_CQ_TUSER             (m0_axis_cq_tuser            ), // input  [84:0]
    .M_AXIS_CQ_TKEEP             (m0_axis_cq_tkeep[7:0]       ), // input  [8:0]
    .M_AXIS_CQ_TREADY            (m0_axis_cq_tready           ), // output


    .M_AXIS_RQ_TDATA             (s_axis_rq_tdata             ),
    .M_AXIS_RQ_TUSER             (s_axis_rq_tuser             ),
    .M_AXIS_RQ_TLAST             (s_axis_rq_tlast             ),
    .M_AXIS_RQ_TKEEP             (s_axis_rq_tkeep             ),
    .M_AXIS_RQ_TVALID            (s_axis_rq_tvalid            ),
    .M_AXIS_RQ_TREADY            ({4{s_axis_rq_treadybit}}    ),

    .S_AXIS_RC_TDATA             (m_axis_rc_tdata             ),
    .S_AXIS_RC_TUSER             (m_axis_rc_tuser             ),
    .S_AXIS_RC_TLAST             (m_axis_rc_tlast             ),
    .S_AXIS_RC_TKEEP             (m_axis_rc_tkeep             ),
    .S_AXIS_RC_TVALID            (m_axis_rc_tvalid            ),
    .S_AXIS_RC_TREADY            (m_axis_rc_treadyarray       ),


    .C2S_TVALID                  (c2s_tvalid                  ),
    .C2S_TREADY                  (c2s_tready                  ),
    .C2S_TDATA                   (c2s_tdata                   ),
    .C2S_TLAST                   (c2s_tlast                   ),
    .C2S_TKEEP                   (c2s_tkeep                   ),

    .S2C_TVALID                  (s2c_tvalid                  ),
    .S2C_TREADY                  (s2c_tready                  ),
    .S2C_TDATA                   (s2c_tdata                   ),
    .S2C_TLAST                   (s2c_tlast                   ),
    .S2C_TKEEP                   (s2c_tkeep                   ),

    .S_MEM_IFACE_EN              (en                          ),
    .S_MEM_IFACE_ADDR            (addr                        ),
    .S_MEM_IFACE_DOUT            (dout_dma                    ),
    .S_MEM_IFACE_DIN             (din                         ),
    .S_MEM_IFACE_WE              (we                          ),
    .S_MEM_IFACE_ACK             (ack_dma                     ),

    .cfg_interrupt_msix_enable   (cfg_interrupt_msix_enable   ), // input  wire [1:0]
    .cfg_interrupt_msix_mask     (cfg_interrupt_msix_mask     ), // input  wire [1:0]
    .cfg_interrupt_msix_vf_enable(cfg_interrupt_msix_vf_enable), // input  wire [5:0]
    .cfg_interrupt_msix_vf_mask  (cfg_interrupt_msix_vf_mask  ), // input  wire [5:0]
    .cfg_interrupt_msix_data     (cfg_interrupt_msix_data     ), // output reg  [31:0]
    .cfg_interrupt_msix_address  (cfg_interrupt_msix_address  ), // output wire [63:0]
    .cfg_interrupt_msix_int      (cfg_interrupt_msix_int      ), // output reg
    .cfg_interrupt_msix_sent     (cfg_interrupt_msix_sent     ), // input  wire
    .cfg_interrupt_msix_fail     (cfg_interrupt_msix_fail     )  // input  wire
  );




endmodule
