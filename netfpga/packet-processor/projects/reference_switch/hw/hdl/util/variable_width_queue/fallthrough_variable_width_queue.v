module fallthrough_variable_width_queue
#(
    parameter TDATA_WIDTH  = 256,
    parameter TUSER_WIDTH  = 128,
    localparam TKEEP_WIDTH = TDATA_WIDTH / 8
)
(
    input  wire                     clk,
    input  wire                     resetn,

    input  wire [TDATA_WIDTH - 1:0] tdata_in,
    input  wire [TKEEP_WIDTH - 1:0] tkeep_in,
    input  wire [TUSER_WIDTH - 1:0] tuser_in,
    input  wire                     tlast_in,
    input  wire                     write,

    output reg  [TDATA_WIDTH - 1:0] tdata_out,
    output reg  [TKEEP_WIDTH - 1:0] tkeep_out,
    output reg  [TUSER_WIDTH - 1:0] tuser_out,
    output reg                      tlast_out,
    input  wire                     read,

    output wire                     can_write,
    output reg                      can_read
);

    // CREATE "MIDDLE"
    reg  [TDATA_WIDTH - 1:0] middle_tdata;
    reg  [TKEEP_WIDTH - 1:0] middle_tkeep;
    reg  [TUSER_WIDTH - 1:0] middle_tuser;
    reg                      middle_tlast;

    reg                      middle_valid;

    // CREATE THE UNDERLYING NON-FWFT QUEUE
    wire [TDATA_WIDTH - 1:0] queue_tdata;
    wire [TKEEP_WIDTH - 1:0] queue_tkeep;
    wire [TUSER_WIDTH - 1:0] queue_tuser;
    wire                     queue_tlast;

    // The "head" of the queue will change in the clock cycle *after* read_from_queue_enabled & can_read_from_queue.
    // That head will keep it's value until it's changed again. This register tracks when a new head has appeared
    // that hasn't been read.
    reg                     queue_valid;

    wire                    queue_read_enabled;
    wire                    queue_can_read;

    variable_width_queue
    #(
        .TDATA_WIDTH(TDATA_WIDTH),
        .TUSER_WIDTH(TUSER_WIDTH)
    )
    queue
    (
        .clk(clk),
        .resetn(resetn),

        .tdata_in(tdata_in),
        .tkeep_in(tkeep_in),
        .tuser_in(tuser_in),
        .tlast_in(tlast_in),
        .write(write),

        .tdata_out(queue_tdata),
        .tkeep_out(queue_tkeep),
        .tuser_out(queue_tuser),
        .tlast_out(queue_tlast),
        .read(queue_read_enabled),

        .can_write(can_write),
        .can_read(queue_can_read)
    );

    // SHOULD WE UPDATE EACH "STAGE" OF THE PIPELINE
    wire will_out_be_empty_after_read_step = read | ~can_read;

    wire should_update_out    = will_out_be_empty_after_read_step & (middle_valid | queue_valid); // The output will need new data, and there is a valid source for that data
    wire should_update_middle = queue_valid & ((middle_valid & should_update_out) | (~middle_valid & ~should_update_out)); // The head of the queue has valid data to fill, and the middle will either be emptied into the out, or the queue head will be moved to the empty middle

    assign queue_read_enabled = queue_can_read & (~middle_valid | ~can_read | ~queue_valid); // The data has room and one of the "stages" is missing data

    always @(posedge clk) begin
        if (~resetn)
            begin
                queue_valid <= 0;
                middle_valid <= 0;
                can_read <= 0;
                tdata_out <= 0;
                tkeep_out <= 0;
                tuser_out <= 0;
                tlast_out <= 0;
                middle_tdata <= 0;
                middle_tkeep <= 0;
                middle_tuser <= 0;
                middle_tlast <= 0;
            end
        else
            begin
                if (should_update_middle) begin
                    middle_tdata <= queue_tdata;
                    middle_tkeep <= queue_tkeep;
                    middle_tuser <= queue_tuser;
                    middle_tlast <= queue_tlast;
                end
             
                if (should_update_out) begin
                    tdata_out <= middle_valid ? middle_tdata : queue_tdata;
                    tkeep_out <= middle_valid ? middle_tkeep : queue_tkeep;
                    tuser_out <= middle_valid ? middle_tuser : queue_tuser;
                    tlast_out <= middle_valid ? middle_tlast : queue_tlast;
                end
                
                if (queue_read_enabled) begin
                    queue_valid <= 1;
                end else if (should_update_middle || should_update_out) begin
                    queue_valid <= 0;
                end
             
                if (should_update_middle) begin
                    middle_valid <= 1;
                end else if (should_update_out) begin
                    middle_valid <= 0;
                end
             
                if (should_update_out) begin
                    can_read <= 1;
                end else if (read) begin
                    can_read <= 0;
                end
            end 
    end

endmodule