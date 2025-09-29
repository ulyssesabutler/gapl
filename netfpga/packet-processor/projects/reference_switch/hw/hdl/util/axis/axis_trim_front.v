/* AXI STREAM TRIM FRONT
 * =====================
 * This module removes the first bytes of an AXI Stream, before forwarding the rest of the AXI Stream, unaltered. The
 * number of bytes removed is dictated by the BYTES_TRIMMED parameter.
 *
 * This module assumes the incoming data stream is flattened.
 */
module axis_trim_front
#(
    parameter TDATA_WIDTH             = 256,
    parameter TUSER_WIDTH             = 128,

    parameter BYTES_TRIMMED           = 0,

    localparam TKEEP_WIDTH            = TDATA_WIDTH / 8,
    localparam BYTES_PER_TRANSMISSION = TKEEP_WIDTH
)
(
    // Global ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] axis_original_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_original_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_original_tuser,
    input                      axis_original_tvalid,
    output                     axis_original_tready,
    input                      axis_original_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_trimmed_tdata,
    output [TKEEP_WIDTH - 1:0] axis_trimmed_tkeep,
    output [TUSER_WIDTH - 1:0] axis_trimmed_tuser,
    output                     axis_trimmed_tvalid,
    input                      axis_trimmed_tready,
    output                     axis_trimmed_tlast
);


    // 1. INPUT QUEUE

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
        .din         ({axis_original_tdata, axis_original_tkeep, axis_original_tuser, axis_original_tlast}),
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
    assign write_to_input_queue = axis_original_tready & axis_original_tvalid;
    assign axis_original_tready = ~input_queue_nearly_full;


    // 2. OUTPUT QUEUE

    // output queue wires
    reg [TDATA_WIDTH - 1:0] output_queue_tdata;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser;
    reg                     output_queue_tlast;

    reg                     write_to_output_queue;
    wire                    read_from_output_queue;

    wire                    output_queue_nearly_full;
    wire                    output_queue_empty;

    // Instantiate input queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(4)
    )
    output_queue
    (
        .din         ({output_queue_tdata, output_queue_tkeep, output_queue_tuser, output_queue_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (read_from_output_queue),
        .dout        ({axis_trimmed_tdata, axis_trimmed_tkeep, axis_trimmed_tuser, axis_trimmed_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Set input queue IO wires
    assign read_from_output_queue = axis_trimmed_tready & axis_trimmed_tvalid;
    assign axis_trimmed_tvalid    = ~output_queue_empty;


    // 3. STATE INFORMATION

    // Create state registers
    reg [31:0] bytes_read;

    // Create next values
    reg [31:0] bytes_read_next;


    // 4. HOW MANY BYTES TO REMOVE?

    // Create the variables
    reg         is_transmission_not_null;
    reg  [31:0] bytes_to_trim_from_transmission;

    wire [31:0] bits_to_trim_from_transmission = bytes_to_trim_from_transmission * 8;

    // Logic
    always @(*) begin
        if (bytes_read > BYTES_TRIMMED) begin
            bytes_to_trim_from_transmission = 0;
            is_transmission_not_null        = 1;
        end else begin
            bytes_to_trim_from_transmission = BYTES_TRIMMED - bytes_read;
            is_transmission_not_null        = bytes_to_trim_from_transmission < BYTES_PER_TRANSMISSION;
        end
    end


    // 5. COMPUTE THE TRIMMED TRANSMISSION

    wire [TDATA_WIDTH - 1:0] trimmed_tdata = is_transmission_not_null ? (input_queue_tdata >> bits_to_trim_from_transmission) : 0;
    wire [TKEEP_WIDTH - 1:0] trimmed_tkeep = is_transmission_not_null ? (input_queue_tkeep >> bytes_to_trim_from_transmission) : 0;


    // 6. MOVE DATA BETWEEN INPUT AND OUTPUT QUEUE

    // Create the next values of the input to the output queue
    reg [TDATA_WIDTH - 1:0] output_queue_tdata_next;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep_next;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser_next;
    reg                     output_queue_tlast_next;

    reg                     write_to_output_queue_next;

    // Determine whether or not we can move data from the input to output queue
    wire move_data_between_queues = ~input_queue_empty & ~output_queue_nearly_full;

    // Logic
    assign read_from_input_queue = move_data_between_queues;

    always @(*) begin
        bytes_read_next = bytes_read;

        if (move_data_between_queues) begin
            bytes_read_next = bytes_read + TKEEP_WIDTH;
        end
    end

    always @(*) begin
        output_queue_tdata_next = 0;
        output_queue_tkeep_next = 0;
        output_queue_tuser_next = 0;
        output_queue_tlast_next = 0;

        write_to_output_queue_next = 0;

        if (move_data_between_queues && is_transmission_not_null) begin
            output_queue_tdata_next = trimmed_tdata;
            output_queue_tkeep_next = trimmed_tkeep;
            output_queue_tuser_next = input_queue_tuser;
            output_queue_tlast_next = input_queue_tlast;

            write_to_output_queue_next = 1;
        end
    end


    // 7. WRITE TO REGISTERS

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            bytes_read <= 0;
            
            output_queue_tdata <= 0;
            output_queue_tkeep <= 0;
            output_queue_tuser <= 0;
            output_queue_tlast <= 0;

            write_to_output_queue <= 0;
        end else begin
            bytes_read <= bytes_read_next;

            output_queue_tdata <= output_queue_tdata_next;
            output_queue_tkeep <= output_queue_tkeep_next;
            output_queue_tuser <= output_queue_tuser_next;
            output_queue_tlast <= output_queue_tlast_next;

            write_to_output_queue <= write_to_output_queue_next;
        end
    end


endmodule