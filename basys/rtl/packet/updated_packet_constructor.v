`default_nettype none

module packet_constructor
(
    input  wire        clock,
    input  wire        reset,

    input  wire [7:0]  packet_data,
    input  wire        packet_valid,
    output reg         packet_ready,
    input  wire        packet_last,

    input  wire [31:0] clock_data,
    input  wire        clock_valid,
    output reg         clock_ready,

    output reg  [7:0]  uart_data,
    output reg         uart_valid,
    input  wire        uart_ready
);

    localparam STATE_PACKET = 0;
    localparam STATE_SENTINEL = 1;
    localparam STATE_CLOCK = 2; // The clock must come after the sentinel since it could contain the sentinel value

    reg [1:0] state;
    reg [1:0] state_next;

    always @(*) begin
        packet_ready = 0;
        clock_ready  = 0;

        case (state)

            STATE_PACKET: begin
                packet_ready = uart_ready;
            end

            STATE_CLOCK: begin
                clock_ready = uart_ready;
            end

        endcase
    end

    wire sending_byte = uart_ready && uart_valid;
    wire clock_last;

    always @(*) begin
        state_next = state;

        case (state)

            STATE_PACKET: begin
                if (sending_byte && packet_last) begin
                    state_next = STATE_SENTINEL;
                end
            end

            STATE_CLOCK: begin
                if (sending_byte && clock_last) begin
                    state_next = STATE_PACKET;
                end
            end

            STATE_SENTINEL: begin
                if (sending_byte) begin
                    state_next = STATE_CLOCK;
                end
            end

        endcase
    end

    reg [1:0] clock_index;
    reg [1:0] clock_index_next;

    always @(*) begin
        clock_index_next = clock_index;

        if (state == STATE_CLOCK && sending_byte) begin
            clock_index_next = clock_index + 1;
        end
    end

    assign clock_last = clock_index == 3;


    always @(*) begin
        uart_data = 0;
        uart_valid = 0;


        case (state)

            STATE_PACKET: begin
                uart_data = packet_data;
                uart_valid = packet_valid;
            end

            STATE_CLOCK: begin
                uart_data = clock_data >> ((3 - clock_index) * 8);
                uart_valid = clock_valid;
            end

            STATE_SENTINEL: begin
                uart_data = 0;
                uart_valid = 1;
            end

        endcase
    end

    always @(posedge clock) begin
        if (reset) begin
            state <= 0;
            clock_index <= 0;
        end else begin
            state <= state_next;
            clock_index <= clock_index_next;
        end
    end

endmodule