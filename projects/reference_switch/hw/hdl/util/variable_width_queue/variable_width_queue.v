module variable_width_queue
#(
    parameter IN_TDATA_WIDTH           = 256,
    parameter OUT_TDATA_WIDTH          = 256,

    parameter TUSER_WIDTH              = 128,

    localparam IN_TKEEP_WIDTH          = IN_TDATA_WIDTH / 8,
    localparam OUT_TKEEP_WIDTH         = OUT_TDATA_WIDTH / 8,

    localparam TDATA_BUFFER_WIDTH      = 2 * (IN_TDATA_WIDTH + OUT_TDATA_WIDTH),
    localparam TKEEP_BUFFER_WIDTH      = 2 * (IN_TKEEP_WIDTH + OUT_TKEEP_WIDTH),
    localparam TUSER_BUFFER_WIDTH      = TUSER_WIDTH,
    
    localparam IN_TDATA_WIDTH_BITS     = $clog2(IN_TDATA_WIDTH + 1),
    localparam IN_TKEEP_WIDTH_BITS     = $clog2(IN_TKEEP_WIDTH + 1),

    localparam OUT_TDATA_WIDTH_BITS    = $clog2(OUT_TDATA_WIDTH + 1),
    localparam OUT_TKEEP_WIDTH_BITS    = $clog2(OUT_TKEEP_WIDTH + 1),

    localparam TDATA_BUFFER_WIDTH_BITS = $clog2(TDATA_BUFFER_WIDTH + 1),
    localparam TKEEP_BUFFER_WIDTH_BITS = $clog2(TKEEP_BUFFER_WIDTH + 1),

    localparam IN_TDATA_SIZE_BITS      = $clog2(IN_TDATA_WIDTH + 2),
    localparam IN_TKEEP_SIZE_BITS      = $clog2(IN_TKEEP_WIDTH + 2),

    localparam OUT_TDATA_SIZE_BITS     = $clog2(OUT_TDATA_WIDTH + 2),
    localparam OUT_TKEEP_SIZE_BITS     = $clog2(OUT_TKEEP_WIDTH + 2),

    localparam TDATA_BUFFER_SIZE_BITS  = $clog2(TDATA_BUFFER_WIDTH + 2),
    localparam TKEEP_BUFFER_SIZE_BITS  = $clog2(TKEEP_BUFFER_WIDTH + 2)
)
(
    input                              clk,
    input                              resetn,

    input      [IN_TDATA_WIDTH - 1:0]  tdata_in,
    input      [IN_TKEEP_WIDTH - 1:0]  tkeep_in,
    input      [TUSER_WIDTH - 1:0]     tuser_in,
    input                              tlast_in,
    input                              write,

    output reg [OUT_TDATA_WIDTH - 1:0] tdata_out,
    output reg [OUT_TKEEP_WIDTH - 1:0] tkeep_out,
    output reg [TUSER_WIDTH - 1:0]     tuser_out,
    output reg                         tlast_out,
    input                              read,

    output                             can_write,
    output                             can_read
);

    // OUTPUT
    wire [OUT_TDATA_WIDTH - 1:0] tdata_out_after_read;
    wire [OUT_TKEEP_WIDTH - 1:0] tkeep_out_after_read;
    wire [TUSER_WIDTH - 1:0]     tuser_out_after_read;
    wire                         tlast_out_after_read;

    // CIRCULAR BUFFER
    // - Create registers and wires
    reg [TDATA_BUFFER_WIDTH - 1:0]       tdata_buffer;
    reg [TKEEP_BUFFER_WIDTH - 1:0]       tkeep_buffer;
    reg [TUSER_BUFFER_WIDTH - 1:0]       tuser_buffer;
    reg                                  tlast_buffer;

    wire [TDATA_BUFFER_SIZE_BITS - 1:0]  tdata_buffer_size;
    reg  [TKEEP_BUFFER_SIZE_BITS - 1:0]  tkeep_buffer_size;

    reg  [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_start_index;
    reg  [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_end_index;

    wire [TDATA_BUFFER_WIDTH_BITS - 1:0] tdata_buffer_start_index = tkeep_buffer_start_index * 8;
    wire [TDATA_BUFFER_WIDTH_BITS - 1:0] tdata_buffer_end_index   = tkeep_buffer_end_index * 8;

    // - Buffer tracking logic
    assign tdata_buffer_size = tkeep_buffer_size * 8;

    assign can_write = ((TKEEP_BUFFER_WIDTH - tkeep_buffer_size) >= IN_TKEEP_WIDTH) & ~tlast_buffer;
    assign can_read  = (tkeep_buffer_size >= OUT_TKEEP_WIDTH) | tlast_buffer;

    // INPUT PROCESSING
    // - Create registers and wires
    wire [IN_TKEEP_SIZE_BITS - 1:0] input_tkeep_size;
    wire [IN_TDATA_SIZE_BITS - 1:0] input_tdata_size = input_tkeep_size * 8;

    // - Buffer tracking logic
    find_last_bit #(.INPUT_SIZE(IN_TKEEP_WIDTH)) find_input_tkeep_size
    (
        .in(tkeep_in),
        .bit_count(input_tkeep_size)
    );

    // WRITE TO BUFFER
    // - Indicate when to write to the buffer
    wire should_write_to_buffer  = can_write & write;

    // - Create registers and wires for the new buffer
    wire [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_mask_after_write;
    wire [TKEEP_BUFFER_WIDTH - 1:0] tkeep_buffer_mask_after_write;
    wire [TUSER_BUFFER_WIDTH - 1:0] tuser_buffer_after_write;
    wire                            tlast_buffer_after_write;

    // - Compute the new TDATA buffer
    wire [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_mask_after_write_front = tdata_in << tdata_buffer_end_index;
    wire [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_mask_after_write_back  = tdata_in >> (TDATA_BUFFER_WIDTH - tdata_buffer_end_index);

    assign tdata_buffer_mask_after_write = tdata_buffer_mask_after_write_front | ((tdata_buffer_start_index < tdata_buffer_end_index) ? tdata_buffer_mask_after_write_back : 0);

    // - Compute the new TKEEP buffer
    wire [TKEEP_BUFFER_WIDTH - 1:0] tkeep_buffer_mask_after_write_front = tkeep_in << tkeep_buffer_end_index;
    wire [TKEEP_BUFFER_WIDTH - 1:0] tkeep_buffer_mask_after_write_back  = tkeep_in >> (TKEEP_BUFFER_WIDTH - tkeep_buffer_end_index);

    assign tkeep_buffer_mask_after_write = tkeep_buffer_mask_after_write_front | ((tkeep_buffer_start_index < tkeep_buffer_end_index) ? tkeep_buffer_mask_after_write_back : 0);

    // - Compute the new TUSER buffer
    assign tuser_buffer_after_write = tuser_in ? tuser_in : tuser_buffer;

    // - Compute the new TLAST buffer
    assign tlast_buffer_after_write = tlast_in;

    // - Compute the new indicies
    wire [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_end_index_after_write;
    wire [TDATA_BUFFER_WIDTH_BITS - 1:0] tdata_buffer_end_index_after_write = tkeep_buffer_end_index_after_write * 8;
    modular_add #( .INT_WIDTH(TKEEP_BUFFER_WIDTH_BITS), .MOD(TKEEP_BUFFER_WIDTH) ) compute_tkeep_buffer_end_index_after_write
    (
        .in_a(tkeep_buffer_end_index),
        .in_b({{(TKEEP_BUFFER_WIDTH_BITS - IN_TKEEP_SIZE_BITS){1'b0}}, input_tkeep_size}),
        .result(tkeep_buffer_end_index_after_write)
    );

    // READ FROM BUFFER
    // - Indicate when to read from the buffer
    wire should_read_from_buffer = can_read & read;

    // - Create registers and wires for the buffer masks and output
    wire [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_after_read;
    reg  [TKEEP_BUFFER_WIDTH - 1:0] tkeep_buffer_after_read;
    wire                            tlast_buffer_after_read;

    // - Compute TDATA output
    wire [OUT_TDATA_WIDTH - 1:0] tdata_out_front = tdata_buffer >> tdata_buffer_start_index;
    wire [OUT_TDATA_WIDTH - 1:0] tdata_out_back  = tdata_buffer << (TDATA_BUFFER_WIDTH - tdata_buffer_start_index);

    assign tdata_out_after_read = tdata_out_front | tdata_out_back;

    // - Compute TKEEP output
    wire [TKEEP_BUFFER_WIDTH - 1:0] tkeep_out_front = tkeep_buffer >> tkeep_buffer_start_index;
    wire [TKEEP_BUFFER_WIDTH - 1:0] tkeep_out_back  = tkeep_buffer << (TKEEP_BUFFER_WIDTH - tkeep_buffer_start_index);

    assign tkeep_out_after_read = tkeep_out_front | tkeep_out_back;

    // - Compute TUSER output
    assign tuser_out_after_read = tuser_buffer;

    // - Compute TLAST output
    //assign tlast_out_after_read = tlast_buffer & (!tkeep_buffer_after_read);
    assign tlast_out_after_read = tlast_buffer & (tkeep_buffer_size <= OUT_TKEEP_WIDTH);

    // - Output tracking logic
    wire [OUT_TKEEP_SIZE_BITS - 1:0] tkeep_out_size_after_read;
    find_last_bit #(.INPUT_SIZE(OUT_TKEEP_WIDTH)) find_output_tkeep_size
    (
        .in(tkeep_out_after_read),
        .bit_count(tkeep_out_size_after_read)
    );

    // - Compute the new TKEEP buffer
    always @(*) begin
        if (TKEEP_BUFFER_WIDTH >= (tkeep_buffer_start_index + tkeep_out_size_after_read)) begin
            if (tkeep_buffer_start_index > tkeep_buffer_end_index) begin
                //tkeep_buffer_after_read = ((1 << tkeep_buffer_end_index) - 1) | ((1 << (TKEEP_BUFFER_WIDTH - (tkeep_buffer_start_index + tkeep_out_size_after_read))) - 1);
                tkeep_buffer_after_read = ((1 << tkeep_buffer_end_index) - 1) | ~((1 << (tkeep_buffer_start_index + tkeep_out_size_after_read)) - 1);
            end else begin
                tkeep_buffer_after_read = ~((1 << (tkeep_buffer_start_index + tkeep_out_size_after_read)) - 1) & ((1 << tkeep_buffer_end_index) - 1);
            end
        end else begin
            tkeep_buffer_after_read = ~((1 << ((tkeep_buffer_start_index + tkeep_out_size_after_read) - TKEEP_BUFFER_WIDTH)) - 1) & ((1 << tkeep_buffer_end_index) - 1);
        end
    end

    // - Compute the new TDATA buffer
    wire [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_mask_after_read;
    byte_to_bit_mask #(.BYTE_MASK_WIDTH(TKEEP_BUFFER_WIDTH)) tdata_buffer_mask_after_write_computer
    (
        .byte_mask(tkeep_buffer_after_read),
        .bit_mask(tdata_buffer_mask_after_read)
    );

    assign tdata_buffer_after_read = tdata_buffer & tdata_buffer_mask_after_read;

    // - Compute the new TLAST buffer
    assign tlast_buffer_after_read = tlast_buffer & ~tlast_out_after_read;

    // - Compute the new indicies
    wire [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_start_index_after_read;
    wire [TDATA_BUFFER_WIDTH_BITS - 1:0] tdata_buffer_start_index_after_read = tkeep_buffer_start_index_after_read * 8;
    modular_add #( .INT_WIDTH(TKEEP_BUFFER_WIDTH_BITS), .MOD(TKEEP_BUFFER_WIDTH) ) compute_tkeep_buffer_start_index_after_read
    (
        .in_a(tkeep_buffer_start_index),
        .in_b({{(TKEEP_BUFFER_WIDTH_BITS - OUT_TKEEP_SIZE_BITS){1'b0}}, tkeep_out_size_after_read}),
        .result(tkeep_buffer_start_index_after_read)
    );

    // APPLY READ AND WRITE
    reg [OUT_TDATA_WIDTH - 1:0] tdata_out_next;
    reg [OUT_TKEEP_WIDTH - 1:0] tkeep_out_next;
    reg [TUSER_WIDTH - 1:0]     tuser_out_next;
    reg                         tlast_out_next;

    reg [TDATA_BUFFER_WIDTH - 1:0] tdata_buffer_next;
    reg [TKEEP_BUFFER_WIDTH - 1:0] tkeep_buffer_next;
    reg [TUSER_BUFFER_WIDTH - 1:0] tuser_buffer_next;
    reg                            tlast_buffer_next;

    reg [TKEEP_BUFFER_SIZE_BITS - 1:0] tkeep_buffer_size_next;

    reg [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_start_index_next;
    reg [TKEEP_BUFFER_WIDTH_BITS - 1:0] tkeep_buffer_end_index_next;

    always @(*) begin
        tdata_buffer_next = tdata_buffer;
        tkeep_buffer_next = tkeep_buffer;
        tuser_buffer_next = tuser_buffer;
        tlast_buffer_next = tlast_buffer;

        tdata_out_next = tdata_out;
        tkeep_out_next = tkeep_out;
        tuser_out_next = tuser_out;
        tlast_out_next = tlast_out;

        tkeep_buffer_size_next = tkeep_buffer_size;

        tkeep_buffer_start_index_next = tkeep_buffer_start_index;
        tkeep_buffer_end_index_next   = tkeep_buffer_end_index;

        if (should_write_to_buffer & should_read_from_buffer) begin
            tdata_buffer_next = tdata_buffer_after_read | tdata_buffer_mask_after_write;
            tkeep_buffer_next = tkeep_buffer_after_read | tkeep_buffer_mask_after_write;
            tuser_buffer_next = tuser_buffer_after_write;
            tlast_buffer_next = tlast_buffer_after_write;

            tdata_out_next = tdata_out_after_read;
            tkeep_out_next = tkeep_out_after_read;
            tuser_out_next = tuser_out_after_read;
            tlast_out_next = tlast_out_after_read;

            tkeep_buffer_size_next = tkeep_buffer_size + input_tkeep_size - tkeep_out_size_after_read;

            tkeep_buffer_start_index_next = tkeep_buffer_start_index_after_read;
            tkeep_buffer_end_index_next   = tkeep_buffer_end_index_after_write;
        end else if (should_write_to_buffer & ~should_read_from_buffer) begin
            tdata_buffer_next = tdata_buffer | tdata_buffer_mask_after_write;
            tkeep_buffer_next = tkeep_buffer | tkeep_buffer_mask_after_write;
            tuser_buffer_next = tuser_buffer_after_write;
            tlast_buffer_next = tlast_buffer_after_write;

            tkeep_buffer_size_next = tkeep_buffer_size + input_tkeep_size;

            tkeep_buffer_start_index_next = tkeep_buffer_start_index;
            tkeep_buffer_end_index_next   = tkeep_buffer_end_index_after_write;
        end else if (~should_write_to_buffer & should_read_from_buffer) begin
            tdata_buffer_next = tdata_buffer_after_read;
            tkeep_buffer_next = tkeep_buffer_after_read;
            tuser_buffer_next = tuser_buffer; // Reads will never affect the TUSER buffer
            tlast_buffer_next = tlast_buffer_after_read;

            tdata_out_next = tdata_out_after_read;
            tkeep_out_next = tkeep_out_after_read;
            tuser_out_next = tuser_out_after_read;
            tlast_out_next = tlast_out_after_read;

            tkeep_buffer_size_next = tkeep_buffer_size - tkeep_out_size_after_read;

            tkeep_buffer_start_index_next = tkeep_buffer_start_index_after_read;
            tkeep_buffer_end_index_next   = tkeep_buffer_end_index;
        end
    end

    always @(posedge clk) begin
        if (~resetn) begin
            tdata_buffer <= 0;
            tkeep_buffer <= 0;
            tuser_buffer <= 0;
            tlast_buffer <= 0;

            tdata_out <= 0;
            tkeep_out <= 0;
            tuser_out <= 0;
            tlast_out <= 0;

            tkeep_buffer_size <= 0;

            tkeep_buffer_start_index <= 0;
            tkeep_buffer_end_index   <= 0;
        end else begin
            tdata_buffer <= tdata_buffer_next;
            tkeep_buffer <= tkeep_buffer_next;
            tuser_buffer <= tuser_buffer_next;
            tlast_buffer <= tlast_buffer_next;

            tdata_out <= tdata_out_next;
            tkeep_out <= tkeep_out_next;
            tuser_out <= tuser_out_next;
            tlast_out <= tlast_out_next;

            tkeep_buffer_size <= tkeep_buffer_size_next;

            tkeep_buffer_start_index <= tkeep_buffer_start_index_next;
            tkeep_buffer_end_index   <= tkeep_buffer_end_index_next;
        end
    end

endmodule