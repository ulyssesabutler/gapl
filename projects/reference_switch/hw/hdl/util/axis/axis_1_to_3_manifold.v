module axis_1_to_3_manifold
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,

    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] axis_input_tdata,
    input  [TKEEP_WIDTH - 1:0] axis_input_tkeep,
    input  [TUSER_WIDTH - 1:0] axis_input_tuser,
    input                      axis_input_tvalid,
    output                     axis_input_tready,
    input                      axis_input_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] axis_output_0_tdata,
    output [TKEEP_WIDTH - 1:0] axis_output_0_tkeep,
    output [TUSER_WIDTH - 1:0] axis_output_0_tuser,
    output                     axis_output_0_tvalid,
    input                      axis_output_0_tready,
    output                     axis_output_0_tlast,

    output [TDATA_WIDTH - 1:0] axis_output_1_tdata,
    output [TKEEP_WIDTH - 1:0] axis_output_1_tkeep,
    output [TUSER_WIDTH - 1:0] axis_output_1_tuser,
    output                     axis_output_1_tvalid,
    input                      axis_output_1_tready,
    output                     axis_output_1_tlast,

    output [TDATA_WIDTH - 1:0] axis_output_2_tdata,
    output [TKEEP_WIDTH - 1:0] axis_output_2_tkeep,
    output [TUSER_WIDTH - 1:0] axis_output_2_tuser,
    output                     axis_output_2_tvalid,
    input                      axis_output_2_tready,
    output                     axis_output_2_tlast
);  

    // OUTPUT QUEUE 0

    // Wires
    reg  [TDATA_WIDTH - 1:0] output_queue_0_input_tdata;
    reg  [TKEEP_WIDTH - 1:0] output_queue_0_input_tkeep;
    reg  [TUSER_WIDTH - 1:0] output_queue_0_input_tuser;
    reg                      output_queue_0_input_tlast;

    reg                      write_to_output_queue_0;
    wire                     read_from_output_queue_0;

    wire                     output_queue_0_nearly_full;
    wire                     output_queue_0_empty;

    // Module
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TKEEP_WIDTH+1),
        .MAX_DEPTH_BITS(4)
    )
    output_fifo_0
    (
        .din         ({output_queue_0_input_tdata, output_queue_0_input_tkeep, output_queue_0_input_tuser, output_queue_0_input_tlast}),
        .wr_en       (write_to_output_queue_0),
        .rd_en       (read_from_output_queue_0),
        .dout        ({axis_output_0_tdata, axis_output_0_tkeep, axis_output_0_tuser, axis_output_0_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_0_nearly_full),
        .empty       (output_queue_0_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Logic
    assign read_from_output_queue_0 = axis_output_0_tvalid & axis_output_0_tready;
    assign axis_output_0_tvalid = ~output_queue_0_empty;


    // OUTPUT QUEUE 1

    // Wires
    reg  [TDATA_WIDTH - 1:0] output_queue_1_input_tdata;
    reg  [TKEEP_WIDTH - 1:0] output_queue_1_input_tkeep;
    reg  [TUSER_WIDTH - 1:0] output_queue_1_input_tuser;
    reg                      output_queue_1_input_tlast;

    reg                      write_to_output_queue_1;
    wire                     read_from_output_queue_1;

    wire                     output_queue_1_nearly_full;
    wire                     output_queue_1_empty;

    // Module
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TKEEP_WIDTH+1),
        .MAX_DEPTH_BITS(4)
    )
    output_fifo_1
    (
        .din         ({output_queue_1_input_tdata, output_queue_1_input_tkeep, output_queue_1_input_tuser, output_queue_1_input_tlast}),
        .wr_en       (write_to_output_queue_1),
        .rd_en       (read_from_output_queue_1),
        .dout        ({axis_output_1_tdata, axis_output_1_tkeep, axis_output_1_tuser, axis_output_1_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_1_nearly_full),
        .empty       (output_queue_1_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Logic
    assign read_from_output_queue_1 = axis_output_1_tvalid & axis_output_1_tready;
    assign axis_output_1_tvalid = ~output_queue_1_empty;


    // OUTPUT QUEUE 2

    // Wires
    reg  [TDATA_WIDTH - 1:0] output_queue_2_input_tdata;
    reg  [TKEEP_WIDTH - 1:0] output_queue_2_input_tkeep;
    reg  [TUSER_WIDTH - 1:0] output_queue_2_input_tuser;
    reg                      output_queue_2_input_tlast;

    reg                      write_to_output_queue_2;
    wire                     read_from_output_queue_2;

    wire                     output_queue_2_nearly_full;
    wire                     output_queue_2_empty;

    // Module
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TKEEP_WIDTH+1),
        .MAX_DEPTH_BITS(4)
    )
    output_fifo_2
    (
        .din         ({output_queue_2_input_tdata, output_queue_2_input_tkeep, output_queue_2_input_tuser, output_queue_2_input_tlast}),
        .wr_en       (write_to_output_queue_2),
        .rd_en       (read_from_output_queue_2),
        .dout        ({axis_output_2_tdata, axis_output_2_tkeep, axis_output_2_tuser, axis_output_2_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_2_nearly_full),
        .empty       (output_queue_2_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    // Logic
    assign read_from_output_queue_2 = axis_output_2_tvalid & axis_output_2_tready;
    assign axis_output_2_tvalid = ~output_queue_2_empty;

    // MANIFOLD LOGIC
    assign axis_input_tready = ~output_queue_0_nearly_full & ~output_queue_1_nearly_full & ~output_queue_2_nearly_full;

    reg [TDATA_WIDTH - 1:0] output_queue_tdata_next;
    reg [TKEEP_WIDTH - 1:0] output_queue_tkeep_next;
    reg [TUSER_WIDTH - 1:0] output_queue_tuser_next;
    reg                     output_queue_tlast_next;

    reg                      write_to_output_queue_next;

    always @(*) begin
        output_queue_tdata_next    = 0;
        output_queue_tkeep_next    = 0;
        output_queue_tuser_next    = 0;
        output_queue_tlast_next    = 0;

        write_to_output_queue_next = 0;

        if (axis_input_tready & axis_input_tvalid) begin
            output_queue_tdata_next    = axis_input_tdata;
            output_queue_tkeep_next    = axis_input_tkeep;
            output_queue_tuser_next    = axis_input_tuser;
            output_queue_tlast_next    = axis_input_tlast;

            write_to_output_queue_next = 1;
        end
    end

    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            // Output Queue 0
            output_queue_0_input_tdata <= 0;
            output_queue_0_input_tkeep <= 0;
            output_queue_0_input_tuser <= 0;
            output_queue_0_input_tlast <= 0;

            write_to_output_queue_0    <= 0;

            // Output Queue 1
            output_queue_1_input_tdata <= 0;
            output_queue_1_input_tkeep <= 0;
            output_queue_1_input_tuser <= 0;
            output_queue_1_input_tlast <= 0;

            write_to_output_queue_1    <= 0;

            // Output Queue 2
            output_queue_2_input_tdata <= 0;
            output_queue_2_input_tkeep <= 0;
            output_queue_2_input_tuser <= 0;
            output_queue_2_input_tlast <= 0;

            write_to_output_queue_2    <= 0;
        end else begin
            // Output Queue 0
            output_queue_0_input_tdata <= output_queue_tdata_next;
            output_queue_0_input_tkeep <= output_queue_tkeep_next;
            output_queue_0_input_tuser <= output_queue_tuser_next;
            output_queue_0_input_tlast <= output_queue_tlast_next;

            write_to_output_queue_0    <= write_to_output_queue_next;

            // Output Queue 1
            output_queue_1_input_tdata <= output_queue_tdata_next;
            output_queue_1_input_tkeep <= output_queue_tkeep_next;
            output_queue_1_input_tuser <= output_queue_tuser_next;
            output_queue_1_input_tlast <= output_queue_tlast_next;

            write_to_output_queue_1    <= write_to_output_queue_next;

            // Output Queue 2
            output_queue_2_input_tdata <= output_queue_tdata_next;
            output_queue_2_input_tkeep <= output_queue_tkeep_next;
            output_queue_2_input_tuser <= output_queue_tuser_next;
            output_queue_2_input_tlast <= output_queue_tlast_next;

            write_to_output_queue_2    <= write_to_output_queue_next;
        end
    end

endmodule