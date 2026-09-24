/*
 * NETFPGA PACKET BODY PROCESSOR - SINGLE-BLOCK MD5 (HLS)
 *
 * The HLS counterpart of gapl-processor.gapl, computing exactly the same function: each beat's 32
 * data bytes are hashed as a complete 32-byte message (one 512-bit block: the data, the 0x80
 * marker, zeros, and a fixed 256-bit length), and the 16-byte digest is placed in data[255:128]
 * with zeros below. keep and last pass through unchanged, one output beat per input beat, and keep
 * is not read (a short beat is still hashed as 32 bytes, as in the GAPL version).
 *
 * All 64 rounds are unrolled inside an II=1 function pipeline, so HLS's scheduler places the
 * pipeline registers - the counterpart of what GAPL's retiming pass does for md5/min-register-count.
 *
 * Byte order is GAPL's (see netfpga_axis.h): data[255:248] is the beat's first byte. So message
 * word M[k] is the byte-swapped data[255-32k : 224-32k], and the digest words are byte-swapped
 * back on the way out - the same as gapl-processor.gapl's md5_hash_block_from_input and
 * md5_hash_output_from_state.
 */
#include "hls-processor.h"

typedef ap_uint<32> word;

static word byte_swap(word x) {
    return (x.range(7, 0), x.range(15, 8), x.range(23, 16), x.range(31, 24));
}

static word rotl(word x, int s) {
    return (x << s) | (x >> (32 - s));
}

static const unsigned K[64] = {
    0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
    0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
    0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
    0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
    0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
    0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
    0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
    0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
};

static const int S[64] = {
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20, 5,  9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
};

static ap_uint<128> md5_single_block(ap_uint<256> data) {
    word m[16];
#pragma HLS ARRAY_PARTITION variable=m complete
    for (int k = 0; k < 8; k++) {
#pragma HLS UNROLL
        m[k] = byte_swap(data.range(255 - 32 * k, 224 - 32 * k));
    }
    // Padding for a 32-byte message: the 0x80 marker byte right after the data, zeros, and the
    // 64-bit little-endian bit length (256) in words 14..15.
    m[8] = 0x00000080;
    for (int k = 9; k < 14; k++) {
#pragma HLS UNROLL
        m[k] = 0;
    }
    m[14] = 256;
    m[15] = 0;

    const word a0 = 0x67452301, b0 = 0xefcdab89, c0 = 0x98badcfe, d0 = 0x10325476;
    word a = a0, b = b0, c = c0, d = d0;
    for (int r = 0; r < 64; r++) {
#pragma HLS UNROLL
        word f;
        int g;
        if (r < 16) {
            f = (b & c) | (~b & d);
            g = r;
        } else if (r < 32) {
            f = (d & b) | (~d & c);
            g = (5 * r + 1) % 16;
        } else if (r < 48) {
            f = b ^ c ^ d;
            g = (3 * r + 5) % 16;
        } else {
            f = c ^ (b | ~d);
            g = (7 * r) % 16;
        }
        word rotated = rotl(a + f + word(K[r]) + m[g], S[r]);
        a = d;
        d = c;
        c = b;
        b = b + rotated;
    }

    return (byte_swap(a0 + a), byte_swap(b0 + b), byte_swap(c0 + c), byte_swap(d0 + d));
}

void packet_body_processor(hls::stream<nf_beat> &i, hls::stream<nf_beat> &o) {
#pragma HLS INTERFACE axis port=i
#pragma HLS INTERFACE axis port=o
#pragma HLS INTERFACE ap_ctrl_none port=return
#pragma HLS PIPELINE II=1

    // read_nb, never read(): see netfpga_axis.h.
    nf_beat in;
    if (i.read_nb(in)) {
        nf_beat out;
        out.data = ap_uint<256>(md5_single_block(in.data)) << 128;
        out.keep = in.keep;
        out.strb = in.strb;
        out.last = in.last;
        o.write(out);
    }
}
