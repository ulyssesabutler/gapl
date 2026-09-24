// Reads an application's test.properties (testInputs / testExpectedOutputs) for the HLS testbench,
// splitting each packet's hex string into beats exactly the way sim-kernel-test's hexToBeats and
// kernel-test's string_to_nf_stream do - see netfpga/ADDING_APPLICATIONS.md for the format:
//   - 64 hex chars per beat, read left to right; if the length isn't a multiple of 64, the short
//     leftover chunk is the FIRST beat, not the last,
//   - a chunk's value is the beat's data as one integer (first byte = data[255:248]),
//   - keep is all ones for a full beat, else the low (chunk bytes) bits,
//   - the final beat of a packet is the one with last set.
#ifndef TEST_PROPERTIES_H
#define TEST_PROPERTIES_H

#include <fstream>
#include <map>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include "netfpga_axis.h"

typedef std::vector<nf_beat> nf_packet;

// Minimal java.util.Properties reader: key=value lines, '#'/'!' comments, and a trailing backslash
// continuing a value onto the next line (whose leading whitespace is dropped) - all test.properties
// files use.
inline std::map<std::string, std::string> read_properties(const std::string &path) {
    std::ifstream in(path.c_str());
    if (!in) throw std::runtime_error("cannot open " + path);
    std::map<std::string, std::string> props;
    std::string line, logical;
    bool continuing = false;
    while (std::getline(in, line)) {
        if (!line.empty() && line[line.size() - 1] == '\r') line.erase(line.size() - 1);
        size_t start = line.find_first_not_of(" \t");
        line = start == std::string::npos ? "" : line.substr(start);
        if (!continuing) {
            if (line.empty() || line[0] == '#' || line[0] == '!') continue;
            logical.clear();
        }
        continuing = !line.empty() && line[line.size() - 1] == '\\';
        logical += continuing ? line.substr(0, line.size() - 1) : line;
        if (continuing) continue;
        size_t eq = logical.find('=');
        if (eq == std::string::npos) continue;
        std::string key = logical.substr(0, eq);
        key.erase(key.find_last_not_of(" \t") + 1);
        std::string value = logical.substr(eq + 1);
        size_t vstart = value.find_first_not_of(" \t");
        props[key] = vstart == std::string::npos ? "" : value.substr(vstart);
    }
    return props;
}

inline std::vector<std::string> split_commas(const std::string &s) {
    std::vector<std::string> parts;
    std::stringstream ss(s);
    std::string part;
    while (std::getline(ss, part, ',')) {
        size_t a = part.find_first_not_of(" \t"), b = part.find_last_not_of(" \t");
        parts.push_back(a == std::string::npos ? "" : part.substr(a, b - a + 1));
    }
    return parts;
}

inline nf_packet hex_to_beats(const std::string &hex) {
    const size_t beat_hex = NF_BEAT_BYTES * 2;
    std::vector<std::string> chunks;
    size_t pos = hex.size() % beat_hex;
    if (pos != 0) chunks.push_back(hex.substr(0, pos));
    for (; pos < hex.size(); pos += beat_hex) chunks.push_back(hex.substr(pos, beat_hex));

    nf_packet beats;
    for (size_t n = 0; n < chunks.size(); n++) {
        nf_beat beat;
        beat.data = ap_uint<NF_BEAT_BYTES * 8>(chunks[n].c_str(), 16);
        size_t bytes = chunks[n].size() / 2;
        ap_uint<NF_BEAT_BYTES> keep = 0;
        for (size_t b = 0; b < bytes; b++) keep[b] = 1;
        beat.keep = keep;
        beat.strb = keep;
        beat.last = (n == chunks.size() - 1);
        beats.push_back(beat);
    }
    return beats;
}

inline std::vector<nf_packet> read_packets(const std::map<std::string, std::string> &props,
                                           const std::string &key) {
    std::map<std::string, std::string>::const_iterator it = props.find(key);
    if (it == props.end()) throw std::runtime_error("test.properties has no " + key);
    std::vector<nf_packet> packets;
    std::vector<std::string> hexes = split_commas(it->second);
    for (size_t n = 0; n < hexes.size(); n++) packets.push_back(hex_to_beats(hexes[n]));
    return packets;
}

#endif
