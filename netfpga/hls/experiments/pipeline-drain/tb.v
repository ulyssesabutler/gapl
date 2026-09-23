`timescale 1ns/1ps
// Sends 3 beats back to back (last on the third), then never sends again, with the output always
// ready. PASS iff all 3 beats come out, in order, within 500 idle cycles.
module tb;
    reg clk = 0;
    always #2 clk = ~clk;
    reg rst_n = 0;

    reg  [255:0] s_data = 0;
    reg  [31:0]  s_keep = 32'hffffffff;
    reg          s_valid = 0;
    reg          s_last = 0;
    wire         s_ready;
    wire [255:0] d_data;
    wire [31:0]  d_keep;
    wire         d_valid;
    wire         d_last;

    toy dut (
        .ap_clk(clk), .ap_rst_n(rst_n),
        .src_TDATA(s_data), .src_TKEEP(s_keep), .src_TSTRB(s_keep), .src_TVALID(s_valid),
        .src_TREADY(s_ready), .src_TLAST(s_last),
        .dst_TDATA(d_data), .dst_TKEEP(d_keep), .dst_TVALID(d_valid), .dst_TREADY(1'b1),
        .dst_TLAST(d_last)
    );

    integer sent = 0, got = 0, cyc = 0, first_in = -1, first_out = -1, errors = 0;

    always @(posedge clk) begin
        cyc <= cyc + 1;
        if (rst_n && d_valid) begin
            if (first_out < 0) first_out = cyc;
            if (d_data[255:128] != 128'hABCD0000 + got) begin
                $display("MISMATCH beat %0d: tag %h", got, d_data[255:128]);
                errors = errors + 1;
            end
            if (d_last != (got == 2)) begin
                $display("MISMATCH beat %0d: last=%b", got, d_last);
                errors = errors + 1;
            end
            got = got + 1;
        end
    end

    initial begin
        repeat (10) @(posedge clk);
        rst_n <= 1;
        repeat (5) @(posedge clk);
        while (sent < 3) begin
            s_data  <= {128'hABCD0000 + sent, 128'h0123456789abcdef0011223344556677 + sent};
            s_last  <= (sent == 2);
            s_valid <= 1;
            @(posedge clk);
            while (!s_ready) @(posedge clk);
            if (first_in < 0) first_in = cyc;
            sent = sent + 1;
        end
        s_valid <= 0;
        s_last  <= 0;
        repeat (500) @(posedge clk);
        if (got == 3 && errors == 0)
            $display("RESULT PASS: 3/3 beats drained, first-beat latency %0d cycles", first_out - first_in);
        else
            $display("RESULT FAIL: %0d/3 beats out, %0d errors", got, errors);
        $finish;
    end
endmodule
