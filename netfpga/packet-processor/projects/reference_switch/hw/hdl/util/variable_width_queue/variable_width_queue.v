// variable_width_queue
// Packs incoming AXIS-like beats (with right-aligned, contiguous tkeep)
// into as-full-as-possible output beats. Emits a partial beat only to
// flush at end-of-stream (after seeing tlast_in).
//
// Handshake:
//   - Accept input when write && can_write
//   - Produce output when read && can_read
//
// TUSER SEMANTICS (assumption):
//   - Per-stream metadata: captured from the first accepted input beat
//     of a stream and forwarded on every output beat of that stream.
//
// Author: chatgpt (have you tried turning your FIFO off and on again? :)
//
module variable_width_queue
#(
    parameter TDATA_WIDTH              = 256,
    parameter TUSER_WIDTH              = 128,
    localparam TKEEP_WIDTH             = TDATA_WIDTH / 8,

    // How many output-beats-worth of byte storage we keep internally.
    // More beats = more headroom when downstream stalls.
    parameter BUFFER_BEATS             = 4
)
(
    input  wire                     clk,
    input  wire                     resetn,

    // Input (write side)
    input  wire [TDATA_WIDTH - 1:0] tdata_in,
    input  wire [TKEEP_WIDTH - 1:0] tkeep_in,
    input  wire [TUSER_WIDTH - 1:0] tuser_in,
    input  wire                     tlast_in,
    input  wire                     write,

    // Output (read side)
    output reg  [TDATA_WIDTH - 1:0] tdata_out,
    output reg  [TKEEP_WIDTH - 1:0] tkeep_out,
    output reg  [TUSER_WIDTH - 1:0] tuser_out,
    output reg                      tlast_out,
    input  wire                     read,

    output wire                     can_write,
    output wire                     can_read
);

    // ----------------------------------------------------------------
    // Local params / widths
    // ----------------------------------------------------------------
    localparam integer BYTES          = TKEEP_WIDTH;
    localparam integer BUFFER_BYTES   = BUFFER_BEATS * BYTES;            // total bytes we can hold
    localparam integer BUF_WIDTH      = BUFFER_BYTES * 8;                // data bits
    localparam integer COUNT_W        = $clog2(BUFFER_BYTES + 1);

    // ----------------------------------------------------------------
    // Byte-accumulator buffer:
    //   - We keep acc_count bytes valid in the LSB side of buf.
    //   - On read: take the lowest BYTES (or fewer on final flush),
    //              then shift buf right by #consumed bytes.
    //   - On write: OR masked input data shifted left by (acc_count bytes).
    // ----------------------------------------------------------------
    reg [BUF_WIDTH-1:0]  buffer;
    reg [COUNT_W-1:0]    acc_count;       // # of valid bytes in buffer (0..BUFFER_BYTES)
    reg                  eos;             // seen end-of-stream (accepted a beat with tlast_in)
    reg                  tuser_latched_v; // have we latched tuser for this stream?
    reg [TUSER_WIDTH-1:0] tuser_latched;  // per-stream tuser

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    // Count the number of 1s in tkeep_in (0..BYTES)
    function [COUNT_W-1:0] count_ones;
        input [TKEEP_WIDTH-1:0] v;
        integer i;
        reg [COUNT_W-1:0] s;
    begin
        s = {COUNT_W{1'b0}};
        for (i = 0; i < TKEEP_WIDTH; i = i + 1)
            if (v[i]) s = s + 1'b1;
        count_ones = s;
    end
    endfunction

    // Expand a byte-enable mask (tkeep) to a bit mask over TDATA_WIDTH
    function [TDATA_WIDTH-1:0] data_mask_from_keep;
        input [TKEEP_WIDTH-1:0] k;
        integer i;
        reg [TDATA_WIDTH-1:0] m;
    begin
        m = {TDATA_WIDTH{1'b0}};
        for (i = 0; i < TKEEP_WIDTH; i = i + 1)
            if (k[i])
                m[(8*i) +: 8] = 8'hFF;
        data_mask_from_keep = m;
    end
    endfunction

    // Keep mask with N LSB ones (width = BYTES). Safe for N=0..BYTES.
    function [TKEEP_WIDTH-1:0] keep_mask_n;
        input [COUNT_W-1:0] n;
        reg [TKEEP_WIDTH-1:0] all1;
    begin
        all1 = {TKEEP_WIDTH{1'b1}};
        keep_mask_n = (n == 0) ? {TKEEP_WIDTH{1'b0}}
                                : (all1 >> (TKEEP_WIDTH - n));
    end
    endfunction

    // ----------------------------------------------------------------
    // Combinational handshake math
    // ----------------------------------------------------------------
    wire [COUNT_W-1:0] in_bytes  = count_ones(tkeep_in);
    wire [TDATA_WIDTH-1:0] in_masked = tdata_in & data_mask_from_keep(tkeep_in);

    wire [COUNT_W-1:0] capacity  = BUFFER_BYTES[COUNT_W-1:0] - acc_count[COUNT_W-1:0];

    // We can accept THIS input beat iff its specific byte count fits.
    assign can_write = (in_bytes <= capacity);

    // How many bytes would we output this cycle if read is asserted?
    // - If we have >= BYTES, we can output a full beat.
    // - If eos is true (we've seen last) and acc_count > 0, we can flush a partial.
    wire [COUNT_W-1:0] out_bytes_candidate =
        (acc_count >= BYTES[COUNT_W-1:0]) ? BYTES[COUNT_W-1:0] :
        (eos && (acc_count != {COUNT_W{1'b0}})) ? acc_count :
        {COUNT_W{1'b0}};

    assign can_read = (out_bytes_candidate != {COUNT_W{1'b0}});

    // Handshakes that will happen this cycle
    wire accept_in  = write && can_write && (in_bytes != {COUNT_W{1'b0}});
    wire give_out   = read  && can_read   && (out_bytes_candidate != {COUNT_W{1'b0}});

    // Next acc_count
    wire [COUNT_W:0] acc_after_read  = acc_count - (give_out ? out_bytes_candidate : {COUNT_W{1'b0}});
    wire [COUNT_W:0] acc_after_write = acc_after_read + (accept_in ? in_bytes : {COUNT_W{1'b0}});

    // Next eos (stream end pending): latch when we accept a tlast_in; clear when we drain to 0.
    wire eos_seen_this_cycle = accept_in && tlast_in;
    wire will_have_eos       = eos | eos_seen_this_cycle;
    wire eos_next            = will_have_eos && (acc_after_write != {COUNT_W+1{1'b0}});

    // tlast_out fires only when we produce data AND after this cycle the accumulator goes to zero AND we know we've seen end.
    wire tlast_out_next      = give_out && will_have_eos && (acc_after_write == {COUNT_W+1{1'b0}});

    // ----------------------------------------------------------------
    // Data-path updates
    // ----------------------------------------------------------------
    // Build next buffer:
    // 1) If giving out, drop the consumed bytes by shifting right.
    // 2) If accepting input, OR the masked input shifted to land above current bytes.
    wire [BUF_WIDTH-1:0] buf_after_read  =
        give_out ? (buffer >> (out_bytes_candidate * 8)) : buffer;

    wire [BUF_WIDTH-1:0] in_shifted      =
        accept_in ? ( {{(BUF_WIDTH-TDATA_WIDTH){1'b0}}, in_masked} << (acc_after_read * 8) )
                  : {BUF_WIDTH{1'b0}};

    wire [BUF_WIDTH-1:0] buf_next        = buf_after_read | in_shifted;

    // The output data word for this transfer is always the lowest BYTES bytes of *current* buffer.
    // For partial flush, the upper bytes in that word will be zero (by construction), and tkeep_out says how many are valid.
    wire [TDATA_WIDTH-1:0] tdata_word = buffer[TDATA_WIDTH-1:0];
    wire [TKEEP_WIDTH-1:0] tkeep_word = keep_mask_n(out_bytes_candidate);

    // ----------------------------------------------------------------
    // Sequential logic
    // ----------------------------------------------------------------
    always @(posedge clk or negedge resetn) begin
        if (!resetn) begin
            buffer           <= {BUF_WIDTH{1'b0}};
            acc_count        <= {COUNT_W{1'b0}};
            eos              <= 1'b0;
            tdata_out        <= {TDATA_WIDTH{1'b0}};
            tkeep_out        <= {TKEEP_WIDTH{1'b0}};
            tlast_out        <= 1'b0;
            tuser_out        <= {TUSER_WIDTH{1'b0}};
            tuser_latched    <= {TUSER_WIDTH{1'b0}};
            tuser_latched_v  <= 1'b0;
        end else begin
            // Latch per-stream TUSER on first accepted beat after becoming idle
            if (!tuser_latched_v && accept_in) begin
                tuser_latched   <= tuser_in;
                tuser_latched_v <= 1'b1;
            end
            // On final output (tlast_out), clear the latch flag so next stream can capture.
            if (tlast_out_next) begin
                tuser_latched_v <= 1'b0;
            end

            // Produce outputs on successful read
            if (give_out) begin
                tdata_out <= tdata_word;
                tkeep_out <= tkeep_word;
                tuser_out <= tuser_latched; // per-stream metadata
            end
            // tlast_out is only asserted on the handshake cycle that empties the buffer after EOS
            tlast_out <= tlast_out_next;

            // Update accumulator/buffer + flags
            buffer    <= buf_next;
            acc_count <= acc_after_write[COUNT_W-1:0];
            eos       <= eos_next;
        end
    end

endmodule
