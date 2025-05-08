`default_nettype none
`timescale 1ns / 1ps

module fifo
#(
    // Force the max depth to be a power of 2, simplifies circular buffer pointer management
    parameter MAX_DEPTH_BITS = 4,
    parameter DATA_WIDTH = 8
) (
    input  wire                     clock,
    input  wire                     reset,

    input  wire [DATA_WIDTH - 1:0]  in_data,
    input  wire                     in_valid,
    output wire                     in_ready,

    output reg  [DATA_WIDTH - 1:0]  out_data,
    output wire                     out_valid,
    input  wire                     out_ready
);

    localparam MAX_DEPTH = 2 ** MAX_DEPTH_BITS;

    // FIFO memory array and pointers
    reg [DATA_WIDTH - 1:0] buffer [0:MAX_DEPTH - 1];

    reg [MAX_DEPTH_BITS - 1:0] write_pointer, read_pointer;
    reg [MAX_DEPTH_BITS:0]     current_depth;

    // FIFO status flags
    assign out_valid = !(current_depth == 0);
    assign in_ready  = !(current_depth == MAX_DEPTH);

    // Compute next values
    reg [DATA_WIDTH - 1:0] next_data;
    reg [MAX_DEPTH_BITS - 1:0] write_pointer_next, read_pointer_next;
    reg [MAX_DEPTH_BITS:0]     current_depth_next;
    always @(*) begin
        next_data          = buffer[write_pointer];
        write_pointer_next = write_pointer;
        read_pointer_next  = read_pointer;
        current_depth_next = current_depth;

        if (in_valid && in_ready) begin
            next_data          = in_data;
            write_pointer_next = write_pointer_next + 1;
            current_depth_next = current_depth_next + 1;
        end

        if (out_valid && out_ready) begin
            read_pointer_next  = read_pointer_next + 1;
            current_depth_next = current_depth_next - 1;
        end
    end

    // Combinational read output: makes the first word immediately available
    always @(*) begin
        if (out_valid) begin
            out_data = buffer[read_pointer];
        end else begin
            out_data = {DATA_WIDTH{1'b0}};
        end
    end

    // Write and pointer management
    integer i;
    always @(posedge clock or posedge reset) begin
        if (reset) begin
            write_pointer <= 0;
            read_pointer  <= 0;
            current_depth <= 0;
            for (i = 0; i < MAX_DEPTH; i = i + 1)
                buffer[i] <= 0;
        end else begin
            write_pointer         <= write_pointer_next;
            read_pointer          <= read_pointer_next;
            current_depth         <= current_depth_next;
            buffer[write_pointer] <= next_data;
        end
    end

endmodule
