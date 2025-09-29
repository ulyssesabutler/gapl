module jpeg_to_bitmap
#(
    parameter TDATA_WIDTH      = 256,
    parameter TUSER_WIDTH      = 128,

    parameter MAX_IMAGE_HEIGHT = 50,
    parameter MAX_IMAGE_WIDTH  = 50,

    localparam TKEEP_WIDTH     = TDATA_WIDTH / 8
)
(
    // Global Ports
    input                      axis_aclk,
    input                      axis_resetn,

    // Module input
    input  [TDATA_WIDTH - 1:0] jpeg_axis_tdata,
    input  [TKEEP_WIDTH - 1:0] jpeg_axis_tkeep,
    input  [TUSER_WIDTH - 1:0] jpeg_axis_tuser,
    input                      jpeg_axis_tvalid,
    output                     jpeg_axis_tready,
    input                      jpeg_axis_tlast,

    // Module output
    output [15:0]              bitmap_height,
    output [15:0]              bitmap_width,

    output [TDATA_WIDTH - 1:0] bitmap_axis_tdata,
    output [TKEEP_WIDTH - 1:0] bitmap_axis_tkeep,
    output [TUSER_WIDTH - 1:0] bitmap_axis_tuser,
    output                     bitmap_axis_tvalid,
    input                      bitmap_axis_tready,
    output                     bitmap_axis_tlast
);


    // 1. DEFINE STATE REGISTERS

    // Registers
    reg [7:0]  r_intensity_buffers      [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];
    reg [7:0]  g_intensity_buffers      [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];
    reg [7:0]  b_intensity_buffers      [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];

    reg        valid_pixel_buffers      [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];

    reg [1:0]  state;

    reg [15:0] current_image_height;
    reg [15:0] current_image_width;

    reg [31:0] current_image_size;
    reg [31:0] received_pixel_count;

    reg [15:0] current_pixel_x;
    reg [15:0] current_pixel_y;

    // Next values for registers
    reg [7:0]  r_intensity_buffers_next [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];
    reg [7:0]  g_intensity_buffers_next [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];
    reg [7:0]  b_intensity_buffers_next [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];

    reg        valid_pixel_buffers_next [MAX_IMAGE_HEIGHT - 1:0][MAX_IMAGE_WIDTH - 1:0];

    reg [1:0]  state_next;

    reg [15:0] current_image_height_next;
    reg [15:0] current_image_width_next;

    reg [31:0] current_image_size_next;
    reg [31:0] received_pixel_count_next;

    reg [15:0] current_pixel_x_next;
    reg [15:0] current_pixel_y_next;

    // Iteration variables, these shouldn't actually be synthesized, but are just used so we can perform operations on the entire arrays
    integer i, j;

    // Define states
    localparam WAITING_STATE      = 0;
    localparam RECEIVING_STATE    = 1;
    localparam TRANSMITTING_STATE = 2;

    // Hook state variables up to output
    assign bitmap_height = current_image_height;
    assign bitmap_width  = current_image_width;


    // 2. DECODE JEPG INPUT

    // Data width converter
    wire [31:0] jpeg_decoder_in_axis_tdata;
    wire [3:0]  jpeg_decoder_in_axis_tkeep;
    wire        jpeg_decoder_in_axis_tvalid;
    wire        jpeg_decoder_in_axis_tready;
    wire        jpeg_decoder_in_axis_tlast;

    axis_data_width_converter #(.IN_TDATA_WIDTH(TDATA_WIDTH), .OUT_TDATA_WIDTH(32)) input_to_jpeg_decoder_width
    (
        .axis_aclk(axis_aclk),
        .axis_resetn(axis_resetn),

        .axis_original_tdata(jpeg_axis_tdata),
        .axis_original_tkeep(jpeg_axis_tkeep),
        .axis_original_tuser(0),
        .axis_original_tvalid(jpeg_axis_tvalid),
        .axis_original_tready(jpeg_axis_tready),
        .axis_original_tlast(jpeg_axis_tlast),

        .axis_resize_tdata(jpeg_decoder_in_axis_tdata),
        .axis_resize_tkeep(jpeg_decoder_in_axis_tkeep),
        .axis_resize_tvalid(jpeg_decoder_in_axis_tvalid),
        .axis_resize_tready(jpeg_decoder_in_axis_tready),
        .axis_resize_tlast(jpeg_decoder_in_axis_tlast)
    );

    // JPEG decoder
    wire        jpeg_decoder_out_valid;
    wire        jpeg_decoder_out_ready;
    wire [15:0] jpeg_decoder_out_width;
    wire [15:0] jpeg_decoder_out_height;
    wire [15:0] jpeg_decoder_out_x;
    wire [15:0] jpeg_decoder_out_y;
    wire [7:0]  jpeg_decoder_out_r_intensity;
    wire [7:0]  jpeg_decoder_out_g_intensity;
    wire [7:0]  jpeg_decoder_out_b_intensity;

    jpeg_core jpeg_decoder
    (
        .clk_i(axis_aclk),
        .rst_i(~axis_resetn),
        .inport_valid_i(jpeg_decoder_in_axis_tvalid),
        .inport_accept_o(jpeg_decoder_in_axis_tready),
        .inport_data_i(jpeg_decoder_in_axis_tdata),
        .inport_strb_i(jpeg_decoder_in_axis_tkeep),
        .inport_last_i(jpeg_decoder_in_axis_tlast),
        //.idle_o(),

        .outport_valid_o(jpeg_decoder_out_valid),
        .outport_accept_i(jpeg_decoder_out_ready),
        .outport_width_o(jpeg_decoder_out_width),
        .outport_height_o(jpeg_decoder_out_height),
        .outport_pixel_x_o(jpeg_decoder_out_x),
        .outport_pixel_y_o(jpeg_decoder_out_y),
        .outport_pixel_r_o(jpeg_decoder_out_r_intensity),
        .outport_pixel_g_o(jpeg_decoder_out_g_intensity),
        .outport_pixel_b_o(jpeg_decoder_out_b_intensity)
    );

    assign jpeg_decoder_out_ready = state == WAITING_STATE | state == RECEIVING_STATE;


    // 3. CREATE THE OUTPUT QUEUE

    // Define the input registers
    reg [TDATA_WIDTH - 1:0] output_queue_in_tdata;
    reg [TKEEP_WIDTH - 1:0] output_queue_in_tkeep;
    reg [TUSER_WIDTH - 1:0] output_queue_in_tuser;
    reg                     output_queue_in_tlast;

    reg                     write_to_output_queue;

    // Define the next values of those registers
    reg [TDATA_WIDTH - 1:0] output_queue_in_tdata_next;
    reg [TKEEP_WIDTH - 1:0] output_queue_in_tkeep_next;
    reg [TUSER_WIDTH - 1:0] output_queue_in_tuser_next;
    reg                     output_queue_in_tlast_next;

    reg                     write_to_output_queue_next;

    // Define the remaining wires
    wire                    send_from_module;
    wire                    output_queue_nearly_full;
    wire                    output_queue_empty;

    // Define the queue
    fallthrough_small_fifo
    #(
        .WIDTH(TDATA_WIDTH + TUSER_WIDTH + TKEEP_WIDTH + 1),
        .MAX_DEPTH_BITS(4)
    )
    output_fifo
    (
        .din         ({output_queue_in_tdata, output_queue_in_tkeep, output_queue_in_tuser, output_queue_in_tlast}),
        .wr_en       (write_to_output_queue),
        .rd_en       (send_from_module),
        .dout        ({bitmap_axis_tdata, bitmap_axis_tkeep, bitmap_axis_tuser, bitmap_axis_tlast}),
        .full        (),
        .prog_full   (),
        .nearly_full (output_queue_nearly_full),
        .empty       (output_queue_empty),
        .reset       (~axis_resetn),
        .clk         (axis_aclk)
    );

    assign send_from_module   = bitmap_axis_tready & bitmap_axis_tvalid;
    assign bitmap_axis_tvalid = ~output_queue_empty;


    // 4. TRANSMIT THE NEXT PIXEL

    // Compute the output queue input
    wire transmitting_pixel = valid_pixel_buffers[current_pixel_y][current_pixel_x]; // TODO: Do we also need to check state?

    always @(*) begin
        output_queue_in_tdata_next = 0;
        output_queue_in_tkeep_next = 0;
        output_queue_in_tuser_next = 0;
        output_queue_in_tlast_next = 0;

        write_to_output_queue_next = 0;

        if (transmitting_pixel) begin
            output_queue_in_tdata_next = {
                {(TDATA_WIDTH - (3 * 8)){1'b0}},
                b_intensity_buffers[current_pixel_y][current_pixel_x],
                g_intensity_buffers[current_pixel_y][current_pixel_x],
                r_intensity_buffers[current_pixel_y][current_pixel_x]
            };
            output_queue_in_tkeep_next = {
                {(TKEEP_WIDTH - 3){1'b0}},
                3'b111
            };
            output_queue_in_tlast_next =
                current_pixel_x == (current_image_width - 1) &&
                current_pixel_y == (current_image_height - 1);

            write_to_output_queue_next = 1;
        end
    end

    // Update the state after transmitting a pixel
    reg finished_transmitting;

    always @(*) begin
        current_pixel_x_next  = current_pixel_x;
        current_pixel_y_next  = current_pixel_y;
        finished_transmitting = 0;

        if (transmitting_pixel) begin
            current_pixel_x_next = current_pixel_x_next + 1;

            if (current_pixel_x_next >= current_image_width) begin
                current_pixel_x_next = 0;
                current_pixel_y_next = current_pixel_y_next + 1;
            end

            if (current_pixel_y_next >= current_image_height) begin
                current_pixel_y_next  = 0;
                finished_transmitting = 1;
            end
        end
    end


    // 5. COMPUTE THE NEXT REGISTER VALUES FOR THE PIXEL BUFFERS

    // Flag to indicate when a pixel is being received
    wire received_pixel           = jpeg_decoder_out_valid && jpeg_decoder_out_ready;
    wire received_pixel_in_bounds = (jpeg_decoder_out_y < current_image_height) && (jpeg_decoder_out_x < current_image_width);

    // logic to add new pixels when writing, and remove old pixels when they're read
    always @(*) begin
        // Set default values. That is, the existing buffer values
        for (i = 0; i < MAX_IMAGE_HEIGHT; i = i + 1) begin
            for (j = 0; j < MAX_IMAGE_WIDTH; j = j + 1) begin
                r_intensity_buffers_next[i][j] = r_intensity_buffers[i][j];
                g_intensity_buffers_next[i][j] = g_intensity_buffers[i][j];
                b_intensity_buffers_next[i][j] = b_intensity_buffers[i][j];

                valid_pixel_buffers_next[i][j] = valid_pixel_buffers[i][j];
            end
        end

        // Write to the buffer from the decoder
        if (received_pixel) begin
            r_intensity_buffers_next[jpeg_decoder_out_y][jpeg_decoder_out_x] = jpeg_decoder_out_r_intensity;
            g_intensity_buffers_next[jpeg_decoder_out_y][jpeg_decoder_out_x] = jpeg_decoder_out_g_intensity;
            b_intensity_buffers_next[jpeg_decoder_out_y][jpeg_decoder_out_x] = jpeg_decoder_out_b_intensity;

            valid_pixel_buffers_next[jpeg_decoder_out_y][jpeg_decoder_out_x] = 1;
        end

        // Mark a given pixel as read
        if (transmitting_pixel) begin
            // r_intensity_buffers_next[current_pixel_y][current_pixel_x] = 0;
            // g_intensity_buffers_next[current_pixel_y][current_pixel_x] = 0;
            // b_intensity_buffers_next[current_pixel_y][current_pixel_x] = 0;

            valid_pixel_buffers_next[current_pixel_y][current_pixel_x] = 0;
        end
    end

    // COMPUTE THE NEXT REGISTER VALUES FOR THE STATE
    always @(*) begin
        state_next = state;

        current_image_height_next = current_image_height;
        current_image_width_next  = current_image_width;

        current_image_size_next   = current_image_size;
        received_pixel_count_next = received_pixel_count;

        case (state)

            WAITING_STATE: begin
                if (received_pixel) begin
                    state_next = RECEIVING_STATE;

                    current_image_height_next = jpeg_decoder_out_height;
                    current_image_width_next  = jpeg_decoder_out_width;

                    current_image_size_next   = current_image_height_next * current_image_width_next;

                    if (received_pixel_in_bounds)
                        received_pixel_count_next = 1;
                    else
                        received_pixel_count_next = 0;
                end
            end

            RECEIVING_STATE: begin
                if (received_pixel && received_pixel_in_bounds) begin
                    received_pixel_count_next = received_pixel_count + 1;

                    if (received_pixel_count_next >= current_image_size) state_next = TRANSMITTING_STATE;
                end
            end

            TRANSMITTING_STATE: begin
                if (finished_transmitting) state_next = WAITING_STATE;
            end

        endcase
    end

    // WRITE TO REGISTERS
    always @(posedge axis_aclk) begin
        if (~axis_resetn) begin
            for (i = 0; i < MAX_IMAGE_HEIGHT; i = i + 1) begin
                for (j = 0; j < MAX_IMAGE_WIDTH; j = j + 1) begin
                    r_intensity_buffers[i][j] <= 0;
                    g_intensity_buffers[i][j] <= 0;
                    b_intensity_buffers[i][j] <= 0;

                    valid_pixel_buffers[i][j] <= 0;
                end
            end

            state <= WAITING_STATE;

            current_image_height <= 0;
            current_image_width  <= 0;

            current_pixel_x <= 0;
            current_pixel_y <= 0;

            current_image_size   <= 0;
            received_pixel_count <= 0;

            output_queue_in_tdata <= 0;
            output_queue_in_tkeep <= 0;
            output_queue_in_tuser <= 0;
            output_queue_in_tlast <= 0;

            write_to_output_queue <= 0;
        end else begin
            for (i = 0; i < MAX_IMAGE_HEIGHT; i = i + 1) begin
                for (j = 0; j < MAX_IMAGE_WIDTH; j = j + 1) begin
                    r_intensity_buffers[i][j] <= r_intensity_buffers_next[i][j];
                    g_intensity_buffers[i][j] <= g_intensity_buffers_next[i][j];
                    b_intensity_buffers[i][j] <= b_intensity_buffers_next[i][j];

                    valid_pixel_buffers[i][j] <= valid_pixel_buffers_next[i][j];
                end
            end

            state <= state_next;

            current_image_height <= current_image_height_next;
            current_image_width  <= current_image_width_next;

            current_pixel_x <= current_pixel_x_next;
            current_pixel_y <= current_pixel_y_next;

            current_image_size   <= current_image_size_next;
            received_pixel_count <= received_pixel_count_next;

            output_queue_in_tdata <= output_queue_in_tdata_next;
            output_queue_in_tkeep <= output_queue_in_tkeep_next;
            output_queue_in_tuser <= output_queue_in_tuser_next;
            output_queue_in_tlast <= output_queue_in_tlast_next;

            write_to_output_queue <= write_to_output_queue_next;
        end
    end

endmodule