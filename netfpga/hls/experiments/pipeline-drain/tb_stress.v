`timescale 1ns/1ps
// Flow-control stress: 200 beats in packets of 1-4 beats, with random input gaps and random output
// backpressure (tready low ~40% of cycles), then input stops entirely. PASS iff every beat comes out
// exactly once, in order, with the right tlast, and nothing is left inside the pipeline.
module tb;
    reg clk = 0;
    always #2 clk = ~clk;
    reg rst_n = 0;

    localparam N = 200;

    reg  [255:0] s_data = 0;
    reg  [31:0]  s_keep = 32'hffffffff;
    reg          s_valid = 0;
    reg          s_last = 0;
    wire         s_ready;
    wire [255:0] d_data;
    wire [31:0]  d_keep;
    wire         d_valid;
    wire         d_last;
    reg          d_ready = 0;

    toy dut (
        .ap_clk(clk), .ap_rst_n(rst_n),
        .src_TDATA(s_data), .src_TKEEP(s_keep), .src_TSTRB(s_keep), .src_TVALID(s_valid),
        .src_TREADY(s_ready), .src_TLAST(s_last),
        .dst_TDATA(d_data), .dst_TKEEP(d_keep), .dst_TVALID(d_valid), .dst_TREADY(d_ready),
        .dst_TLAST(d_last)
    );

    // last-ness of beat i: packets of length 1..4 from a fixed pseudo-random sequence
    reg last_of [0:N-1];
    integer i, plen, left;
    initial begin
        left = 0;
        for (i = 0; i < N; i = i + 1) begin
            if (left == 0) left = 1 + ((i * 7 + 3) % 4);
            left = left - 1;
            last_of[i] = (left == 0) || (i == N - 1);
        end
    end

    integer sent = 0, got = 0, errors = 0, seed = 12345;

    always @(posedge clk) begin
        d_ready <= ($random(seed) % 10) >= 4;
        if (rst_n && d_valid && d_ready) begin
            if (d_data[255:128] != 128'hABCD0000 + got || d_last != last_of[got]) begin
                if (errors < 5) $display("MISMATCH at beat %0d: tag %h last %b", got, d_data[255:128], d_last);
                errors = errors + 1;
            end
            got = got + 1;
        end
    end

    initial begin
        repeat (10) @(posedge clk);
        rst_n <= 1;
        repeat (5) @(posedge clk);
        while (sent < N) begin
            if (($random(seed) % 10) < 3) begin
                s_valid <= 0;
                @(posedge clk);
            end else begin
                s_data  <= {128'hABCD0000 + sent, 128'h0123456789abcdef0011223344556677 + sent};
                s_last  <= last_of[sent];
                s_valid <= 1;
                @(posedge clk);
                while (!s_ready) @(posedge clk);
                sent = sent + 1;
            end
        end
        s_valid <= 0;
        repeat (1000) @(posedge clk);
        if (got == N && errors == 0)
            $display("RESULT PASS: %0d/%0d beats, in order, under random gaps + backpressure", got, N);
        else
            $display("RESULT FAIL: %0d/%0d beats out, %0d errors", got, N, errors);
        $finish;
    end
endmodule
