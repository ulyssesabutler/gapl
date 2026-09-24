// Generic HLS testbench for any NetFPGA application's packet_body_processor: runs every packet in
// the application's test.properties through the kernel and checks every output beat's data, keep,
// and last against testExpectedOutputs - the same checks sim-kernel-test and kernel-test make, so
// one test.properties covers the GAPL and HLS implementations alike.
//
// Usage (C-sim and C/RTL cosim): argv[1] is the path to test.properties.
//
// All input beats are queued first and the kernel is then invoked once per input beat; outputs
// are regrouped into packets by last. The drain property (last beats come out with no further
// input) is checked separately at the RTL level by drain_tb.v, since C-sim can't observe it.
#include <cstdio>
#include <string>

#include "hls-processor.h"
#include "test_properties.h"

static std::string hex256(const ap_uint<NF_BEAT_BYTES * 8> &v) {
    std::string s = v.to_string(16, false);
    if (s.compare(0, 2, "0x") == 0) s = s.substr(2);
    return std::string(NF_BEAT_BYTES * 2 - s.size(), '0') + s;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        std::fprintf(stderr, "usage: kernel_tb <test.properties>\n");
        return 2;
    }
    std::map<std::string, std::string> props = read_properties(argv[1]);
    std::vector<nf_packet> inputs = read_packets(props, "testInputs");
    std::vector<nf_packet> expected = read_packets(props, "testExpectedOutputs");
    if (inputs.size() != expected.size()) {
        std::fprintf(stderr, "testInputs has %zu packets but testExpectedOutputs has %zu\n",
                     inputs.size(), expected.size());
        return 2;
    }

    hls::stream<nf_beat> in("i"), out("o");
    size_t beats_in = 0;
    for (size_t p = 0; p < inputs.size(); p++) {
        for (size_t b = 0; b < inputs[p].size(); b++) {
            in.write(inputs[p][b]);
            beats_in++;
        }
    }
    for (size_t n = 0; n < beats_in; n++) packet_body_processor(in, out);

    std::vector<nf_packet> actual(1);
    while (!out.empty()) {
        nf_beat beat = out.read();
        actual.back().push_back(beat);
        if (beat.last) actual.push_back(nf_packet());
    }
    if (actual.back().empty()) actual.pop_back();

    int errors = 0;
    if (actual.size() != expected.size()) {
        std::printf("FAIL: expected %zu output packets, got %zu\n", expected.size(), actual.size());
        errors++;
    }
    for (size_t p = 0; p < expected.size() && p < actual.size(); p++) {
        if (actual[p].size() != expected[p].size()) {
            std::printf("FAIL packet %zu: expected %zu beats, got %zu\n", p, expected[p].size(),
                        actual[p].size());
            errors++;
            continue;
        }
        for (size_t b = 0; b < expected[p].size(); b++) {
            const nf_beat &e = expected[p][b], &a = actual[p][b];
            bool ok = e.data == a.data && e.keep == a.keep && e.last == a.last;
            std::printf("%s packet %zu beat %zu\n  data expected %s\n  data actual   %s\n"
                        "  keep %s/%s last %d/%d\n",
                        ok ? "PASS" : "FAIL", p, b, hex256(e.data).c_str(), hex256(a.data).c_str(),
                        e.keep.to_string(16, false).c_str(), a.keep.to_string(16, false).c_str(),
                        (int)e.last, (int)a.last);
            if (!ok) errors++;
        }
    }
    if (errors == 0) {
        std::printf("RESULT PASS: all %zu packets match\n", expected.size());
        return 0;
    }
    std::printf("RESULT FAIL: %d errors\n", errors);
    return 1;
}
