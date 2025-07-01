module array_dump
#(
    parameter ARRAY_HEIGHT = 16,
    parameter ARRAY_WIDTH  = 3,
    parameter CELL_WIDTH   = 8
) (
    input  wire clock,
    input  wire reset,

    input  wire [ARRAY_HEIGHT * ARRAY_WIDTH * CELL_WIDTH - 1:0] in_data,
    input  wire                                                 in_valid,
    output wire                                                 in_ready,

    output wire [7:0]                                           out_data,
    output wire                                                 out_valid,
    input  wire                                                 out_ready,
    output wire                                                 out_last
);

    localparam ARRAY_SIZE = (ARRAY_HEIGHT * ARRAY_WIDTH * CELL_WIDTH) / 8;
    localparam ARRAY_INDEX_BITS = $clog2(ARRAY_SIZE + 1);

    reg [ARRAY_HEIGHT * ARRAY_WIDTH * CELL_WIDTH - 1:0] buffer;
    reg [ARRAY_HEIGHT * ARRAY_WIDTH * CELL_WIDTH - 1:0] buffer_next;

    reg [ARRAY_INDEX_BITS - 1:0] current_index;
    reg [ARRAY_INDEX_BITS - 1:0] current_index_next;

    assign out_data = buffer[current_index * 8 +: 8];
    assign out_last = current_index == (ARRAY_SIZE - 1);

    localparam STATE_RECEIVING = 0;
    localparam STATE_SENDING   = 1;

    reg state;
    reg state_next;

    assign in_ready  = state == STATE_RECEIVING;
    assign out_valid = state == STATE_SENDING;

    wire receiving = in_ready && in_valid;
    wire sending   = out_ready && out_valid;

    always @(*) begin
        state_next = state;
        current_index_next = current_index;
        buffer_next = buffer;

        case (state)

            STATE_RECEIVING: begin
                if (receiving) begin
                    state_next = STATE_SENDING;
                    current_index_next = 0;
                    buffer_next = in_data;
                end
            end

            STATE_SENDING: begin
                if (sending) begin
                    if ((current_index + 1) < ARRAY_SIZE) begin
                        current_index_next = current_index + 1;
                    end else begin
                        state_next = STATE_RECEIVING;
                    end
                end
            end

        endcase

    end

    always @(posedge clock) begin
        if (reset) begin
            state         <= 0;
            current_index <= 0;
            buffer        <= 0;
        end else begin
            state         <= state_next;
            current_index <= current_index_next;
            buffer        <= buffer_next;
        end
    end

endmodule
