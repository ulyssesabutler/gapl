module axis_transmission_combiner
#(
  // AXI Stream Data Width
  parameter TDATA_WIDTH = 256,
  parameter TUSER_WIDTH = 128
)
(
  // Global Ports
  input                              axis_aclk,
  input                              axis_resetn,

  input  [TDATA_WIDTH - 1:0]         axis_input_0_tdata,
  input  [((TDATA_WIDTH / 8)) - 1:0] axis_input_0_tkeep,
  input  [TUSER_WIDTH-1:0]           axis_input_0_tuser,
  input                              axis_input_0_tvalid,
  output                             axis_input_0_tready,
  input                              axis_input_0_tlast,

  input  [TDATA_WIDTH - 1:0]         axis_input_1_tdata,
  input  [((TDATA_WIDTH / 8)) - 1:0] axis_input_1_tkeep,
  input  [TUSER_WIDTH-1:0]           axis_input_1_tuser,
  input                              axis_input_1_tvalid,
  output                             axis_input_1_tready,
  input                              axis_input_1_tlast,

  input  [TDATA_WIDTH - 1:0]         axis_input_2_tdata,
  input  [((TDATA_WIDTH / 8)) - 1:0] axis_input_2_tkeep,
  input  [TUSER_WIDTH-1:0]           axis_input_2_tuser,
  input                              axis_input_2_tvalid,
  output                             axis_input_2_tready,
  input                              axis_input_2_tlast,

  input  [TDATA_WIDTH - 1:0]         axis_input_3_tdata,
  input  [((TDATA_WIDTH / 8)) - 1:0] axis_input_3_tkeep,
  input  [TUSER_WIDTH-1:0]           axis_input_3_tuser,
  input                              axis_input_3_tvalid,
  output                             axis_input_3_tready,
  input                              axis_input_3_tlast,

  output [TDATA_WIDTH - 1:0]         axis_combined_tdata,
  output [((TDATA_WIDTH / 8)) - 1:0] axis_combined_tkeep,
  output [TUSER_WIDTH - 1:0]         axis_combined_tuser,
  output                             axis_combined_tvalid,
  input                              axis_combined_tready,
  output                             axis_combined_tlast
);

  // Input queue 0
  wire [TDATA_WIDTH - 1:0]          input_queue_0_tdata;
  wire [((TDATA_WIDTH / 8)) - 1:0]  input_queue_0_tkeep;
  wire [TUSER_WIDTH-1:0]            input_queue_0_tuser;
  wire                              input_queue_0_tlast;

  wire                              write_to_input_queue_0;
  wire                              read_from_input_queue_0;

  wire                              input_queue_0_nearly_full;
  wire                              input_queue_0_empty;

  fallthrough_small_fifo
  #(
    .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1), // Fit the whole AXIS packet and the headers
    .MAX_DEPTH_BITS(4)
  )
  input_queue_0
  (
    .din         ({axis_input_0_tdata, axis_input_0_tkeep, axis_input_0_tuser, axis_input_0_tlast}),
    .wr_en       (write_to_input_queue_0),
    .rd_en       (read_from_input_queue_0),
    .dout        ({input_queue_0_tdata, input_queue_0_tkeep, input_queue_0_tuser, input_queue_0_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (input_queue_0_nearly_full),
    .empty       (input_queue_0_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  wire input_queue_0_null = ~|input_queue_0_tkeep;

  assign write_to_input_queue_0 = axis_input_0_tready & axis_input_0_tvalid;
  assign axis_input_0_tready = ~input_queue_0_nearly_full;

  // Input queue 1
  wire [TDATA_WIDTH - 1:0]          input_queue_1_tdata;
  wire [((TDATA_WIDTH / 8)) - 1:0]  input_queue_1_tkeep;
  wire [TUSER_WIDTH-1:0]            input_queue_1_tuser;
  wire                              input_queue_1_tlast;

  wire                              write_to_input_queue_1;
  wire                              read_from_input_queue_1;

  wire                              input_queue_1_nearly_full;
  wire                              input_queue_1_empty;

  fallthrough_small_fifo
  #(
    .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1), // Fit the whole AXIS packet and the headers
    .MAX_DEPTH_BITS(4)
  )
  input_queue_1
  (
    .din         ({axis_input_1_tdata, axis_input_1_tkeep, axis_input_1_tuser, axis_input_1_tlast}),
    .wr_en       (write_to_input_queue_1),
    .rd_en       (read_from_input_queue_1),
    .dout        ({input_queue_1_tdata, input_queue_1_tkeep, input_queue_1_tuser, input_queue_1_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (input_queue_1_nearly_full),
    .empty       (input_queue_1_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  wire input_queue_1_null = ~|input_queue_1_tkeep;

  assign write_to_input_queue_1 = axis_input_1_tready & axis_input_1_tvalid;
  assign axis_input_1_tready = ~input_queue_1_nearly_full;

  // Input queue 2
  wire [TDATA_WIDTH - 1:0]          input_queue_2_tdata;
  wire [((TDATA_WIDTH / 8)) - 1:0]  input_queue_2_tkeep;
  wire [TUSER_WIDTH-1:0]            input_queue_2_tuser;
  wire                              input_queue_2_tlast;

  wire                              write_to_input_queue_2;
  wire                              read_from_input_queue_2;

  wire                              input_queue_2_nearly_full;
  wire                              input_queue_2_empty;

  fallthrough_small_fifo
  #(
    .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1), // Fit the whole AXIS packet and the headers
    .MAX_DEPTH_BITS(4)
  )
  input_queue_2
  (
    .din         ({axis_input_2_tdata, axis_input_2_tkeep, axis_input_2_tuser, axis_input_2_tlast}),
    .wr_en       (write_to_input_queue_2),
    .rd_en       (read_from_input_queue_2),
    .dout        ({input_queue_2_tdata, input_queue_2_tkeep, input_queue_2_tuser, input_queue_2_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (input_queue_2_nearly_full),
    .empty       (input_queue_2_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  wire input_queue_2_null = ~|input_queue_2_tkeep;

  assign write_to_input_queue_2 = axis_input_2_tready & axis_input_2_tvalid;
  assign axis_input_2_tready = ~input_queue_2_nearly_full;

  // Input queue 3
  wire [TDATA_WIDTH - 1:0]         input_queue_3_tdata;
  wire [((TDATA_WIDTH / 8)) - 1:0] input_queue_3_tkeep;
  wire [TUSER_WIDTH-1:0]           input_queue_3_tuser;
  wire                             input_queue_3_tlast;

  wire                             write_to_input_queue_3;
  wire                             read_from_input_queue_3;

  wire                             input_queue_3_nearly_full;
  wire                             input_queue_3_empty;

  fallthrough_small_fifo
  #(
    .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1), // Fit the whole AXIS packet and the headers
    .MAX_DEPTH_BITS(4)
  )
  input_queue_3
  (
    .din         ({axis_input_3_tdata, axis_input_3_tkeep, axis_input_3_tuser, axis_input_3_tlast}),
    .wr_en       (write_to_input_queue_3),
    .rd_en       (read_from_input_queue_3),
    .dout        ({input_queue_3_tdata, input_queue_3_tkeep, input_queue_3_tuser, input_queue_3_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (input_queue_3_nearly_full),
    .empty       (input_queue_3_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  wire input_queue_3_null = ~|input_queue_3_tkeep;

  assign write_to_input_queue_3 = axis_input_3_tready & axis_input_3_tvalid;
  assign axis_input_3_tready = ~input_queue_3_nearly_full;

  // Output Queue
  reg [TDATA_WIDTH - 1:0]       output_queue_tdata;
  reg [(TDATA_WIDTH / 8) - 1:0] output_queue_tkeep;
  reg [TUSER_WIDTH - 1:0]       output_queue_tuser;
  reg                           output_queue_tlast;
  
  reg                           write_to_output_queue;
  wire                          output_queue_nearly_full;
  wire                          output_queue_empty;

  fallthrough_small_fifo
  #(
    .WIDTH(TDATA_WIDTH+TUSER_WIDTH+TDATA_WIDTH/8+1),
    .MAX_DEPTH_BITS(4)
  )
  output_fifo
  (
    .din         ({output_queue_tdata, output_queue_tkeep, output_queue_tuser, output_queue_tlast}),
    .wr_en       (write_to_output_queue),
    .rd_en       (send_from_module),
    .dout        ({axis_combined_tdata, axis_combined_tkeep, axis_combined_tuser, axis_combined_tlast}),
    .full        (),
    .prog_full   (),
    .nearly_full (output_queue_nearly_full),
    .empty       (output_queue_empty),
    .reset       (~axis_resetn),
    .clk         (axis_aclk)
  );

  assign send_from_module     = axis_combined_tvalid & axis_combined_tready;
  assign axis_combined_tvalid = ~output_queue_empty;

  // FSM
  reg [1:0]                        current_input;

  // Current Input Queue
  wire [TDATA_WIDTH - 1:0]         current_input_queue_tdata;
  wire [((TDATA_WIDTH / 8)) - 1:0] current_input_queue_tkeep;
  wire [TUSER_WIDTH-1:0]           current_input_queue_tuser;
  wire                             current_input_queue_tlast;

  wire                             current_input_queue_empty;

  assign current_input_queue_tdata    = current_input == 0 ? input_queue_0_tdata : current_input == 1 ? input_queue_1_tdata : current_input == 2 ? input_queue_2_tdata : input_queue_3_tdata;
  assign current_input_queue_tkeep    = current_input == 0 ? input_queue_0_tkeep : current_input == 1 ? input_queue_1_tkeep : current_input == 2 ? input_queue_2_tkeep : input_queue_3_tkeep;
  assign current_input_queue_tuser    = current_input == 0 ? input_queue_0_tuser : current_input == 1 ? input_queue_1_tuser : current_input == 2 ? input_queue_2_tuser : input_queue_3_tuser;
  assign current_input_queue_tlast    = current_input == 0 ? input_queue_0_tlast : current_input == 1 ? input_queue_1_tlast : current_input == 2 ? input_queue_2_tlast : input_queue_3_tlast;

  assign current_input_queue_empty    = current_input == 0 ? input_queue_0_empty : current_input == 1 ? input_queue_1_empty : current_input == 2 ? input_queue_2_empty : input_queue_3_empty;

  wire read_from_current_input_queue  = ((current_input == 0 & all_input_queues_are_not_empty) | (current_input != 0 & ~current_input_queue_empty)) & ~output_queue_nearly_full;

  assign read_from_input_queue_0      = read_from_current_input_queue & current_input == 0;
  assign read_from_input_queue_1      = read_from_current_input_queue & current_input == 1;
  assign read_from_input_queue_2      = read_from_current_input_queue & current_input == 2;
  assign read_from_input_queue_3      = read_from_current_input_queue & current_input == 3;

  // Logic
  reg [1:0]                     current_input_next;

  reg [TDATA_WIDTH - 1:0]       output_queue_tdata_next;
  reg [(TDATA_WIDTH / 8) - 1:0] output_queue_tkeep_next;
  reg [TUSER_WIDTH - 1:0]       output_queue_tuser_next;
  reg                           output_queue_tlast_next;
  
  reg                           write_to_output_queue_next;

  wire all_input_queues_are_not_empty = ~input_queue_0_empty & ~input_queue_1_empty & ~input_queue_2_empty & ~input_queue_3_empty;

  always @(*) begin
    current_input_next         = current_input;

    output_queue_tdata_next    = 0;
    output_queue_tkeep_next    = 0;
    output_queue_tuser_next    = 0;
    output_queue_tlast_next    = 0;
    
    write_to_output_queue_next = 0;

    if (read_from_current_input_queue) begin
      current_input_next      = current_input + 1;

      output_queue_tdata_next = current_input_queue_tdata;
      output_queue_tkeep_next = current_input_queue_tkeep;
      output_queue_tuser_next = current_input_queue_tuser;

      case (current_input)
        0: begin
          output_queue_tlast_next    = current_input_queue_tlast & input_queue_1_null;
          write_to_output_queue_next = ~input_queue_0_null;
        end

        1: begin
          output_queue_tlast_next    = current_input_queue_tlast & input_queue_2_null;
          write_to_output_queue_next = ~input_queue_1_null;
        end

        2: begin
          output_queue_tlast_next    = current_input_queue_tlast & input_queue_3_null;
          write_to_output_queue_next = ~input_queue_2_null;
        end

        3: begin
          output_queue_tlast_next    = current_input_queue_tlast;
          write_to_output_queue_next = ~input_queue_3_null;
        end
      endcase
    end
  end

  // Latch values
  always @(posedge axis_aclk) begin
    if (~axis_resetn) begin
      current_input         <= 0;

      output_queue_tdata    <= 0;
      output_queue_tkeep    <= 0;
      output_queue_tuser    <= 0;
      output_queue_tlast    <= 0;
      
      write_to_output_queue <= 0;
    end else begin
      current_input         <= current_input_next;

      output_queue_tdata    <= output_queue_tdata_next;
      output_queue_tkeep    <= output_queue_tkeep_next;
      output_queue_tuser    <= output_queue_tuser_next;
      output_queue_tlast    <= output_queue_tlast_next;
      
      write_to_output_queue <= write_to_output_queue_next;
    end
  end

endmodule
