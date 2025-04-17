module tb_test_harness;

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
    test_harness #( .BAUD_RATE(BAUD_FREQUENCY), .CLOCK_FREQUENCY(CLOCK_FREQUENCY) ) dut
    (
        .clock(clock),
        .reset(reset),

        .uart_receive(receive_uart),
        .uart_transmit(transmit_uart)
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

    // Receive a byte from the FPGA (that is, over it's transmit wire)
    task read_byte;
        output [7:0] data;

        integer i;

        begin
            @(negedge transmit_uart);

            // Skip the first bit, it's just the start bit
            #BAUD_PERIOD;

            // Sample at the middle
            #(BAUD_PERIOD / 2);

            for (i = 0; i < 8; i = i + 1) begin
                data[i] = transmit_uart;
                #BAUD_PERIOD;
            end

            #BAUD_PERIOD;
        end
    endtask

    // Send multiple bytes over UART
    task send_string(input logic [7:0] str[], input int len);
        int i;
        begin
            for (i = 0; i < len; i = i + 1)
                send_byte(str[i]);
        end
    endtask

    // Assuming you know how many bytes to expect:
    task read_string(output logic [7:0] response[], input int len);
        int i;
        logic [7:0] temp;
        begin
            for (i = 0; i < len; i = i + 1) begin
                read_byte(temp);
                response[i] = temp;
            end
        end
    endtask

    logic [7:0] my_input_string [0:14] = '{
        8'h48,
        8'h65,
        8'h6C,
        8'h6C,
        8'h6C,
        8'h6C,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h6F,
        8'h00
    };

    // Main
    initial begin

        reset          = 1;
        receive_uart   = 1;
        #(2 * CLOCK_PERIOD);
        #(BAUD_PERIOD * 5);

        reset = 0;

        send_string(my_input_string, 15);

        #(BAUD_PERIOD * 5);

        $finish;

    end

endmodule