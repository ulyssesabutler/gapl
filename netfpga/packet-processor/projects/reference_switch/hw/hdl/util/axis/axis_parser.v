/* AXI STREAM PARSER
 * =================
 * This will extract the value in an AXI stream from START to END index. For example, if each transmission is 32-bits,
 * and the start and end incies are 16 and 48, respectively, we want to extract a 32 bit "parsed values". That value
 * would consist of the last 16 bits of the first transmission, and the first 16 bits of the second transmission.
 *
 * This module assumes the incoming AXI stream is "flattened"
 * 
 * It's also worth noting that this module doesn't act as a receiver. It only acts as a "prober." As such, it won't
 * set the ready flag.
 *
 * When the value has been parsed out, 
 */
module axis_parser
#(
    parameter START_INDEX  = 0,
    parameter END_INDEX    = 31,

    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam OUT_SIZE    = END_INDEX - START_INDEX + 1,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                       axis_aclk,
    input                       axis_resetn,

    // Module input
    input [TDATA_WIDTH - 1:0]   axis_tdata,
    input [TKEEP_WIDTH - 1:0]   axis_tkeep,
    input [TUSER_WIDTH - 1:0]   axis_tuser,
    input                       axis_tvalid,
    input                       axis_tready, // Notice that this isn't an output since this module is probing, not receiving.
    input                       axis_tlast,

    input                       reset, // Make reset explicit, so output registers aren't cleared too early

    // Module output
    output reg [OUT_SIZE - 1:0] parsed_value,
    output reg                  parsed_value_ready
);


    // 1. STATE INFORMATION

    // Create registers
    reg [31:0] bits_read;
    reg [31:0] bits_written;

    // Create next values
    reg [31:0] bits_read_next;
    reg [31:0] bits_written_next;


    // 2. FIND THE PART OF THE TRANSMISSION TO PARSE
    wire [31:0] current_transmission_start_index = (START_INDEX > bits_read) ? (START_INDEX - bits_read) : 0;
    wire [31:0] current_transmission_end_index   = ((END_INDEX - bits_read) < TDATA_WIDTH) ? (END_INDEX - bits_read) : (TDATA_WIDTH - 1);

    wire [31:0] current_transmission_bits_read = (((END_INDEX + 1) >= bits_read) & (START_INDEX <= (bits_read + TDATA_WIDTH))) ? (current_transmission_end_index - current_transmission_start_index + 1) : 0;

    wire [TDATA_WIDTH - 1:0] parsed_data_mask;

    generate_mask #(.MASK_WIDTH(TDATA_WIDTH)) generate_parsed_data_mask
    (
        .start_index(current_transmission_start_index),
        .end_index(current_transmission_end_index),
        .mask(parsed_data_mask)
    );

    wire [TDATA_WIDTH - 1:0] parsed_data = axis_tdata & (current_transmission_bits_read ? parsed_data_mask : 0);


    // 3. SHIFT THE DATA INTO PLACE

    // Let's just do the lazy method. There's a faster way to do this that we can implement if this doesn't meet timing
    wire [OUT_SIZE - 1:0] parsed_data_at_front    = parsed_data >> current_transmission_start_index;
    wire [OUT_SIZE - 1:0] parsed_data_output_mask = parsed_data_at_front << bits_written;


    // 4. SET THE NEXT VALUES FOR THE OUTPUT REGISTERS
    
    // First, determine if a transmission even occurred
    wire transmission_occurred = axis_tready && axis_tvalid;

    // Then, create the next value variables
    reg [OUT_SIZE - 1:0] parsed_value_next;
    reg                  parsed_value_ready_next;

    always @(*) begin
        parsed_value_next       = parsed_value;
        parsed_value_ready_next = parsed_value_ready;

        if (reset) begin
            parsed_value_next       = 0;
            parsed_value_ready_next = 0;
        end else if (transmission_occurred) begin
            parsed_value_next       = parsed_value | parsed_data_output_mask;
            parsed_value_ready_next = (current_transmission_bits_read + bits_written) == OUT_SIZE;
        end
    end


    // 5. COMPUTE THE NEXT VALUES FOR THE BITS READ AND WRITTEN

    always @(*) begin
        bits_read_next    = bits_read;
        bits_written_next = bits_written;

        if (reset) begin
            bits_read_next    = 0;
            bits_written_next = 0;
        end else if (transmission_occurred) begin
            bits_read_next    = bits_read + TDATA_WIDTH; // It doesn't really matter if this is an overestimate
            bits_written_next = bits_written + current_transmission_bits_read;
        end
    end


    // 6. WRITE TO REGISTERS

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            parsed_value       <= 0;
            parsed_value_ready <= 0;

            bits_read    <= 0;
            bits_written <= 0;
        end else begin
            parsed_value       <= parsed_value_next;
            parsed_value_ready <= parsed_value_ready_next;

            bits_read    <= bits_read_next;
            bits_written <= bits_written_next;
        end
    end

endmodule