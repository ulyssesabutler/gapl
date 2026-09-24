// Shared types for NetFPGA HLS kernels.
//
// Every HLS application defines, in its own hls-processor.h/.cpp, a top-level function with exactly
// this signature (the HLS counterpart of GAPL's `packet_body_processor` contract - see
// netfpga/ADDING_APPLICATIONS.md):
//
//     void packet_body_processor(hls::stream<nf_beat> &i, hls::stream<nf_beat> &o);
//
// synthesized with axis ports and ap_ctrl_none, so the generated RTL module is also named
// packet_body_processor, with i_TDATA/i_TKEEP/i_TSTRB/i_TVALID/i_TREADY/i_TLAST and o_* ports.
//
// Beats are in the same byte order GAPL's packet_body_processor sees: hls_wrapper.v keeps
// gapl_wrapper.v's reverse_bytes, so test.properties means the same thing to both implementations.
// Concretely, a beat's hex chunk in test.properties is data's value as one 256-bit integer: the
// chunk's first byte is data[255:248], its last byte data[7:0].
//
// Pipelined kernels must read input with read_nb() and only write output for a successful read,
// never with a blocking read(). A blocking read in an HLS stall pipeline stops the whole pipeline
// when input runs out, stranding the last packet's beats inside it - which deadlocks behind
// axis_mutual_exclusion, since that only admits the next packet after the current one's output
// tlast. See netfpga/hls/experiments/pipeline-drain/README.md.
#ifndef NETFPGA_AXIS_H
#define NETFPGA_AXIS_H

#include <ap_axi_sdata.h>
#include <ap_int.h>
#include <hls_stream.h>

static const int NF_BEAT_BYTES = 32;

typedef ap_axiu<NF_BEAT_BYTES * 8, 0, 0, 0> nf_beat;

#endif
