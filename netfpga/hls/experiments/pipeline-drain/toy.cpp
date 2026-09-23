// Pipeline-drain experiment: a 256-bit AXI-Stream kernel with enough dependent arithmetic that HLS
// must pipeline it over several cycles at the target clock. The question is whether the last beat of
// a burst comes out when no further input arrives (NetFPGA's axis_mutual_exclusion admits the next
// packet only after the current one's output tlast, so a pipeline that needs more input to drain
// deadlocks).
//
// Variant selected by one of: VARIANT_STP, VARIANT_FLP, VARIANT_FRP, VARIANT_FLUSH, VARIANT_NB.
#include <ap_axi_sdata.h>
#include <ap_int.h>
#include <hls_stream.h>

typedef ap_axiu<256, 0, 0, 0> beat_t;

static ap_uint<32> rotl(ap_uint<32> x, int s) { return (x << s) | (x >> (32 - s)); }

// 16 MD5-like rounds over the low 128 bits; the high 128 bits pass through unchanged so the testbench
// can identify which input beat each output beat came from.
static ap_uint<256> mix(ap_uint<256> d) {
    static const int S[4] = {7, 12, 17, 22};
    ap_uint<32> w[8];
    for (int i = 0; i < 8; i++) {
#pragma HLS UNROLL
        w[i] = d.range(32 * i + 31, 32 * i);
    }
    ap_uint<32> a = w[0], b = w[1], c = w[2], e = w[3];
    for (int r = 0; r < 16; r++) {
#pragma HLS UNROLL
        ap_uint<32> f = (b & c) | (~b & e);
        ap_uint<32> t = b + rotl(a + f + w[r % 8] + ap_uint<32>(0x5a827999u * (r + 1)), S[r % 4]);
        a = e; e = c; c = b; b = t;
    }
    ap_uint<256> o = d;
    o.range(31, 0) = a;
    o.range(63, 32) = b;
    o.range(95, 64) = c;
    o.range(127, 96) = e;
    return o;
}

void toy(hls::stream<beat_t> &src, hls::stream<beat_t> &dst) {
#pragma HLS INTERFACE axis port=src
#pragma HLS INTERFACE axis port=dst
#pragma HLS INTERFACE ap_ctrl_none port=return
#if defined(VARIANT_STP)
#pragma HLS PIPELINE II=1
#elif defined(VARIANT_FLP)
#pragma HLS PIPELINE II=1 style=flp
#elif defined(VARIANT_FRP)
#pragma HLS PIPELINE II=1 style=frp
#elif defined(VARIANT_FLUSH)
#pragma HLS PIPELINE II=1 enable_flush
#elif defined(VARIANT_NB)
#pragma HLS PIPELINE II=1
#else
#error "no variant selected"
#endif

#if defined(VARIANT_NB)
    // Never block on input: an empty cycle just sends an invalid bubble down the pipeline, so the
    // pipeline keeps advancing (and draining) with no input at all.
    beat_t in;
    if (src.read_nb(in)) {
        beat_t out = in;
        out.data = mix(in.data);
        dst.write(out);
    }
#else
    beat_t in = src.read();
    beat_t out = in;
    out.data = mix(in.data);
    dst.write(out);
#endif
}
