/* INFERENCE REQUEST DATA PACKER
 * =============================
 * This module takes the result of the preprocessor (input through the packet body input stream) and
 * creates a stream of network packets that can be sent back into the nf_datapath pipeline.
 * 
 * The transmission metadata wires should have the correct values while tvalid is set for the first
 * AXI transmission of this image.
 *
 * On the contrary, the error wire should have the correct value on the last AXI transmission of this
 * image. That is, while tlast and tvalid are set.
 */
module packet_packer
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam PORT_WIDTH             = 16,

    localparam IP_LENGTH_WIDTH        = 16,
    localparam IP_ID_WIDTH            = 16,
    localparam UDP_LENGTH_WIDTH       = 16,

    localparam ETH_HDR_WIDTH          = 14 * 8,
    localparam IP_HDR_WIDTH           = 20 * 8,
    localparam UDP_HDR_WIDTH          = 8 * 8
) (
    // Global Ports
    input                                axis_aclk,
    input                                axis_resetn,

    // Module input
    input  [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_in,
    input  [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_in,

    input  [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_in,
    input  [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_in,
    input  [IP_ID_WIDTH - 1:0]           ip_id_in,
    input  [IP_LENGTH_WIDTH - 1:0]       ip_length_in,

    input  [PORT_WIDTH - 1:0]            src_port_in,
    input  [PORT_WIDTH - 1:0]            dest_port_in,
    input  [UDP_LENGTH_WIDTH - 1:0]      udp_length_in,

    input  [TDATA_WIDTH - 1:0]           packet_body_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]           packet_body_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]           packet_body_in_axis_tuser,
    input                                packet_body_in_axis_tvalid,
    output                               packet_body_in_axis_tready,
    input                                packet_body_in_axis_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0]           packet_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]           packet_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]           packet_out_axis_tuser,
    output                               packet_out_axis_tvalid,
    input                                packet_out_axis_tready,
    output                               packet_out_axis_tlast
);

    // 1. STATES

    // Registers
    reg [3:0]  state;

    // Next value
    reg [3:0]  state_next;

    // Define states
    localparam WAITING_STATE         = 0;
    localparam WRITING_ETH_HDR_STATE = 2;
    localparam WRITING_IP_HDR_STATE  = 3;
    localparam WRITING_UDP_HDR_STATE = 4;
    localparam WRITING_BODY_STATE    = 5;


    // 2. METADATA STORAGE
    
    // Create registers
    reg [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr;
    reg [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr;

    reg [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr;
    reg [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr;
    reg [IP_ID_WIDTH - 1:0]           ip_id;
    reg [IP_LENGTH_WIDTH - 1:0]       ip_length;

    reg [PORT_WIDTH - 1:0]            src_port;
    reg [PORT_WIDTH - 1:0]            dest_port;
    reg [UDP_LENGTH_WIDTH - 1:0]      udp_length;

    reg [TUSER_WIDTH - 1:0]           tuser;

    // Create next values
    reg [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_next;
    reg [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_next;

    reg [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_next;
    reg [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_next;
    reg [IP_ID_WIDTH - 1:0]           ip_id_next;
    reg [IP_LENGTH_WIDTH - 1:0]       ip_length_next;

    reg [PORT_WIDTH - 1:0]            src_port_next;
    reg [PORT_WIDTH - 1:0]            dest_port_next;
    reg [UDP_LENGTH_WIDTH - 1:0]      udp_length_next;

    reg [TUSER_WIDTH - 1:0]           tuser_next;

    // Capture input
    always @(*) begin
        // Metadata
        src_mac_addr_next    = src_mac_addr;
        dest_mac_addr_next   = dest_mac_addr;

        src_ip_addr_next     = src_ip_addr;
        dest_ip_addr_next    = dest_ip_addr;
        ip_id_next           = ip_id;
        ip_length_next       = ip_length;

        src_port_next        = src_port;
        dest_port_next       = dest_port;
        udp_length_next      = udp_length;

        tuser_next           = tuser;

        if (state == WAITING_STATE & packet_body_in_axis_tvalid & packet_body_in_axis_tready) begin
            // Metadata
            src_mac_addr_next    = src_mac_addr_in;
            dest_mac_addr_next   = dest_mac_addr_in;

            src_ip_addr_next     = src_ip_addr_in;
            dest_ip_addr_next    = dest_ip_addr_in;
            ip_id_next           = ip_id_in;
            ip_length_next       = ip_length_in;

            src_port_next        = src_port_in;
            dest_port_next       = dest_port_in;
            udp_length_next      = udp_length_in;

            tuser_next           = packet_body_in_axis_tuser;
        end
    end


    // 3. BODY QUEUE

    // Registers
    wire [TDATA_WIDTH - 1:0] packet_body_queue_axis_tdata;
    wire [TKEEP_WIDTH - 1:0] packet_body_queue_axis_tkeep;
    wire [TUSER_WIDTH - 1:0] packet_body_queue_axis_tuser;
    wire                     packet_body_queue_axis_tvalid;
    reg                      packet_body_queue_axis_tready;
    wire                     packet_body_queue_axis_tlast;

    // Queue
    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    body_queue
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .in_tdata(packet_body_in_axis_tdata),
        .in_tkeep(packet_body_in_axis_tkeep),
        .in_tuser(packet_body_in_axis_tuser),
        .in_tvalid(packet_body_in_axis_tvalid),
        .in_tready(packet_body_in_axis_tready),
        .in_tlast(packet_body_in_axis_tlast),

        .out_tdata(packet_body_queue_axis_tdata),
        .out_tkeep(packet_body_queue_axis_tkeep),
        .out_tuser(packet_body_queue_axis_tuser),
        .out_tvalid(packet_body_queue_axis_tvalid),
        .out_tready(packet_body_queue_axis_tready),
        .out_tlast(packet_body_queue_axis_tlast)
    );


    // 4. OUTPUT QUEUE

    // Output queue wires
    reg [TDATA_WIDTH - 1:0] output_queue_tdata;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser;
    reg                     output_queue_tvalid;
    wire                    output_queue_tready;
    reg                     output_queue_tlast;

    // Register next wires
    axis_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    output_queue
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .in_tdata(output_queue_tdata),
        .in_tkeep(output_queue_tkeep),
        .in_tuser(output_queue_tuser),
        .in_tvalid(output_queue_tvalid),
        .in_tready(output_queue_tready),
        .in_tlast(output_queue_tlast),

        .out_tdata(packet_out_axis_tdata),
        .out_tkeep(packet_out_axis_tkeep),
        .out_tuser(packet_out_axis_tuser),
        .out_tvalid(packet_out_axis_tvalid),
        .out_tready(packet_out_axis_tready),
        .out_tlast(packet_out_axis_tlast)
    );


    // 5. CONSTRUCT HEADERS

    // Create the headers
    wire [ETH_HDR_WIDTH - 1:0] eth_hdr;
    wire [IP_HDR_WIDTH - 1:0]  ip_hdr;
    wire [UDP_HDR_WIDTH - 1:0] udp_hdr;

    // UDP Header
    udp_hdr_constructor construct_udp_hdr
    (
        .src_port(src_port),
        .dest_port(dest_port),
        .udp_length(udp_length),

        .udp_hdr(udp_hdr)
    );

    // IP Header
    ip_hdr_constructor construct_ip_hdr
    (
        .src_ip_addr(src_ip_addr),
        .dest_ip_addr(dest_ip_addr),

        .ip_length(ip_length),
        .id(ip_id),

        .ip_hdr(ip_hdr)
    );

    // Ethernet Header
    eth_hdr_constructor construct_eth_hdr
    (
        .src_mac_addr(src_mac_addr),
        .dest_mac_addr(dest_mac_addr),
        .eth_hdr(eth_hdr)
    );


    // 6. WRITE TO OUTPUT QUEUE

    always @(*) begin
        output_queue_tdata    = 0;
        output_queue_tkeep    = 0;
        output_queue_tuser    = 0;
        output_queue_tlast    = 0;
        output_queue_tvalid   = 0;

        packet_body_queue_axis_tready = 0;

        case (state)

            WRITING_ETH_HDR_STATE: begin
                output_queue_tdata    = { {(TDATA_WIDTH - ETH_HDR_WIDTH){1'h0}}, eth_hdr };
                output_queue_tkeep    = { {(TKEEP_WIDTH - (ETH_HDR_WIDTH / 8)){1'h0}}, {(ETH_HDR_WIDTH / 8){1'h1}} };
                output_queue_tuser    = tuser;
                output_queue_tlast    = 0;
                output_queue_tvalid   = 1;
            end

            WRITING_IP_HDR_STATE: begin
                output_queue_tdata    = { {(TDATA_WIDTH - IP_HDR_WIDTH){1'h0}}, ip_hdr };
                output_queue_tkeep    = { {(TKEEP_WIDTH - (IP_HDR_WIDTH / 8)){1'h0}}, {(IP_HDR_WIDTH / 8){1'h1}} };
                output_queue_tuser    = 0;
                output_queue_tlast    = 0;
                output_queue_tvalid   = 1;
            end

            WRITING_UDP_HDR_STATE: begin
                output_queue_tdata    = { {(TDATA_WIDTH - UDP_HDR_WIDTH){1'h0}}, udp_hdr };
                output_queue_tkeep    = { {(TKEEP_WIDTH - (UDP_HDR_WIDTH / 8)){1'h0}}, {(UDP_HDR_WIDTH / 8){1'h1}} };
                output_queue_tuser    = 0;
                output_queue_tlast    = 0;
                output_queue_tvalid   = 1;
            end

            WRITING_BODY_STATE: begin
                output_queue_tdata    = packet_body_queue_axis_tdata;
                output_queue_tkeep    = packet_body_queue_axis_tkeep;
                output_queue_tuser    = 0;
                output_queue_tlast    = packet_body_queue_axis_tlast;
                output_queue_tvalid   = packet_body_queue_axis_tvalid;

                packet_body_queue_axis_tready = output_queue_tready;
            end

        endcase
    end

    
    // 7. UPDATE STATE

    always @(*) begin
        state_next = state;

        case (state)

            WAITING_STATE: begin
                if (packet_body_in_axis_tvalid & packet_body_in_axis_tready) begin
                    state_next = WRITING_ETH_HDR_STATE;
                end
            end

            WRITING_ETH_HDR_STATE: begin
                if (output_queue_tvalid & output_queue_tready) begin
                    state_next = WRITING_IP_HDR_STATE;
                end
            end

            WRITING_IP_HDR_STATE: begin
                if (output_queue_tvalid & output_queue_tready) begin
                    state_next = WRITING_UDP_HDR_STATE;
                end
            end

            WRITING_UDP_HDR_STATE: begin
                if (output_queue_tvalid & output_queue_tready) begin
                    state_next = WRITING_BODY_STATE;
                end
            end

            WRITING_BODY_STATE: begin
                if (output_queue_tvalid & output_queue_tready & output_queue_tlast) begin
                    state_next = WAITING_STATE;
                end
            end

        endcase
    end

    // 8. WRITE REGISTERS

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            // State
            state <= 0;

            // Packet metadata
            src_mac_addr  <= 0;
            dest_mac_addr <= 0;

            src_ip_addr   <= 0;
            dest_ip_addr  <= 0;
            ip_id         <= 0;
            ip_length     <= 0;

            src_port      <= 0;
            dest_port     <= 0;
            udp_length    <= 0;

            tuser         <= 0;
        end else begin
            // State
            state <= state_next;

            // Packet metadata
            src_mac_addr  <= src_mac_addr_next;
            dest_mac_addr <= dest_mac_addr_next;

            src_ip_addr   <= src_ip_addr_next;
            dest_ip_addr  <= dest_ip_addr_next;
            ip_id         <= ip_id_next;
            ip_length     <= ip_length_next;

            src_port      <= src_port_next;
            dest_port     <= dest_port_next;
            udp_length    <= udp_length_next;

            tuser         <= tuser_next;
        end
    end

endmodule