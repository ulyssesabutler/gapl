`default_nettype none

module packet_parser
(
    input  wire       clock,
    input  wire       reset,

    input  wire [7:0] uart_data,
    input  wire       uart_valid,
    output wire       uart_ready,

    output wire [7:0] packet_data,
    output wire       packet_valid,
    input  wire       packet_ready,
    output wire       packet_last
);

    reg [7:0] data_ingress;
    reg [7:0] data_ingress_next;
    reg       valid_ingress;
    reg       valid_ingress_next;

    reg [7:0] data_process;
    reg [7:0] data_process_next;
    reg       valid_process;
    reg       valid_process_next;

    reg [7:0] data_egress;
    reg [7:0] data_egress_next;
    reg       valid_egress;
    reg       valid_egress_next;
    reg       last_egress;
    reg       last_egress_next;

    assign packet_data  = data_egress;
    assign packet_valid = valid_egress;
    assign uart_ready   = packet_ready;
    assign packet_last  = last_egress;

    always @(*) begin
        data_ingress_next = data_ingress;
        valid_ingress_next = valid_ingress;

        data_process_next = data_process;
        valid_process_next = valid_process;

        data_egress_next = data_egress;
        valid_egress_next = valid_egress;
        last_egress_next = last_egress;

        // The consumer can still read, even when the whole pipeline isn't moving
        if (packet_ready & packet_valid) begin
            valid_egress_next = 0;
        end

        // If this pipeline is moving
        if (packet_ready & (uart_valid | (data_ingress == 0 & valid_ingress))) begin
            // First, the egress stage
            if (valid_ingress & valid_process) begin
                data_egress_next  = data_process;
                last_egress_next  = (data_ingress == 0);
                valid_egress_next = 1;
            end else begin
                valid_egress_next = 0;
            end

            // Next, the process stage
            if (data_ingress != 0) begin
                data_process_next  = data_ingress;
                valid_process_next = valid_ingress;
            end else begin
                valid_process_next = 0;
            end

            // Last, the ingress stage
            data_ingress_next = uart_data;
            valid_ingress_next = uart_valid;
        end
    end

    always @(posedge clock) begin
        if (reset) begin
            data_ingress  <= 0;
            valid_ingress <= 0;

            data_process  <= 0;
            valid_process <= 0;

            data_egress   <= 0;
            valid_egress  <= 0;
            last_egress   <= 0;
        end else begin
            data_ingress  <= data_ingress_next;
            valid_ingress <= valid_ingress_next;

            data_process  <= data_process_next;
            valid_process <= valid_process_next;

            data_egress   <= data_egress_next;
            valid_egress  <= valid_egress_next;
            last_egress   <= last_egress_next;
        end
    end

endmodule