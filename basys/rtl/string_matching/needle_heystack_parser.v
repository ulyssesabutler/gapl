module needle_heystack_parser
#(
    parameter STRING_SIZE = 5
) (
    input  wire                           clock,
    input  wire                           reset,
    input  wire                           enable,

    input  wire [7:0]                     in_data,
    input  wire                           in_valid,
    input  wire                           in_last,

    output reg  [(STRING_SIZE * 8) - 1:0] needle,

    output reg  [7:0]                     heystack_data,
    output reg                            heystack_valid,
    output reg                            heystack_last
);

    localparam STRING_SIZE_BITS = $clog2(STRING_SIZE + 1);

    reg [(STRING_SIZE * 8) - 1:0] needle_next;

    reg [STRING_SIZE_BITS - 1:0] needle_index;
    reg [STRING_SIZE_BITS - 1:0] needle_index_next;

    reg [7:0] heystack_data_next;
    reg       heystack_valid_next;
    reg       heystack_last_next;

    localparam STATE_RECEIVING_NEEDLE   = 0;
    localparam STATE_RECEIVING_HEYSTACK = 1;

    reg state;
    reg state_next;

    always @(*) begin
        needle_next = needle;
        needle_index_next = needle_index;
        state_next = state;

        heystack_data_next = 0;
        heystack_valid_next = 0;
        heystack_last_next = 0;

        if (state == STATE_RECEIVING_NEEDLE) begin
            if (in_valid) begin
                needle_next = needle_next | (in_data << (needle_index * 8));

                if (needle_index == (STRING_SIZE_BITS - 1)) begin
                    state_next = STATE_RECEIVING_HEYSTACK;
                    needle_index_next = 0;
                end else begin
                    needle_index_next = needle_index + 1;
                end
            end
        end else if (state == STATE_RECEIVING_HEYSTACK) begin
            if (in_valid) begin
                heystack_data_next = in_data;
                heystack_valid_next = 1;
                heystack_last_next = in_last;

                if (in_last) begin
                    state_next  = STATE_RECEIVING_NEEDLE;
                    needle_next = 0;
                end
            end
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            state        <= 0;
            needle       <= 0;
            needle_index <= 0;

            heystack_data  <= 0;
            heystack_valid <= 0;
            heystack_last  <= 0;
        end else begin
            state        <= state_next;
            needle       <= needle_next;
            needle_index <= needle_index_next;

            heystack_data  <= heystack_data_next;
            heystack_valid <= heystack_valid_next;
            heystack_last  <= heystack_last_next;
        end
    end

endmodule
