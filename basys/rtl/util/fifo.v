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
            // Read
            if (in_valid && in_ready) begin
                buffer[write_pointer] <= in_data;
                write_pointer         <= write_pointer + 1;
                current_depth         <= current_depth + 1;
            end

            // Write
            if (out_valid && out_ready) begin
                read_pointer  <= read_pointer + 1;
                current_depth <= current_depth - 1;
            end
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

endmodule
