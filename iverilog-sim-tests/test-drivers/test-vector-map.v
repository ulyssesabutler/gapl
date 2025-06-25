module test;
  wire [19:0] in;
  wire [19:0] out;

  assign in[0*4 +: 4] = 4'd1;
  assign in[1*4 +: 4] = 4'd2;
  assign in[2*4 +: 4] = 4'd3;
  assign in[3*4 +: 4] = 4'd4;
  assign in[4*4 +: 4] = 4'd5;

  vector_map_main dut
  (
    .i_output(in),
    .o_input(out)
  );

  initial
    $monitor("in  %h\nout %h", in, out);
    //  $monitor("in  {%d, %d, %d, %d, %d }\nout {%d, %d, %d, %d, %d }",
    //     in[0*4 +: 4],
    //     in[1*4 +: 4],
    //     in[2*4 +: 4],
    //     in[3*4 +: 4],
    //     in[4*4 +: 4],
    //     out[0*4 +: 4],
    //     out[1*4 +: 4],
    //     out[2*4 +: 4],
    //     out[3*4 +: 4],
    //     out[4*4 +: 4]
    //  );
endmodule