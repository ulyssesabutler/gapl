/* INFERENCE REQUEST PACKET PARSER
 * ===============================
 * This module takes a stream representing a packet as its input. It's output consists of the values for
 * each of the relevant header fields and a stream consisting of just the packet body (with no headers).
 */
module inference_request_packet_parser
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    localparam MAC_ADDRESS_WIDTH      = 48,
    localparam IP_ADDRESS_WIDTH       = 32,
    localparam PORT_WIDTH             = 16,
    localparam TRANSMISSION_ID_WIDTH  = 32,
    localparam CIP_SEQUENCE_NUM_WIDTH = 16,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                                 axis_aclk,
    input                                 axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0]            packet_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]            packet_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]            packet_in_axis_tuser,
    input                                 packet_in_axis_tvalid,
    output                                packet_in_axis_tready,
    input                                 packet_in_axis_tlast,

    // Module output
    output [MAC_ADDRESS_WIDTH - 1:0]      src_mac_addr_out,
    output [MAC_ADDRESS_WIDTH - 1:0]      dest_mac_addr_out,

    output [IP_ADDRESS_WIDTH - 1:0]       src_ip_addr_out,
    output [IP_ADDRESS_WIDTH - 1:0]       dest_ip_addr_out,

    output [PORT_WIDTH - 1:0]             src_port_out,
    output [PORT_WIDTH - 1:0]             dest_port_out,

    output [TRANSMISSION_ID_WIDTH - 1:0]  transmission_id_out,

    output [CIP_SEQUENCE_NUM_WIDTH - 1:0] sequence_number_out,
    output                                last_packet_out,

    output [TDATA_WIDTH - 1:0]            packet_body_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]            packet_body_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]            packet_body_out_axis_tuser,
    output                                packet_body_out_axis_tvalid,
    input                                 packet_body_out_axis_tready,
    output                                packet_body_out_axis_tlast
);


    // 1. STATES

    // Define state register
    reg state;

    // Define next value
    reg state_next;

    // Define states
    localparam READING_STATE = 0;
    localparam WAITING_STATE = 1;


    // 2. INPUT QUEUE

    // Input queue wires
    wire [TDATA_WIDTH - 1:0] input_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] input_queue_tkeep;
    wire [TUSER_WIDTH - 1:0] input_queue_tuser;
    wire                     input_queue_tlast;

    wire                     write_to_input_queue;
    wire                     read_from_input_queue;

    wire                     input_queue_nearly_full;
    wire                     input_queue_empty;

    // Instantiate input queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(4)
    )
    input_queue
    (
        .din         ({packet_in_axis_tdata, packet_in_axis_tkeep, packet_in_axis_tuser, packet_in_axis_tlast}),
        .wr_en       (write_to_input_queue),
        .rd_en       (read_from_input_queue),
        .dout        ({input_queue_tdata, input_queue_tkeep, input_queue_tuser, input_queue_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (input_queue_nearly_full),
        .empty       (input_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Set input queue IO wires
    assign write_to_input_queue  = packet_in_axis_tready & packet_in_axis_tvalid;
    assign packet_in_axis_tready = ~input_queue_nearly_full & (state == READING_STATE);


    // 3. PACKET BODY

    // Convenience wires (for consistent naming)
    wire [TDATA_WIDTH - 1:0] pretrim_tdata = input_queue_tdata;
    wire [TKEEP_WIDTH - 1:0] pretrim_tkeep = input_queue_tkeep;
    wire [TUSER_WIDTH - 1:0] pretrim_tuser = input_queue_tuser;
    wire                     pretrim_tlast = input_queue_tlast;

    // Additional wires
    wire pretrim_tready;
    wire pretrim_tvalid = ~input_queue_empty;

    wire trimmed_tready;

    assign read_from_input_queue = pretrim_tready & pretrim_tvalid;

    // Instantiate trim module
    axis_trim_front #( .BYTES_TRIMMED(14 + 20 + 8 + 8) ) trim_headers
    (
        .axis_aclk           (axis_aclk),
        .axis_resetn         (axis_resetn),

        .axis_original_tdata (pretrim_tdata),
        .axis_original_tkeep (pretrim_tkeep),
        .axis_original_tuser (pretrim_tuser),
        .axis_original_tvalid(pretrim_tvalid),
        .axis_original_tready(pretrim_tready),
        .axis_original_tlast (pretrim_tlast),

        .axis_trimmed_tdata  (packet_body_out_axis_tdata),
        .axis_trimmed_tkeep  (packet_body_out_axis_tkeep),
        .axis_trimmed_tuser  (packet_body_out_axis_tuser),
        .axis_trimmed_tvalid (packet_body_out_axis_tvalid),
        .axis_trimmed_tready (trimmed_tready),
        .axis_trimmed_tlast  (packet_body_out_axis_tlast)
    );


    // 4. METADATA PARSERS

    // Global wires
    reg reset_parsers;

    // Next values
    reg reset_parsers_next;

    // src mac address
    wire src_mac_addr_ready;

    axis_parser #( .START_INDEX(48), .END_INDEX(95) ) src_mac_addr_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (src_mac_addr_out),
        .parsed_value_ready(src_mac_addr_ready)
    );

    // dest mac address
    wire dest_mac_addr_ready;

    axis_parser #( .START_INDEX(0), .END_INDEX(47) ) dest_mac_addr_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (dest_mac_addr_out),
        .parsed_value_ready(dest_mac_addr_ready)
    );

    // src ip address
    wire src_ip_addr_ready;

    axis_parser #( .START_INDEX(208), .END_INDEX(239) ) src_ip_addr_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (src_ip_addr_out),
        .parsed_value_ready(src_ip_addr_ready)
    );

    // dest ip address
    wire dest_ip_addr_ready;

    axis_parser #( .START_INDEX(240), .END_INDEX(271) ) dest_ip_addr_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (dest_ip_addr_out),
        .parsed_value_ready(dest_ip_addr_ready)
    );

    // src port
    wire src_port_ready;

    axis_parser #( .START_INDEX(272), .END_INDEX(287) ) src_port_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (src_port_out),
        .parsed_value_ready(src_port_ready)
    );

    // dest port
    wire dest_port_ready;

    axis_parser #( .START_INDEX(288), .END_INDEX(303) ) dest_port_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (dest_port_out),
        .parsed_value_ready(dest_port_ready)
    );

    // transmission ID
    wire transmission_id_ready;

    axis_parser #( .START_INDEX(344), .END_INDEX(375) ) transmission_id_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (transmission_id_out),
        .parsed_value_ready(transmission_id_ready)
    );

    // last packet
    wire last_packet_ready;

    axis_parser #( .START_INDEX(343), .END_INDEX(343) ) last_packet_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (last_packet_out),
        .parsed_value_ready(last_packet_ready)
    );

    // sequence number
    wire sequence_number_ready;

    axis_parser #( .START_INDEX(376), .END_INDEX(391) ) sequence_number_parser
    (
        .axis_aclk         (axis_aclk),
        .axis_resetn       (axis_resetn),

        .axis_tdata        (pretrim_tdata),
        .axis_tkeep        (pretrim_tkeep),
        .axis_tuser        (pretrim_tuser),
        .axis_tready       (pretrim_tready),
        .axis_tvalid       (pretrim_tvalid),
        .axis_tlast        (pretrim_tlast),

        .reset             (reset_parsers),

        .parsed_value      (sequence_number_out),
        .parsed_value_ready(sequence_number_ready)
    );


    // 6. OUTPUT

    wire metadata_ready =
        src_mac_addr_ready &
        dest_mac_addr_ready &
        src_ip_addr_ready &
        dest_ip_addr_ready &
        src_port_ready &
        dest_port_ready &
        transmission_id_ready &
        last_packet_ready &
        sequence_number_ready;

    assign trimmed_tready = packet_body_out_axis_tready & metadata_ready;

    // 5. STATE TRANSITIONS

    always @(*) begin
        state_next         = state;
        reset_parsers_next = 0;

        case (state)

            READING_STATE: begin
                // While we're reading, we want to monitor the input
                if (packet_in_axis_tready & packet_in_axis_tvalid) begin
                    if (packet_in_axis_tlast) begin
                        state_next = WAITING_STATE;
                    end
                end
            end

            WAITING_STATE: begin
                // While we're writing, we want to monitor the output
                if (trimmed_tready & packet_body_out_axis_tvalid) begin
                    if (packet_body_out_axis_tlast) begin
                        state_next         = READING_STATE;
                        reset_parsers_next = 1;
                    end
                end
            end

        endcase
    end


    // 6. WRITE TO REGISTERS

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            state         <= READING_STATE;
            reset_parsers <= 0;
        end else begin
            state         <= state_next;
            reset_parsers <= reset_parsers_next;
        end
    end


endmodule