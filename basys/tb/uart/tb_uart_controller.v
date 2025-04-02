module tb_uart_controller;

    // Parameters
    parameter  TIME_PER_SECOND = 1000;
    parameter  CLOCK_PERIOD    = 10;
    localparam CLOCK_FREQUENCY = TIME_PER_SECOND / CLOCK_PERIOD;
    parameter  BAUD_PERIOD     = 100;
    localparam BAUD_FREQUENCY  = TIME_PER_SECOND / BAUD_PERIOD;

    // Signal declaration
    reg         clock;
    reg         uart_clock;
    reg         reset;

    reg         receive_uart;
    wire        transmit_uart;

    wire [7:0]  transmit_data = 0;
    wire        transmit_valid = 0;
    wire        transmit_ready;

    wire [7:0]  receive_data;
    wire        receive_valid;
    wire        receive_ready = 0;

    // Clock generation
    always begin
        clock = 0;
        #(CLOCK_PERIOD / 2);

        clock = 1;
        #(CLOCK_PERIOD / 2);
    end

    always begin
        uart_clock = 0;
        #(BAUD_PERIOD / 2);

        uart_clock = 1;
        #(BAUD_PERIOD / 2);
    end

    // Instantiate DUT
    uart_controller #( .BAUD_RATE(BAUD_FREQUENCY), .CLOCK_FREQUENCY(CLOCK_FREQUENCY) ) dut (
        .clock(clock),
        .reset(reset),

        .receive_uart(receive_uart),
        .transmit_uart(transmit_uart),

        .transmit_data(transmit_data),
        .transmit_valid(transmit_valid),
        .transmit_ready(transmit_ready),

        .receive_data(receive_data),
        .receive_valid(receive_valid),
        .receive_ready(receive_ready)
    );

    // Send a single byte from the host (that is, over the receiver wire)
    task send_byte;

        input [7:0] data;
        integer i;

        begin
            receive_uart = 0;
            #BAUD_PERIOD;

            for (i = 0; i < 8; i = i + 1) begin
                receive_uart = data[i];
                #BAUD_PERIOD;
            end

            receive_uart = 1;
            #(2 * BAUD_PERIOD);
        end

    endtask;

    // Main
    initial begin

        reset          = 1;
        receive_uart   = 1;
        #(2 * CLOCK_PERIOD);
        #(BAUD_PERIOD * 5);

        reset = 0;

        send_byte(8'hAA);

        #(BAUD_PERIOD * 5);

        $finish;

    end

endmodule