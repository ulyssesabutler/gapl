module axis_transmission_splitter
#(
  // AXI Stream Data Width
  parameter  IN_TDATA_WIDTH  = 256,
  parameter  TUSER_WIDTH     = 128,

  localparam OUT_TDATA_WIDTH = IN_TDATA_WIDTH / 4
)
(
  // Global Ports
  input                                  axis_aclk,
  input                                  axis_resetn,

  input  [IN_TDATA_WIDTH - 1:0]          axis_original_tdata,
  input  [((IN_TDATA_WIDTH / 8)) - 1:0]  axis_original_tkeep,
  input  [TUSER_WIDTH-1:0]               axis_original_tuser,
  input                                  axis_original_tvalid,
  output                                 axis_original_tready,
  input                                  axis_original_tlast,

  output [OUT_TDATA_WIDTH - 1:0]         axis_resize_0_tdata,
  output [((OUT_TDATA_WIDTH / 8)) - 1:0] axis_resize_0_tkeep,
  output [TUSER_WIDTH - 1:0]             axis_resize_0_tuser,
  output                                 axis_resize_0_tvalid,
  input                                  axis_resize_0_tready,
  output                                 axis_resize_0_tlast,

  output [OUT_TDATA_WIDTH - 1:0]         axis_resize_1_tdata,
  output [((OUT_TDATA_WIDTH / 8)) - 1:0] axis_resize_1_tkeep,
  output [TUSER_WIDTH - 1:0]             axis_resize_1_tuser,
  output                                 axis_resize_1_tvalid,
  input                                  axis_resize_1_tready,
  output                                 axis_resize_1_tlast,

  output [OUT_TDATA_WIDTH - 1:0]         axis_resize_2_tdata,
  output [((OUT_TDATA_WIDTH / 8)) - 1:0] axis_resize_2_tkeep,
  output [TUSER_WIDTH - 1:0]             axis_resize_2_tuser,
  output                                 axis_resize_2_tvalid,
  input                                  axis_resize_2_tready,
  output                                 axis_resize_2_tlast,

  output [OUT_TDATA_WIDTH - 1:0]         axis_resize_3_tdata,
  output [((OUT_TDATA_WIDTH / 8)) - 1:0] axis_resize_3_tkeep,
  output [TUSER_WIDTH - 1:0]             axis_resize_3_tuser,
  output                                 axis_resize_3_tvalid,
  input                                  axis_resize_3_tready,
  output                                 axis_resize_3_tlast
);

  // OUTPUT QUEUE 0

  // Wires
  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_0_input_tdata;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_0_input_tkeep;
  reg  [TUSER_WIDTH-1:0]               output_queue_0_input_tuser;
  reg                                  output_queue_0_input_tlast;

  reg                                  write_to_output_queue_0;
  wire                                 read_from_output_queue_0;

  wire                                 output_queue_0_nearly_full;
  wire                                 output_queue_0_empty;

  // Module
  fallthrough_small_fifo
  #(
    .WIDTH(OUT_TDATA_WIDTH+TUSER_WIDTH+OUT_TDATA_WIDTH/8+1),
    .MAX_DEPTH_BITS(4)
  )
  output_fifo_0
  (
    .din         ({output_queue_0_input_tdata, output_queue_0_input_tkeep, output_queue_0_input_tuser, output_queue_0_input_tlast}),
    .wr_en       (write_to_output_queue_0),
    .rd_en       (read_from_output_queue_0),
    .dout        ({axis_resize_0_tdata, axis_resize_0_tkeep, axis_resize_0_tuser, axis_resize_0_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (output_queue_0_nearly_full),
    .empty       (output_queue_0_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  // Logic
  assign read_from_output_queue_0 = axis_resize_0_tvalid & axis_resize_0_tready;
  assign axis_resize_0_tvalid = ~output_queue_0_empty;


  // OUTPUT QUEUE 1

  // Wires
  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_1_input_tdata;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_1_input_tkeep;
  reg  [TUSER_WIDTH-1:0]               output_queue_1_input_tuser;
  reg                                  output_queue_1_input_tlast;

  reg                                  write_to_output_queue_1;
  wire                                 read_from_output_queue_1;

  wire                                 output_queue_1_nearly_full;
  wire                                 output_queue_1_empty;

  // Module
  fallthrough_small_fifo
  #(
    .WIDTH(OUT_TDATA_WIDTH+TUSER_WIDTH+OUT_TDATA_WIDTH/8+1),
    .MAX_DEPTH_BITS(4)
  )
  output_fifo_1
  (
    .din         ({output_queue_1_input_tdata, output_queue_1_input_tkeep, output_queue_1_input_tuser, output_queue_1_input_tlast}),
    .wr_en       (write_to_output_queue_1),
    .rd_en       (read_from_output_queue_1),
    .dout        ({axis_resize_1_tdata, axis_resize_1_tkeep, axis_resize_1_tuser, axis_resize_1_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (output_queue_1_nearly_full),
    .empty       (output_queue_1_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  // Logic
  assign read_from_output_queue_1 = axis_resize_1_tvalid & axis_resize_1_tready;
  assign axis_resize_1_tvalid = ~output_queue_1_empty;


  // OUTPUT QUEUE 2

  // Wires
  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_2_input_tdata;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_2_input_tkeep;
  reg  [TUSER_WIDTH-1:0]               output_queue_2_input_tuser;
  reg                                  output_queue_2_input_tlast;

  reg                                  write_to_output_queue_2;
  wire                                 read_from_output_queue_2;

  wire                                 output_queue_2_nearly_full;
  wire                                 output_queue_2_empty;

  // Module
  fallthrough_small_fifo
  #(
    .WIDTH(OUT_TDATA_WIDTH+TUSER_WIDTH+OUT_TDATA_WIDTH/8+1),
    .MAX_DEPTH_BITS(4)
  )
  output_fifo_2
  (
    .din         ({output_queue_2_input_tdata, output_queue_2_input_tkeep, output_queue_2_input_tuser, output_queue_2_input_tlast}),
    .wr_en       (write_to_output_queue_2),
    .rd_en       (read_from_output_queue_2),
    .dout        ({axis_resize_2_tdata, axis_resize_2_tkeep, axis_resize_2_tuser, axis_resize_2_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (output_queue_2_nearly_full),
    .empty       (output_queue_2_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  // Logic
  assign read_from_output_queue_2 = axis_resize_2_tvalid & axis_resize_2_tready;
  assign axis_resize_2_tvalid = ~output_queue_2_empty;


  // OUTPUT QUEUE 3

  // Wires
  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_3_input_tdata;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_3_input_tkeep;
  reg  [TUSER_WIDTH-1:0]               output_queue_3_input_tuser;
  reg                                  output_queue_3_input_tlast;

  reg                                  write_to_output_queue_3;
  wire                                 read_from_output_queue_3;

  wire                                 output_queue_3_nearly_full;
  wire                                 output_queue_3_empty;

  // Module
  fallthrough_small_fifo
  #(
    .WIDTH(OUT_TDATA_WIDTH+TUSER_WIDTH+OUT_TDATA_WIDTH/8+1),
    .MAX_DEPTH_BITS(4)
  )
  output_fifo_3
  (
    .din         ({output_queue_3_input_tdata, output_queue_3_input_tkeep, output_queue_3_input_tuser, output_queue_3_input_tlast}),
    .wr_en       (write_to_output_queue_3),
    .rd_en       (read_from_output_queue_3),
    .dout        ({axis_resize_3_tdata, axis_resize_3_tkeep, axis_resize_3_tuser, axis_resize_3_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (output_queue_3_nearly_full),
    .empty       (output_queue_3_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  // Logic
  assign read_from_output_queue_3 = axis_resize_3_tvalid & axis_resize_3_tready;
  assign axis_resize_3_tvalid = ~output_queue_3_empty;

  // INPUT TO OUTPUT LOGIC

  // Registers
  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_0_input_tdata_next;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_0_input_tkeep_next;
  reg  [TUSER_WIDTH-1:0]               output_queue_0_input_tuser_next;
  reg                                  output_queue_0_input_tlast_next;
  reg                                  write_to_output_queue_0_next;

  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_1_input_tdata_next;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_1_input_tkeep_next;
  reg  [TUSER_WIDTH-1:0]               output_queue_1_input_tuser_next;
  reg                                  output_queue_1_input_tlast_next;
  reg                                  write_to_output_queue_1_next;

  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_2_input_tdata_next;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_2_input_tkeep_next;
  reg  [TUSER_WIDTH-1:0]               output_queue_2_input_tuser_next;
  reg                                  output_queue_2_input_tlast_next;
  reg                                  write_to_output_queue_2_next;

  reg  [OUT_TDATA_WIDTH - 1:0]         output_queue_3_input_tdata_next;
  reg  [((OUT_TDATA_WIDTH / 8)) - 1:0] output_queue_3_input_tkeep_next;
  reg  [TUSER_WIDTH-1:0]               output_queue_3_input_tuser_next;
  reg                                  output_queue_3_input_tlast_next;
  reg                                  write_to_output_queue_3_next;

  // Logic
  assign axis_original_tready = ~output_queue_0_nearly_full & ~output_queue_1_nearly_full & ~output_queue_2_nearly_full & ~output_queue_3_nearly_full;

  always @(*) begin
    output_queue_0_input_tdata_next = 0;
    output_queue_0_input_tkeep_next = 0;
    output_queue_0_input_tuser_next = 0;
    output_queue_0_input_tlast_next = 0;
    write_to_output_queue_0_next    = 0;

    output_queue_1_input_tdata_next = 0;
    output_queue_1_input_tkeep_next = 0;
    output_queue_1_input_tuser_next = 0;
    output_queue_1_input_tlast_next = 0;
    write_to_output_queue_1_next    = 0;

    output_queue_2_input_tdata_next = 0;
    output_queue_2_input_tkeep_next = 0;
    output_queue_2_input_tuser_next = 0;
    output_queue_2_input_tlast_next = 0;
    write_to_output_queue_2_next    = 0;

    output_queue_3_input_tdata_next = 0;
    output_queue_3_input_tkeep_next = 0;
    output_queue_3_input_tuser_next = 0;
    output_queue_3_input_tlast_next = 0;
    write_to_output_queue_3_next    = 0;

    if (axis_original_tready & axis_original_tvalid) begin
      output_queue_0_input_tdata_next = axis_original_tdata[(OUT_TDATA_WIDTH * 1) - 1:OUT_TDATA_WIDTH * 0];
      output_queue_0_input_tkeep_next = axis_original_tkeep[((OUT_TDATA_WIDTH / 8) * 1) - 1:(OUT_TDATA_WIDTH / 8) * 0];
      output_queue_0_input_tuser_next = axis_original_tuser;
      output_queue_0_input_tlast_next = axis_original_tlast;
      write_to_output_queue_0_next    = 1;

      output_queue_1_input_tdata_next = axis_original_tdata[(OUT_TDATA_WIDTH * 2) - 1:OUT_TDATA_WIDTH * 1];
      output_queue_1_input_tkeep_next = axis_original_tkeep[((OUT_TDATA_WIDTH / 8) * 2) - 1:(OUT_TDATA_WIDTH / 8) * 1];
      output_queue_1_input_tuser_next = axis_original_tuser;
      output_queue_1_input_tlast_next = axis_original_tlast;
      write_to_output_queue_1_next    = 1;

      output_queue_2_input_tdata_next = axis_original_tdata[(OUT_TDATA_WIDTH * 3) - 1:OUT_TDATA_WIDTH * 2];
      output_queue_2_input_tkeep_next = axis_original_tkeep[((OUT_TDATA_WIDTH / 8) * 3) - 1:(OUT_TDATA_WIDTH / 8) * 2];
      output_queue_2_input_tuser_next = axis_original_tuser;
      output_queue_2_input_tlast_next = axis_original_tlast;
      write_to_output_queue_2_next    = 1;

      output_queue_3_input_tdata_next = axis_original_tdata[(OUT_TDATA_WIDTH * 4) - 1:OUT_TDATA_WIDTH * 3];
      output_queue_3_input_tkeep_next = axis_original_tkeep[((OUT_TDATA_WIDTH / 8) * 4) - 1:(OUT_TDATA_WIDTH / 8) * 3];
      output_queue_3_input_tuser_next = axis_original_tuser;
      output_queue_3_input_tlast_next = axis_original_tlast;
      write_to_output_queue_3_next    = 1;
    end
  end

  always @(posedge axis_aclk) begin
    if (~axis_resetn) begin
      output_queue_0_input_tdata <= 0;
      output_queue_0_input_tkeep <= 0;
      output_queue_0_input_tuser <= 0;
      output_queue_0_input_tlast <= 0;
      write_to_output_queue_0    <= 0;

      output_queue_1_input_tdata <= 0;
      output_queue_1_input_tkeep <= 0;
      output_queue_1_input_tuser <= 0;
      output_queue_1_input_tlast <= 0;
      write_to_output_queue_1    <= 0;

      output_queue_2_input_tdata <= 0;
      output_queue_2_input_tkeep <= 0;
      output_queue_2_input_tuser <= 0;
      output_queue_2_input_tlast <= 0;
      write_to_output_queue_2    <= 0;

      output_queue_3_input_tdata <= 0;
      output_queue_3_input_tkeep <= 0;
      output_queue_3_input_tuser <= 0;
      output_queue_3_input_tlast <= 0;
      write_to_output_queue_3    <= 0;
    end else begin
      output_queue_0_input_tdata <= output_queue_0_input_tdata_next;
      output_queue_0_input_tkeep <= output_queue_0_input_tkeep_next;
      output_queue_0_input_tuser <= output_queue_0_input_tuser_next;
      output_queue_0_input_tlast <= output_queue_0_input_tlast_next;
      write_to_output_queue_0    <= write_to_output_queue_0_next;

      output_queue_1_input_tdata <= output_queue_1_input_tdata_next;
      output_queue_1_input_tkeep <= output_queue_1_input_tkeep_next;
      output_queue_1_input_tuser <= output_queue_1_input_tuser_next;
      output_queue_1_input_tlast <= output_queue_1_input_tlast_next;
      write_to_output_queue_1    <= write_to_output_queue_1_next;

      output_queue_2_input_tdata <= output_queue_2_input_tdata_next;
      output_queue_2_input_tkeep <= output_queue_2_input_tkeep_next;
      output_queue_2_input_tuser <= output_queue_2_input_tuser_next;
      output_queue_2_input_tlast <= output_queue_2_input_tlast_next;
      write_to_output_queue_2    <= write_to_output_queue_2_next;

      output_queue_3_input_tdata <= output_queue_3_input_tdata_next;
      output_queue_3_input_tkeep <= output_queue_3_input_tkeep_next;
      output_queue_3_input_tuser <= output_queue_3_input_tuser_next;
      output_queue_3_input_tlast <= output_queue_3_input_tlast_next;
      write_to_output_queue_3    <= write_to_output_queue_3_next;
    end
  end

endmodule