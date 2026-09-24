`timescale 1ns/1ps
// RTL-level flow-control check for any HLS NetFPGA kernel (module packet_body_processor, axis ports
// i_* / o_*), complementing kernel_tb.cpp, which checks data but can't observe draining. Assumes
// one output beat per input beat (true of every current application), checking beat counts and
// last flags only:
//
//   1. Drain: send a 3-beat packet, then no more input. All 3 beats must come out - a pipeline
//      that needs further input to release them deadlocks behind axis_mutual_exclusion (see
//      netfpga/hls/experiments/pipeline-drain/README.md).
//   2. Stress: 200 more beats in 1-4 beat packets with random input gaps and random output
//      backpressure; every beat must come out exactly once with the right last flag.
module drain_tb;
    reg clk = 0;
    always #5 clk = ~clk;
    reg rst_n = 0;

    localparam N = 203; // 3 drain beats + 200 stress beats

    reg  [255:0] i_data = 0;
    reg          i_valid = 0;
    reg          i_last = 0;
    wire         i_ready;
    wire [255:0] o_data;
    wire [31:0]  o_keep;
    wire [31:0]  o_strb;
    wire         o_valid;
    wire         o_last;
    reg          o_ready = 1;

    packet_body_processor dut (
        .ap_clk(clk), .ap_rst_n(rst_n),
        .i_TDATA(i_data), .i_TKEEP(32'hffffffff), .i_TSTRB(32'hffffffff), .i_TVALID(i_valid),
        .i_TREADY(i_ready), .i_TLAST(i_last),
        .o_TDATA(o_data), .o_TKEEP(o_keep), .o_TSTRB(o_strb), .o_TVALID(o_valid),
        .o_TREADY(o_ready), .o_TLAST(o_last)
    );

    reg last_of [0:N-1];
    integer n, left;
    initial begin
        last_of[0] = 0; last_of[1] = 0; last_of[2] = 1;
        left = 0;
        for (n = 3; n < N; n = n + 1) begin
            if (left == 0) left = 1 + ((n * 7 + 3) % 4);
            left = left - 1;
            last_of[n] = (left == 0) || (n == N - 1);
        end
    end

    integer sent = 0, got = 0, errors = 0, seed = 12345, stress = 0;

    always @(posedge clk) begin
        if (stress) o_ready <= ($random(seed) % 10) >= 4;
        if (rst_n && o_valid && o_ready) begin
            if (o_last !== last_of[got]) begin
                if (errors < 5) $display("MISMATCH at beat %0d: last=%b", got, o_last);
                errors = errors + 1;
            end
            got = got + 1;
        end
    end

    task send_beat;
        begin
            i_data  <= {8{sent[31:0]}};
            i_last  <= last_of[sent];
            i_valid <= 1;
            @(posedge clk);
            while (!i_ready) @(posedge clk);
            sent = sent + 1;
        end
    endtask

    initial begin
        repeat (10) @(posedge clk);
        rst_n <= 1;
        repeat (5) @(posedge clk);

        while (sent < 3) send_beat;
        i_valid <= 0;
        repeat (500) @(posedge clk);
        if (got != 3) begin
            $display("RESULT FAIL: drain - %0d/3 beats came out after input stopped", got);
            $finish;
        end
        $display("drain: 3/3 beats came out with no further input");

        stress = 1;
        while (sent < N) begin
            if (($random(seed) % 10) < 3) begin
                i_valid <= 0;
                @(posedge clk);
            end else send_beat;
        end
        i_valid <= 0;
        repeat (2000) @(posedge clk);
        if (got == N && errors == 0)
            $display("RESULT PASS: drain ok, and %0d/%0d beats under random gaps + backpressure", got, N);
        else
            $display("RESULT FAIL: %0d/%0d beats out, %0d errors", got, N, errors);
        $finish;
    end
endmodule
