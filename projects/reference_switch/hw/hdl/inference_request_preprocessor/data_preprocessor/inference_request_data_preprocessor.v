/* INFERENCE REQUEST DATA PREPROCESSOR
 * ===================================
 * This module takes data from inference request packets and processes those packets. It will track the "current"
 * inference request. Packets that don't match that request will be discarded. It will also attempt to detect
 * errors in the current inference requests. Once the inference request is completed, or an error is detected, this
 * module will end the current inference request and wait for the next one.
 *
 * While this module is outputting the packet body, it needs to maintain the output metadata wires. As a result,
 * this module can't start processing the next packet until the current packet has been transmitted.
 */
module inference_request_data_preprocessor
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
    input  [MAC_ADDRESS_WIDTH - 1:0]      src_mac_addr_in,
    input  [MAC_ADDRESS_WIDTH - 1:0]      dest_mac_addr_in,

    input  [IP_ADDRESS_WIDTH - 1:0]       src_ip_addr_in,
    input  [IP_ADDRESS_WIDTH - 1:0]       dest_ip_addr_in,

    input  [PORT_WIDTH - 1:0]             src_port_in,
    input  [PORT_WIDTH - 1:0]             dest_port_in,

    input  [TRANSMISSION_ID_WIDTH - 1:0]  transmission_id_in,

    input  [CIP_SEQUENCE_NUM_WIDTH - 1:0] sequence_number_in,
    input                                 last_packet_in,

    input  [TDATA_WIDTH - 1:0]            packet_body_in_axis_tdata,
    input  [TKEEP_WIDTH - 1:0]            packet_body_in_axis_tkeep,
    input  [TUSER_WIDTH - 1:0]            packet_body_in_axis_tuser,
    input                                 packet_body_in_axis_tvalid,
    output                                packet_body_in_axis_tready,
    input                                 packet_body_in_axis_tlast,

    // Module output
    output [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_out,
    output [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_out,

    output [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_out,
    output [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_out,

    output [PORT_WIDTH - 1:0]            src_port_out,
    output [PORT_WIDTH - 1:0]            dest_port_out,

    output [TRANSMISSION_ID_WIDTH - 1:0] transmission_id_out,
    output                               error_out,

    output [TDATA_WIDTH - 1:0]           packet_body_out_axis_tdata,
    output [TKEEP_WIDTH - 1:0]           packet_body_out_axis_tkeep,
    output [TUSER_WIDTH - 1:0]           packet_body_out_axis_tuser,
    output                               packet_body_out_axis_tvalid,
    input                                packet_body_out_axis_tready,
    output                               packet_body_out_axis_tlast
);


    // CURRENT STATE

    // Registers
    reg [1:0] state;
    reg       error;
    reg       reset;

    // Next values
    reg [1:0] state_next;
    reg       reset_next;

    // State values
    localparam WAITING_FOR_FIRST_STATE = 0;
    localparam RECEIVING_STATE         = 1;
    localparam WAITING_FOR_NEXT_STATE  = 2;
    localparam TRANSMITTING_STATE      = 3;

    // Create a pipeline reset
    wire pipeline_reset = reset || !axis_resetn;


    // TRACK CURRENT INFERENCE REQUEST

    // Registers
    reg [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr;
    reg [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr;

    reg [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr;
    reg [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr;

    reg [PORT_WIDTH - 1:0]            src_port;
    reg [PORT_WIDTH - 1:0]            dest_port;

    reg [TRANSMISSION_ID_WIDTH - 1:0] transmission_id;
    reg                               error_flag;

    // Next values
    reg [MAC_ADDRESS_WIDTH - 1:0]     src_mac_addr_next;
    reg [MAC_ADDRESS_WIDTH - 1:0]     dest_mac_addr_next;

    reg [IP_ADDRESS_WIDTH - 1:0]      src_ip_addr_next;
    reg [IP_ADDRESS_WIDTH - 1:0]      dest_ip_addr_next;

    reg [PORT_WIDTH - 1:0]            src_port_next;
    reg [PORT_WIDTH - 1:0]            dest_port_next;

    reg [TRANSMISSION_ID_WIDTH - 1:0] transmission_id_next;
    reg                               error_flag_next;

    // Output the values of the current inference request
    assign src_mac_addr_out = src_mac_addr;
    assign dest_mac_addr_out = dest_mac_addr;

    assign src_ip_addr_out = src_ip_addr;
    assign dest_ip_addr_out = dest_ip_addr;

    assign src_port_out = src_port;
    assign dest_port_out = dest_port;

    assign transmission_id_out = transmission_id;
    assign error_out = error_flag;


    // CREATE THE INPUT QUEUE

    // Define the input registers
    reg [TDATA_WIDTH - 1:0] input_queue_in_tdata;
    reg [TKEEP_WIDTH - 1:0] input_queue_in_tkeep;
    reg [TUSER_WIDTH - 1:0] input_queue_in_tuser;
    reg                     input_queue_in_tlast;

    reg                     write_to_input_queue;

    // Define the next values of those registers
    reg [TDATA_WIDTH - 1:0] input_queue_in_tdata_next;
    reg [TKEEP_WIDTH - 1:0] input_queue_in_tkeep_next;
    reg [TUSER_WIDTH - 1:0] input_queue_in_tuser_next;
    reg                     input_queue_in_tlast_next;

    reg                     write_to_input_queue_next;

    // Define the output wires
    wire [TDATA_WIDTH - 1:0] input_queue_out_tdata;
    wire [TKEEP_WIDTH - 1:0] input_queue_out_tkeep;
    wire [TUSER_WIDTH - 1:0] input_queue_out_tuser;
    wire                     input_queue_out_tlast;

    // Define the remaining wires
    wire                    input_queue_nearly_full;
    wire                    input_queue_empty;
    wire                    read_from_input_queue;

    // Define the queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(4)
    )
    input_fifo
    (
        .din         ({input_queue_in_tdata, input_queue_in_tkeep, input_queue_in_tuser, input_queue_in_tlast}),
        .wr_en       (write_to_input_queue),
        .rd_en       (read_from_input_queue),
        .dout        ({input_queue_out_tdata, input_queue_out_tkeep, input_queue_out_tuser, input_queue_out_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (input_queue_nearly_full),
        .empty       (input_queue_empty),
        .reset       (pipeline_reset),
        .clk         (axis_aclk)
    );


    // TRACK CURRENT PACKET

    // Create the registers
    reg [CIP_SEQUENCE_NUM_WIDTH - 1:0] sequence_number;
    reg                                last_packet;

    // Create the next values
    reg [CIP_SEQUENCE_NUM_WIDTH - 1:0] sequence_number_next;
    reg                                last_packet_next;

    // Reverse the byte order to get a usable version of the sequence number
    wire [CIP_SEQUENCE_NUM_WIDTH - 1:0] logical_sequence_number;
    wire [CIP_SEQUENCE_NUM_WIDTH - 1:0] logical_sequence_number_in;

    reverse_byte_order #( .BYTE_COUNT(CIP_SEQUENCE_NUM_WIDTH / 8) ) compute_logical_sequence_number
    (
        .input_number(sequence_number),
        .output_number(logical_sequence_number)
    );

    reverse_byte_order #( .BYTE_COUNT(CIP_SEQUENCE_NUM_WIDTH / 8) ) compute_logical_sequence_number_in
    (
        .input_number(sequence_number_in),
        .output_number(logical_sequence_number_in)
    );


    // PROCESS MODULE INPUT

    // Create the wires
    assign packet_body_in_axis_tready = !input_queue_nearly_full && ((state == WAITING_FOR_FIRST_STATE) || (state == WAITING_FOR_NEXT_STATE) || (state == RECEIVING_STATE));
    wire recieving_transmission = packet_body_in_axis_tready && packet_body_in_axis_tvalid;

    // Detect if this packet is the same transmission as the last packet
    wire is_same_transmission = src_mac_addr == src_mac_addr_in
                                && dest_mac_addr == dest_mac_addr_in
                                && src_ip_addr == src_ip_addr_in
                                && dest_ip_addr == dest_ip_addr_in
                                && src_port == src_port_in
                                && dest_port == dest_port_in
                                && transmission_id == transmission_id_in;

    // Logic
    always @(*) begin
        // Transmission Metadata
        src_mac_addr_next  = src_mac_addr;
        dest_mac_addr_next = dest_mac_addr;

        src_ip_addr_next  = src_ip_addr;
        dest_ip_addr_next = dest_ip_addr;

        src_port_next  = src_port;
        dest_port_next = dest_port;

        transmission_id_next = transmission_id;

        // Packet metadata
        sequence_number_next = sequence_number;
        last_packet_next = last_packet;
        error = 0;

        // Queue input
        input_queue_in_tdata_next = 0;
        input_queue_in_tkeep_next = 0;
        input_queue_in_tuser_next = 0;
        input_queue_in_tlast_next = 0;

        write_to_input_queue_next = 0;

        if (recieving_transmission) begin
            if (state == WAITING_FOR_FIRST_STATE) begin
                // Error detection
                error = !(logical_sequence_number_in == 0);

                // Transmission metadata
                src_mac_addr_next  = src_mac_addr_in;
                dest_mac_addr_next = dest_mac_addr_in;

                src_ip_addr_next  = src_ip_addr_in;
                dest_ip_addr_next = dest_ip_addr_in;

                src_port_next  = src_port_in;
                dest_port_next = dest_port_in;

                transmission_id_next = transmission_id_in;

                // Packet metadata
                sequence_number_next = sequence_number_in;
                last_packet_next = last_packet_in;

                // Queue input
                input_queue_in_tdata_next = packet_body_in_axis_tdata;
                input_queue_in_tkeep_next = packet_body_in_axis_tkeep;
                input_queue_in_tuser_next = packet_body_in_axis_tuser;
                input_queue_in_tlast_next = packet_body_in_axis_tlast && last_packet;

                write_to_input_queue_next = 1;
            end else if (state == WAITING_FOR_NEXT_STATE) begin
                // Error detection
                error = !(is_same_transmission && ((logical_sequence_number + 1) == logical_sequence_number_in));

                // Packet metadata
                sequence_number_next = sequence_number_in;
                last_packet_next = last_packet_in;

                // Queue input
                if (is_same_transmission) begin
                    input_queue_in_tdata_next = packet_body_in_axis_tdata;
                    input_queue_in_tkeep_next = packet_body_in_axis_tkeep;
                    input_queue_in_tuser_next = packet_body_in_axis_tuser;
                    input_queue_in_tlast_next = packet_body_in_axis_tlast && last_packet;

                    write_to_input_queue_next = 1;
                end
            end else if (state == RECEIVING_STATE) begin
                input_queue_in_tdata_next = packet_body_in_axis_tdata;
                input_queue_in_tkeep_next = packet_body_in_axis_tkeep;
                input_queue_in_tuser_next = packet_body_in_axis_tuser;
                input_queue_in_tlast_next = packet_body_in_axis_tlast && last_packet;

                write_to_input_queue_next = 1;
            end

            // Otherwise, drop the packet
        end
    end


    // CREATE THE OUTPUT QUEUE

    // Define the input registers
    reg [TDATA_WIDTH - 1:0] output_queue_in_tdata;
    reg [TKEEP_WIDTH - 1:0] output_queue_in_tkeep;
    reg [TUSER_WIDTH - 1:0] output_queue_in_tuser;
    reg                     output_queue_in_tlast;

    reg                     write_to_output_queue;

    // Define the next values of those registers
    reg [TDATA_WIDTH - 1:0] output_queue_in_tdata_next;
    reg [TKEEP_WIDTH - 1:0] output_queue_in_tkeep_next;
    reg [TUSER_WIDTH - 1:0] output_queue_in_tuser_next;
    reg                     output_queue_in_tlast_next;

    reg                     write_to_output_queue_next;

    // Define the remaining wires
    wire                    output_queue_nearly_full;
    wire                    output_queue_empty;
    wire                    read_from_output_queue;

    // Define the queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(4)
    )
    output_fifo
    (
        .din         ({output_queue_in_tdata, output_queue_in_tkeep, output_queue_in_tuser, output_queue_in_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (read_from_output_queue),
        .dout        ({packet_body_out_axis_tdata, packet_body_out_axis_tkeep, packet_body_out_axis_tuser, packet_body_out_axis_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    assign packet_body_out_axis_tvalid = !output_queue_empty;
    assign read_from_output_queue = packet_body_out_axis_tvalid && packet_body_out_axis_tready;


    // SEND INPUT TO DATA PROCESSING PIPELINE

    // Create the wires for the input
    wire inference_request_data_processing_pipeline_in_valid = ~input_queue_empty;
    wire inference_request_data_processing_pipeline_in_ready;

    assign read_from_input_queue = inference_request_data_processing_pipeline_in_valid & inference_request_data_processing_pipeline_in_ready;

    // Create output wires
    wire [TDATA_WIDTH - 1:0] processed_packet_tdata;
    wire [TKEEP_WIDTH - 1:0] processed_packet_tkeep;
    wire [TUSER_WIDTH - 1:0] processed_packet_tuser;
    wire                     processed_packet_tvalid;
    wire                     processed_packet_tready;
    wire                     processed_packet_tlast;

    // Instantiate the module
    inference_request_data_processing_pipeline
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    data_processing_pipeline
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(!pipeline_reset),

        .data_in_axis_tdata(input_queue_out_tdata),
        .data_in_axis_tkeep(input_queue_out_tkeep),
        .data_in_axis_tuser(input_queue_out_tuser),
        .data_in_axis_tvalid(inference_request_data_processing_pipeline_in_valid),
        .data_in_axis_tready(inference_request_data_processing_pipeline_in_ready),
        .data_in_axis_tlast(input_queue_out_tlast),

        .data_out_axis_tdata(processed_packet_tdata),
        .data_out_axis_tkeep(processed_packet_tkeep),
        .data_out_axis_tuser(processed_packet_tuser),
        .data_out_axis_tvalid(processed_packet_tvalid),
        .data_out_axis_tready(processed_packet_tready),
        .data_out_axis_tlast(processed_packet_tlast)
    );


    // PROCESS OUTPUT

    // Determine when to output
    wire can_write_to_output_queue = !output_queue_nearly_full;
    wire can_move_from_pipeline_to_output_queue = processed_packet_tvalid && processed_packet_tready;
    wire can_write_error_to_output_queue = can_write_to_output_queue && error;

    assign processed_packet_tready = can_write_to_output_queue & !error;

    // Write to output queue
    always @(*) begin
        output_queue_in_tdata_next = 0;
        output_queue_in_tkeep_next = 0;
        output_queue_in_tuser_next = 0;
        output_queue_in_tlast_next = 0;

        write_to_output_queue_next = 0;

        if (can_move_from_pipeline_to_output_queue) begin
            output_queue_in_tdata_next = processed_packet_tdata;
            output_queue_in_tkeep_next = processed_packet_tkeep;
            output_queue_in_tuser_next = processed_packet_tuser;
            output_queue_in_tlast_next = processed_packet_tlast;

            write_to_output_queue_next = 1;
        end else if (can_write_error_to_output_queue) begin
            output_queue_in_tlast_next = 1;
            write_to_output_queue_next = 1;
        end
    end


    // SET NEXT STATE
    always @(*) begin
        state_next = state;
        error_flag_next = error_flag;
        // Reset should only remain for a single clock cycle
        reset_next = 0;

        if (error) begin
            error_flag_next = 1;
            reset_next = 1;

            state_next = TRANSMITTING_STATE;
        end else if (state == WAITING_FOR_FIRST_STATE) begin
            if (recieving_transmission) begin
                state_next = RECEIVING_STATE;
            end
        end else if (state == RECEIVING_STATE) begin
            if (recieving_transmission && packet_body_in_axis_tlast) begin
                if (last_packet) begin
                    state_next = TRANSMITTING_STATE;
                end else begin
                    state_next = WAITING_FOR_NEXT_STATE;
                end
            end
        end else if (state == WAITING_FOR_NEXT_STATE) begin
            if (recieving_transmission) begin
                state_next = RECEIVING_STATE;
            end
        end else if (state == TRANSMITTING_STATE) begin
            if (output_queue_empty) begin
                state_next = WAITING_FOR_FIRST_STATE;
                error_flag_next = 0;
            end
        end
    end


    // WRITE TO REGISTERS
    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            state <= WAITING_FOR_FIRST_STATE;
            reset <= 0;

            src_mac_addr  <= 0;
            dest_mac_addr <= 0;

            src_ip_addr  <= 0;
            dest_ip_addr <= 0;

            src_port  <= 0;
            dest_port <= 0;

            transmission_id <= 0;
            error_flag <= 0;

            sequence_number <= 0;
            last_packet <= 1;

            input_queue_in_tdata <= 0;
            input_queue_in_tkeep <= 0;
            input_queue_in_tuser <= 0;
            input_queue_in_tlast <= 0;

            write_to_input_queue <= 0;

            output_queue_in_tdata <= 0;
            output_queue_in_tkeep <= 0;
            output_queue_in_tuser <= 0;
            output_queue_in_tlast <= 0;

            write_to_output_queue <= 0;
        end else begin
            state <= state_next;
            reset <= reset_next;

            src_mac_addr  <= src_mac_addr_next;
            dest_mac_addr <= dest_mac_addr_next;

            src_ip_addr  <= src_ip_addr_next;
            dest_ip_addr <= dest_ip_addr_next;

            src_port  <= src_port_next;
            dest_port <= dest_port_next;

            transmission_id <= transmission_id_next;
            error_flag <= error_flag_next;

            sequence_number <= sequence_number_next;
            last_packet <= last_packet_next;

            input_queue_in_tdata <= input_queue_in_tdata_next;
            input_queue_in_tkeep <= input_queue_in_tkeep_next;
            input_queue_in_tuser <= input_queue_in_tuser_next;
            input_queue_in_tlast <= input_queue_in_tlast_next;

            write_to_input_queue <= write_to_input_queue_next;

            output_queue_in_tdata <= output_queue_in_tdata_next;
            output_queue_in_tkeep <= output_queue_in_tkeep_next;
            output_queue_in_tuser <= output_queue_in_tuser_next;
            output_queue_in_tlast <= output_queue_in_tlast_next;

            write_to_output_queue <= write_to_output_queue_next;
        end
    end

endmodule