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

    localparam IP_BODY_LENGTH_WIDTH   = 16,
    localparam IP_ID_WIDTH            = 16,
    localparam UDP_BODY_LENGTH_WIDTH  = 16,

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

    input  [PORT_WIDTH - 1:0]            src_port_in,
    input  [PORT_WIDTH - 1:0]            dest_port_in,

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
    reg [15:0] image_packet_count;
    reg [15:0] packet_count;

    // Next value
    reg [3:0]  state_next;
    reg [15:0] image_packet_count_next;
    reg [15:0] packet_count_next;

    // Define states
    localparam WAITING_STATE         = 0;
    localparam READING_STATE         = 1;
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

    reg [PORT_WIDTH - 1:0]            src_port;
    reg [PORT_WIDTH - 1:0]            dest_port;

    // Create next values
    reg [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_next;
    reg [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_next;

    reg [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_next;
    reg [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_next;

    reg [PORT_WIDTH - 1:0]            src_port_next;
    reg [PORT_WIDTH - 1:0]            dest_port_next;

    // Capture input
    always @(*) begin
        // Metadata
        src_mac_addr_next    = src_mac_addr;
        dest_mac_addr_next   = dest_mac_addr;

        src_ip_addr_next     = src_ip_addr;
        dest_ip_addr_next    = dest_ip_addr;

        src_port_next        = src_port;
        dest_port_next       = dest_port;

        // Packet tracking
        image_packet_count_next = image_packet_count;
        packet_count_next       = packet_count;

        if (state == WAITING_STATE & packet_body_in_axis_tvalid) begin
            // Metadata
            src_mac_addr_next    = src_mac_addr_in;
            dest_mac_addr_next   = dest_mac_addr_in;

            src_ip_addr_next     = src_ip_addr_in;
            dest_ip_addr_next    = dest_ip_addr_in;

            src_port_next        = src_port_in;
            dest_port_next       = dest_port_in;

            // Packet tracking
            packet_count_next = packet_count + 1;

            if (
                src_mac_addr_next    == src_mac_addr_in  &
                dest_mac_addr_next   == dest_mac_addr_in &

                src_ip_addr_next     == src_ip_addr_in   &
                dest_ip_addr_next    == dest_ip_addr_in  &

                src_port_next        == src_port_in      &
                dest_port_next       == dest_port_in
            ) begin
                image_packet_count_next = image_packet_count + 1;
            end else begin
                image_packet_count_next = 1;
            end
        end
    end


    // 3. BODY BUFFER
    
    // Limit the number of items in the buffer so we can manually add a "packet last" flag that's different from the body last
    reg [15:0] items_in_buffer;
    reg [15:0] items_in_buffer_next;

    localparam BUFFER_ITEM_LIMIT = 30; // 960 bytes

    // Create the "packet last"
    wire packet_last = packet_body_in_axis_tlast | (items_in_buffer == BUFFER_ITEM_LIMIT);

    // Input queue wires
    wire [TDATA_WIDTH - 1:0] input_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] input_queue_tkeep;
    wire [TUSER_WIDTH - 1:0] input_queue_tuser;
    wire                     input_queue_tlast;
    wire                     input_queue_packet_last;

    wire                     write_to_input_queue;
    wire                     read_from_input_queue;

    wire                     input_queue_nearly_full;
    wire                     input_queue_empty;

    // Instantiate input queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1 + 1),
        .MAX_DEPTH_BITS(5) // This should allow us to store 1024 bytes of body data
    )
    input_queue
    (
        .din         ({packet_body_in_axis_tdata, packet_body_in_axis_tkeep, packet_body_in_axis_tuser, packet_body_in_axis_tlast, packet_last}),
        .wr_en       (write_to_input_queue),
        .rd_en       (read_from_input_queue),
        .dout        ({input_queue_tdata, input_queue_tkeep, input_queue_tuser, input_queue_tlast, input_queue_packet_last}),
        .full        (),
        .prog_full   (),
        .nearly_full (input_queue_nearly_full),
        .empty       (input_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Set input queue IO wires
    assign write_to_input_queue       = packet_body_in_axis_tvalid & packet_body_in_axis_tready;
    assign packet_body_in_axis_tready = ~input_queue_nearly_full & (state == READING_STATE);


    // 4. OUTPUT QUEUE

    // Output queue wires
    reg [TDATA_WIDTH - 1:0] output_queue_tdata;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser;
    reg                     output_queue_tlast;

    reg                     write_to_output_queue;
    wire                    read_from_output_queue;

    wire                    output_queue_nearly_full;
    wire                    output_queue_empty;

    // Register next wires
    reg [TDATA_WIDTH - 1:0] output_queue_tdata_next;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep_next;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser_next;
    reg                     output_queue_tlast_next;

    reg                     write_to_output_queue_next;

    // Instantiate input queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(5) // This should allow us to store 1024 bytes of body data
    )
    output_queue
    (
        .din         ({output_queue_tdata, output_queue_tkeep, output_queue_tuser, output_queue_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (read_from_output_queue),
        .dout        ({packet_out_axis_tdata, packet_out_axis_tkeep, packet_out_axis_tuser, packet_out_axis_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Set input queue IO wires
    assign read_from_output_queue = packet_out_axis_tvalid & packet_out_axis_tready;
    assign packet_out_axis_tvalid = ~output_queue_empty;


    // 5. TRACK BODY 

    // Registers to track current packet body
    reg        is_last_image_packet;
    reg [15:0] packet_body_length;
    reg [15:0] packet_body_sum;
    reg [15:0] transmissions_in_packet_body;

    // Next values
    reg        is_last_image_packet_next;
    reg [15:0] packet_body_length_next;
    reg [15:0] packet_body_sum_next;
    reg [15:0] transmissions_in_packet_body_next;

    // Compute the next values
    wire [15:0] packet_body_length_after_transmission;
    wire [15:0] packet_body_sum_after_transmission;

    // Compute the values of the current transmission
    wire [$clog2(TKEEP_WIDTH + 1) - 1:0] current_transmission_length;
    wire [15:0]                          current_transmission_sum;

    population_count #( .IN_WIDTH(TKEEP_WIDTH) ) current_transmission_byte_count
    (
        .in(packet_body_in_axis_tkeep),
        .population(current_transmission_length)
    );

    ones_complement_sum #( .OPERAND_COUNT(TKEEP_WIDTH / 2) ) sum_transmission_to_packet
    (
        .operands(packet_body_in_axis_tdata),
        .sum(current_transmission_sum)
    );

    // Now, add those to the overall packet
    assign packet_body_length_after_transmission = packet_body_length + current_transmission_length;

    ones_complement_addition ones_complement_add_transmission_to_packet
    (
        .operand_1(packet_body_sum),
        .operand_2(current_transmission_sum),

        .sum(packet_body_sum_after_transmission)
    );

    // Logic
    always @(*) begin
        items_in_buffer_next              = items_in_buffer;
        is_last_image_packet_next         = is_last_image_packet;
        packet_body_length_next           = packet_body_length;
        packet_body_sum_next              = packet_body_sum;
        transmissions_in_packet_body_next = transmissions_in_packet_body;

        if (state != READING_STATE & state_next == READING_STATE) begin
            // If we are resetting the module back to the reading state
            items_in_buffer_next              = 0;
            is_last_image_packet_next         = 0;
            packet_body_length_next           = 0;
            packet_body_sum_next              = 0;
            transmissions_in_packet_body_next = 0;
        end else if (write_to_input_queue) begin
            items_in_buffer_next              = items_in_buffer + 1;
            is_last_image_packet_next         = packet_body_in_axis_tlast;
            packet_body_length_next           = packet_body_length_after_transmission;
            packet_body_sum_next              = packet_body_sum_after_transmission;
            transmissions_in_packet_body_next = transmissions_in_packet_body + 1;
        end
    end

    // 6. CONSTRUCT HEADERS

    // Create the headers
    wire [ETH_HDR_WIDTH - 1:0] eth_hdr;
    wire [IP_HDR_WIDTH - 1:0]  ip_hdr;
    wire [UDP_HDR_WIDTH - 1:0] udp_hdr;

    // UDP Header
    wire [UDP_BODY_LENGTH_WIDTH - 1:0] udp_body_length = packet_body_length;

    udp_hdr_constructor construct_udp_hdr
    (
        .src_port(src_port),
        .dest_port(dest_port),
        .body_length(udp_body_length),

        .udp_hdr(udp_hdr)
    );

    // IP Header
    wire [IP_BODY_LENGTH_WIDTH - 1:0] ip_body_length = udp_body_length + (UDP_HDR_WIDTH / 8);
    wire [IP_ID_WIDTH - 1:0]          ip_id          = packet_count;

    ip_hdr_constructor construct_ip_hdr
    (
        .src_ip_addr(src_ip_addr),
        .dest_ip_addr(dest_ip_addr),

        .body_length(ip_body_length),
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


    // 7. WRITE TO OUTPUT QUEUE

    wire should_write_to_output_queue = ~input_queue_empty & ~output_queue_nearly_full;
    assign read_from_input_queue = should_write_to_output_queue & (state == WRITING_BODY_STATE);

    always @(*) begin
        output_queue_tdata_next    = 0;
        output_queue_tkeep_next    = 0;
        output_queue_tuser_next    = 0;
        output_queue_tlast_next    = 0;

        write_to_output_queue_next = 0;

        if (should_write_to_output_queue) begin
            case (state)

                WRITING_ETH_HDR_STATE: begin
                    output_queue_tdata_next    = { {(TDATA_WIDTH - ETH_HDR_WIDTH){1'h0}}, eth_hdr };
                    output_queue_tkeep_next    = { {(TKEEP_WIDTH - (ETH_HDR_WIDTH / 8)){1'h0}}, {(ETH_HDR_WIDTH / 8){1'h1}} };
                    output_queue_tuser_next    = 0;
                    output_queue_tlast_next    = 0;

                    write_to_output_queue_next = 1;
                end

                WRITING_IP_HDR_STATE: begin
                    output_queue_tdata_next    = { {(TDATA_WIDTH - IP_HDR_WIDTH){1'h0}}, ip_hdr };
                    output_queue_tkeep_next    = { {(TKEEP_WIDTH - (IP_HDR_WIDTH / 8)){1'h0}}, {(IP_HDR_WIDTH / 8){1'h1}} };
                    output_queue_tuser_next    = 0;
                    output_queue_tlast_next    = 0;

                    write_to_output_queue_next = 1;
                end

                WRITING_UDP_HDR_STATE: begin
                    output_queue_tdata_next    = { {(TDATA_WIDTH - UDP_HDR_WIDTH){1'h0}}, udp_hdr };
                    output_queue_tkeep_next    = { {(TKEEP_WIDTH - (UDP_HDR_WIDTH / 8)){1'h0}}, {(UDP_HDR_WIDTH / 8){1'h1}} };
                    output_queue_tuser_next    = 0;
                    output_queue_tlast_next    = 0;

                    write_to_output_queue_next = 1;
                end

                WRITING_BODY_STATE: begin
                    output_queue_tdata_next    = input_queue_tdata;
                    output_queue_tkeep_next    = input_queue_tkeep;
                    output_queue_tuser_next    = input_queue_tuser;
                    output_queue_tlast_next    = input_queue_packet_last;

                    write_to_output_queue_next = 1;
                end

            endcase
        end
    end

    
    // 8. UPDATE STATE

    always @(*) begin
        state_next              = state;
        image_packet_count_next = image_packet_count;
        packet_count_next       = packet_count;

        case (state)

            WAITING_STATE: begin
                if (packet_body_in_axis_tvalid) begin
                    state_next = READING_STATE;
                end
            end

            READING_STATE: begin
                if (write_to_input_queue & packet_last) begin
                    state_next = WRITING_ETH_HDR_STATE;
                end
            end

            WRITING_ETH_HDR_STATE: begin
                if (should_write_to_output_queue) begin
                    state_next = WRITING_IP_HDR_STATE;
                end
            end

            WRITING_IP_HDR_STATE: begin
                if (should_write_to_output_queue) begin
                    state_next = WRITING_UDP_HDR_STATE;
                end
            end

            WRITING_UDP_HDR_STATE: begin
                if (should_write_to_output_queue) begin
                    state_next = WRITING_BODY_STATE;
                end
            end

            WRITING_BODY_STATE: begin
                if (should_write_to_output_queue & input_queue_packet_last) begin
                    state_next = WAITING_STATE;
                end
            end

        endcase
    end

    // 9. WRITE REGISTERS

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            // State
            state              <= 0;
            image_packet_count <= 0;
            packet_count       <= 0;

            // Packet metadata
            src_mac_addr    <= 0;
            dest_mac_addr   <= 0;

            src_ip_addr     <= 0;
            dest_ip_addr    <= 0;

            src_port        <= 0;
            dest_port       <= 0;

            // Packet tracking
            items_in_buffer              <= 0;
            is_last_image_packet         <= 0;
            packet_body_length           <= 0;
            packet_body_sum              <= 0;
            transmissions_in_packet_body <= 0;

            // Output queue
            output_queue_tdata    <= 0;
            output_queue_tkeep    <= 0;
            output_queue_tuser    <= 0;
            output_queue_tlast    <= 0;

            write_to_output_queue <= 0;
        end else begin
            // State
            state              <= state_next;
            image_packet_count <= image_packet_count_next;
            packet_count       <= packet_count_next;

            // Packet metadata
            src_mac_addr    <= src_mac_addr_next;
            dest_mac_addr   <= dest_mac_addr_next;

            src_ip_addr     <= src_ip_addr_next;
            dest_ip_addr    <= dest_ip_addr_next;

            src_port        <= src_port_next;
            dest_port       <= dest_port_next;

            // Packet tracking
            items_in_buffer              <= items_in_buffer_next;
            is_last_image_packet         <= is_last_image_packet_next;
            packet_body_length           <= packet_body_length_next;
            packet_body_sum              <= packet_body_sum_next;
            transmissions_in_packet_body <= transmissions_in_packet_body_next;

            // Output queue
            output_queue_tdata    <= output_queue_tdata_next;
            output_queue_tkeep    <= output_queue_tkeep_next;
            output_queue_tuser    <= output_queue_tuser_next;
            output_queue_tlast    <= output_queue_tlast_next;

            write_to_output_queue <= write_to_output_queue_next;
        end
    end

endmodule