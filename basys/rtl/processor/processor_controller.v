`default_nettype none
`timescale 1ns / 1ps

module processor_controller
#(
    parameter TIMER_SIZE    = 32,
    parameter DATA_IN_SIZE  = 8,
    parameter DATA_OUT_SIZE = 8
) (
    input  wire                       clock,
    input  wire                       reset,

    input  wire [DATA_IN_SIZE - 1:0]  data_ingress_in,
    input  wire                       valid_ingress_in,
    output wire                       ready_ingress_in,
    input  wire                       last_ingress_in,

    output wire [DATA_IN_SIZE - 1:0]  data_ingress_out,
    output wire                       valid_ingress_out,
    input  wire                       ready_ingress_out,
    output wire                       last_ingress_out,

    output wire                       enable,

    input  wire [DATA_OUT_SIZE - 1:0] data_egress_in,
    input  wire                       valid_egress_in,
    output wire                       ready_egress_in,
    input  wire                       last_egress_in,

    output wire [DATA_OUT_SIZE - 1:0] data_egress_out,
    output wire                       valid_egress_out,
    input  wire                       ready_egress_out,
    output wire                       last_egress_out,

    output wire [TIMER_SIZE - 1:0]    clock_cycles,
    output reg                        clock_cycles_valid,
    input  wire                       clock_cycles_ready
);

    // Convinient defintions
    wire reading_in              = valid_ingress_in & ready_ingress_in;
    wire writing_out             = valid_egress_out & ready_egress_out;
    wire writing_clock_cycle_out = clock_cycles_valid & clock_cycles_ready;

    // FSM
    localparam STATE_STARTING = 0;
    localparam STATE_RUNNING  = 1;
    localparam STATE_COMPLETE = 2;

    reg [2:0] state;
    reg [2:0] state_next;

    always @(*) begin
        state_next = state;

        case (state)

            STATE_STARTING: begin
                if (reading_in & last_ingress_in) state_next = STATE_RUNNING;
            end

            STATE_RUNNING: begin
                if (writing_out & last_egress_out) state_next = STATE_COMPLETE;
            end

            STATE_COMPLETE: begin
                if (writing_clock_cycle_out) state_next = STATE_COMPLETE;
            end

        endcase
    end

    // Enabling
    reg enable;

    always @(*) begin
        enable = 0;

        case (state)

            STATE_STARTING: begin
                if (valid_ingress_in & ready_egress_out) enable = 1;
            end

            STATE_RUNNING: begin
                if (ready_egress_out) enable = 1;
            end

        endcase
    end

    // Hook-up ingress and egress
    assign data_ingress_out  = data_ingress_in;
    assign valid_ingress_out = valid_ingress_in; // Pausing should be handled by enable, not valid
    assign ready_ingress_in  = ready_ingress_out & enable; // Don't send if the module is disabled
    assign last_ingress_out  = last_ingress_in;

    assign data_egress_out   = data_egress_in;
    assign valid_egress_out  = valid_egress_in & enable;
    assign ready_egress_in   = ready_egress_out & enable; // If the module is disabled while ready is high...
    assign last_egress_out   = last_egress_in;
    
    // Timer
    always @(*) begin
        clock_cycles_valid = 0;

        case (state)

            STATE_COMPLETE: begin
                clock_cycles_valid = 1;
            end

        endcase
    end

    timer count_clock_cycles
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),
        .clock_cycles(clock_cycles)
    );

    always @(posedge clock) begin
        if (reset) begin
            state <= 0;
        end else begin
            state <= state_next;
        end
    end

endmodule