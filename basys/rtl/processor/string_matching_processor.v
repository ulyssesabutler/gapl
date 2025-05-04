module string_matching_processor
#(
    parameter STRING_SIZE = 40
) (
    input  wire       clock,
    input  wire       reset,
    input  wire       enable,

    input  wire [7:0] in_data,
    input  wire       in_valid,
    input  wire       in_last,

    output wire [7:0] out_data,
    output wire       out_valid,
    output wire       out_last
);

    wire [(STRING_SIZE * 8) - 1:0] needle;

    wire [7:0] heystack_data;
    wire       heystack_valid;
    wire       heystack_last;

    needle_heystack_parser #( .STRING_SIZE(STRING_SIZE) ) parser
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .in_data(in_data),
        .in_valid(in_valid),
        .in_last(in_last),

        .needle(needle),

        .heystack_data(heystack_data),
        .heystack_valid(heystack_valid),
        .heystack_last(heystack_last)
    );

    // Output of string matcher
    wire result;
    wire valid;

    string_matching_main string_matching
    (
        .clock(clock),
        .reset(reset),
        .enable(enable),

        .needle(needle),

        .heystack(heystack_data),
        .last(heystack_last),

        .result(result),
        .valid(valid)
    );

    assign out_data = { 7'b0, result };
    assign out_valid = valid;
    assign out_last = valid;

endmodule