module extract_channel_from_stream
#(
    parameter  TDATA_WIDTH         = 256,
    parameter  TKEEP_WIDTH         = TDATA_WIDTH / 8,
    parameter  TUSER_WIDTH         = 128,

    parameter  CHANNEL_COUNT      = 3,
    parameter  CHANNEL_OFFSET     = 0,

    parameter  ITEM_WIDTH         = 8,
    localparam ITEM_COUNT         = TDATA_WIDTH / ITEM_WIDTH,
    localparam CHANNEL_COUNT_BITS = $clog2(CHANNEL_COUNT)
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] full_rgb_tdata,
    input  [TKEEP_WIDTH - 1:0] full_rgb_tkeep,
    input  [TUSER_WIDTH - 1:0] full_rgb_tuser,
    input                      full_rgb_tvalid,
    output                     full_rgb_tready,
    input                      full_rgb_tlast,

    // Module output
    output [TDATA_WIDTH - 1:0] single_channel_tdata,
    output [TKEEP_WIDTH - 1:0] single_channel_tkeep,
    output [TUSER_WIDTH - 1:0] single_channel_tuser,
    output                     single_channel_tvalid,
    input                      single_channel_tready,
    output                     single_channel_tlast
);

    // Flatten input
    wire [TDATA_WIDTH - 1:0] flattened_rgb_tdata;
    wire [TKEEP_WIDTH - 1:0] flattened_rgb_tkeep;
    wire [TUSER_WIDTH - 1:0] flattened_rgb_tuser;
    wire                     flattened_rgb_tvalid;
    wire                     flattened_rgb_tready;
    wire                     flattened_rgb_tlast;

    axis_flattener input_flattener
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_original_tdata(full_rgb_tdata),
        .axis_original_tkeep(full_rgb_tkeep),
        .axis_original_tuser(full_rgb_tuser),
        .axis_original_tvalid(full_rgb_tvalid),
        .axis_original_tready(full_rgb_tready),
        .axis_original_tlast(full_rgb_tlast),

        .axis_flattened_tdata(flattened_rgb_tdata),
        .axis_flattened_tkeep(flattened_rgb_tkeep),
        .axis_flattened_tuser(flattened_rgb_tuser),
        .axis_flattened_tvalid(flattened_rgb_tvalid),
        .axis_flattened_tready(flattened_rgb_tready),
        .axis_flattened_tlast(flattened_rgb_tlast)
    );

    // Flatten output
    wire [TDATA_WIDTH - 1:0] unflattened_single_channel_tdata;
    wire [TKEEP_WIDTH - 1:0] unflattened_single_channel_tkeep;
    wire [TUSER_WIDTH-1:0]   unflattened_single_channel_tuser;
    wire                     unflattened_single_channel_tvalid;
    wire                     unflattened_single_channel_tready;
    wire                     unflattened_single_channel_tlast;

    axis_flattener output_flattener
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_original_tdata(unflattened_single_channel_tdata),
        .axis_original_tkeep(unflattened_single_channel_tkeep),
        .axis_original_tuser(unflattened_single_channel_tuser),
        .axis_original_tvalid(unflattened_single_channel_tvalid),
        .axis_original_tready(unflattened_single_channel_tready),
        .axis_original_tlast(unflattened_single_channel_tlast),

        .axis_flattened_tdata(single_channel_tdata),
        .axis_flattened_tkeep(single_channel_tkeep),
        .axis_flattened_tuser(single_channel_tuser),
        .axis_flattened_tvalid(single_channel_tvalid),
        .axis_flattened_tready(single_channel_tready),
        .axis_flattened_tlast(single_channel_tlast)
    );

    // Output queue
    reg [TDATA_WIDTH - 1:0]       output_queue_tdata;
    reg [(TDATA_WIDTH / 8) - 1:0] output_queue_tkeep;
    reg [TUSER_WIDTH - 1:0]       output_queue_tuser;
    reg                           output_queue_tlast;
    
    reg                           write_to_output_queue;
    wire                          read_from_output_queue;
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
        .rd_en       (read_from_output_queue),
        .dout        ({unflattened_single_channel_tdata, unflattened_single_channel_tkeep, unflattened_single_channel_tuser, unflattened_single_channel_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    assign read_from_output_queue            = unflattened_single_channel_tvalid & unflattened_single_channel_tready;
    assign unflattened_single_channel_tvalid = ~output_queue_empty;

    // Track the offset for each trasmission
    reg [CHANNEL_COUNT_BITS - 1:0] current_transmission_channel_offset;


    // Advance the offset tracker
    reg [CHANNEL_COUNT_BITS - 1:0] current_transmission_channel_offset_next;

    always @(*) begin
        current_transmission_channel_offset_next = current_transmission_channel_offset;

        if (flattened_rgb_tvalid & flattened_rgb_tready) begin
            if (current_transmission_channel_offset + 1 >= CHANNEL_COUNT || flattened_rgb_tlast) begin
                current_transmission_channel_offset_next = CHANNEL_OFFSET;
            end else begin
                current_transmission_channel_offset_next = current_transmission_channel_offset + 1;
            end
        end
    end

    wire [TDATA_WIDTH - 1:0] extracted_single_channel_tdata;
    wire [TKEEP_WIDTH - 1:0] extracted_single_channel_tkeep;

    // Compute the extracted channels
    extract_channel_from_transmission_with_offset
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .ITEM_WIDTH(ITEM_WIDTH),
        .CHANNEL_COUNT(3)
    )
    extract_tdata
    (
        .full_rgb(flattened_rgb_tdata),
        .channel_offset(current_transmission_channel_offset),
        .single_channel(extracted_single_channel_tdata)
    );

    extract_channel_from_transmission_with_offset
    #(
        .TDATA_WIDTH(TKEEP_WIDTH),
        .ITEM_WIDTH(1),
        .CHANNEL_COUNT(3)
    )
    extract_tkeep
    (
        .full_rgb(flattened_rgb_tkeep),
        .channel_offset(current_transmission_channel_offset),
        .single_channel(extracted_single_channel_tkeep)
    );

    // Compute the next value for the output queue
    assign flattened_rgb_tready = ~output_queue_nearly_full;

    reg [TDATA_WIDTH - 1:0]       output_queue_tdata_next;
    reg [(TDATA_WIDTH / 8) - 1:0] output_queue_tkeep_next;
    reg [TUSER_WIDTH - 1:0]       output_queue_tuser_next;
    reg                           output_queue_tlast_next;
    
    reg                           write_to_output_queue_next;

    always @(*) begin
        output_queue_tdata_next    = 0;
        output_queue_tkeep_next    = 0;
        output_queue_tuser_next    = 0;
        output_queue_tlast_next    = 0;
        
        write_to_output_queue_next = 0;

        if (flattened_rgb_tready & flattened_rgb_tvalid) begin
            output_queue_tdata_next    = extracted_single_channel_tdata;
            output_queue_tkeep_next    = extracted_single_channel_tkeep;
            output_queue_tuser_next    = flattened_rgb_tuser;
            output_queue_tlast_next    = flattened_rgb_tlast;

            write_to_output_queue_next = 1;
        end
    end

    // Latch
    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            current_transmission_channel_offset <= CHANNEL_OFFSET;

            output_queue_tdata                  <= 0;
            output_queue_tkeep                  <= 0;
            output_queue_tuser                  <= 0;
            output_queue_tlast                  <= 0;
           
            write_to_output_queue               <= 0;
        end else begin
            current_transmission_channel_offset <= current_transmission_channel_offset_next;

            output_queue_tdata                  <= output_queue_tdata_next;
            output_queue_tkeep                  <= output_queue_tkeep_next;
            output_queue_tuser                  <= output_queue_tuser_next;
            output_queue_tlast                  <= output_queue_tlast_next;
           
            write_to_output_queue               <= write_to_output_queue_next;
        end
    end

endmodule