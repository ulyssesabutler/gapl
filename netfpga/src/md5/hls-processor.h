#ifndef MD5_HLS_PROCESSOR_H
#define MD5_HLS_PROCESSOR_H

#include "netfpga_axis.h"

void packet_body_processor(hls::stream<nf_beat> &i, hls::stream<nf_beat> &o);

#endif
