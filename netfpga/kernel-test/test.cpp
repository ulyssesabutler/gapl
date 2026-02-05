#include "Vpacket_body_processor.h"
#include "util/options.h"
#include "util/hex.h"
#include <verilated.h>
#include <verilated_vcd_c.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <functional>
#include <iomanip>
#include <iostream>
#include <memory>
#include <random>
#include <sstream>
#include <stdexcept>
#include <vector>

using Wire256 = std::array<uint32_t, 8>;
using Wire32  = uint32_t;

static VerilatedVcdC* waveform = nullptr;

// Simple simulation time for Verilator
static vluint64_t sim_time = 0;
double sc_time_stamp() { return sim_time; }

// One clock tick: 0 -> 1 -> 0 with evals
static void tick(Vpacket_body_processor* top)
{
    top->clock = 0;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;

    top->clock = 1;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;
}

struct Transmission {
    Wire256 data{}; // 256-bit word as 8x32-bit
    Wire32  keep{}; // 32 lanes of 1 byte each
};

std::vector<Transmission> string_to_nf_stream(const std::string& hex_string)
{
    std::vector<uint8_t> bytes = string_to_hex(hex_string);

    // If you want empty input to mean "no beats", this is the cleanest outcome.
    if (bytes.empty()) return {};

    // Preserve original behavior: reverse byte order (MSB<->LSB)
    std::reverse(bytes.begin(), bytes.end());

    constexpr size_t kBytesPerBeat = 32; // 256 bits

    std::vector<Transmission> out;
    out.reserve((bytes.size() + kBytesPerBeat - 1) / kBytesPerBeat);

    for (size_t offset = 0; offset < bytes.size(); offset += kBytesPerBeat)
    {
        const size_t chunk_bytes = std::min(kBytesPerBeat, bytes.size() - offset);

        Transmission transmission{};
        transmission.data.fill(0);

        // keep: low chunk_bytes bits set
        if (chunk_bytes == 32) {
            transmission.keep = 0xFFFFFFFFu;
        } else {
            // chunk_bytes is 1..31 here, so shift is safe.
            transmission.keep = (1u << static_cast<uint32_t>(chunk_bytes)) - 1u;
        }

        // Pack bytes into 8x 32-bit words, little-endian within each word.
        // Missing bytes in the final beat remain zero due to beat.data.fill(0).
        for (size_t i = 0; i < 8; ++i)
        {
            const size_t base = offset + i * 4;

            uint32_t w = 0;
            if (base + 0 < offset + chunk_bytes) w |= static_cast<uint32_t>(bytes[base + 0]) << 0;
            if (base + 1 < offset + chunk_bytes) w |= static_cast<uint32_t>(bytes[base + 1]) << 8;
            if (base + 2 < offset + chunk_bytes) w |= static_cast<uint32_t>(bytes[base + 2]) << 16;
            if (base + 3 < offset + chunk_bytes) w |= static_cast<uint32_t>(bytes[base + 3]) << 24;

            transmission.data[i] = w;
        }

        out.push_back(transmission);
    }

    return out;
}

std::string nf_data_to_string(const Wire256& value)
{
    // Unpack into bytes (inverse of the packing in string_to_nf_data)
    std::array<uint8_t, 32> bytes{};
    for (size_t i = 0; i < value.size(); ++i) {
        const uint32_t w = value[i];
        const size_t base = i * 4;
        bytes[base + 0] = static_cast<uint8_t>((w >> 0)  & 0xFF);
        bytes[base + 1] = static_cast<uint8_t>((w >> 8)  & 0xFF);
        bytes[base + 2] = static_cast<uint8_t>((w >> 16) & 0xFF);
        bytes[base + 3] = static_cast<uint8_t>((w >> 24) & 0xFF);
    }

    // Reverse byte order to undo string_to_nf_data's reverse()
    std::reverse(bytes.begin(), bytes.end());

    // Convert to hex string
    static constexpr char kHex[] = "0123456789abcdef";
    std::string out;
    out.resize(bytes.size() * 2);

    for (size_t i = 0; i < bytes.size(); ++i) {
        const uint8_t b = bytes[i];
        out[2 * i + 0] = kHex[(b >> 4) & 0x0F];
        out[2 * i + 1] = kHex[b & 0x0F];
    }

    return out;
}

// Input interface struct
struct InputInterface {
    Wire256 data;
    Wire32  keep;
    bool    last;
};

// Output interface struct
struct OutputInterface {
    Wire256 data;
    Wire32  keep;
    bool    last;
};

// Generic packer: turns a list of hex strings into a vector-of-messages,
// where each message is a vector of beats with correct .last flags.
template <typename InterfaceT>
std::vector<std::vector<InterfaceT>> make_messages(const std::vector<std::string>& hex_strings)
{
    std::vector<std::vector<InterfaceT>> messages;
    messages.reserve(hex_strings.size());

    for (const auto& hex_string : hex_strings)
    {
        const std::vector<Transmission> beats = string_to_nf_stream(hex_string);

        std::vector<InterfaceT> msg;
        msg.reserve(beats.size());

        for (size_t i = 0; i < beats.size(); ++i)
        {
            msg.push_back(InterfaceT{
                .data = beats[i].data,
                .keep = beats[i].keep,
                .last = (i == 0)
            });
        }

        std::reverse(msg.begin(), msg.end());

        messages.push_back(std::move(msg));
    }

    return messages;
}

std::vector<std::vector<InputInterface>> make_inputs(const std::vector<std::string>& inputs_strings)
{
    return make_messages<InputInterface>(inputs_strings);
}

std::vector<std::vector<OutputInterface>> make_expected_outputs(const std::vector<std::string>& expected_outputs_strings)
{
    return make_messages<OutputInterface>(expected_outputs_strings);
}

static void default_inputs(Vpacket_body_processor* top) {
    for (int w = 0; w < 8; ++w) top->i__024data[w] = 0;
    top->i__024valid = false;
    top->i__024keep  = 0;
    top->i__024last  = false;
}

static void drive_inputs(Vpacket_body_processor* top, const InputInterface& in) {
    for (int w = 0; w < 8; ++w) top->i__024data[w] = in.data[w];
    top->i__024valid = true;
    top->i__024keep  = in.keep;
    top->i__024last  = in.last;
}

static OutputInterface capture_output(Vpacket_body_processor* top) {
    OutputInterface out;

    for (int w = 0; w < 8; ++w) out.data[w] = top->o__024data[w];
    out.keep = top->o__024keep;
    out.last = top->o__024last;

    return out;
}

std::vector<std::vector<OutputInterface>> simulate(
    Vpacket_body_processor* top,
    const std::vector<std::vector<InputInterface>>& input_packets,
    size_t max_idle_cycles_per_packet = 1000
) {
    std::vector<std::vector<OutputInterface>> output_packets;
    output_packets.reserve(input_packets.size());

    // Initialize signals
    top->reset  = 1;
    top->enable = 1;
    tick(top);

    // Finish reset
    top->reset = 0;
    tick(top);

    size_t clock_cycle = 0;

    for (size_t packet_index = 0; packet_index < input_packets.size(); ++packet_index)
    {
        const auto& packet_inputs = input_packets[packet_index];

        std::vector<OutputInterface> packet_outputs;
        packet_outputs.reserve(packet_inputs.size()); // heuristic

        size_t input_beat_index = 0;
        bool   saw_last_output  = false;
        size_t idle_cycles_left = max_idle_cycles_per_packet;

        while (!saw_last_output && idle_cycles_left > 0)
        {
            // Drive one input beat per cycle until packet is fully sent
            if (input_beat_index < packet_inputs.size()) {
                const auto& in = packet_inputs[input_beat_index];

                std::cout << "Packet " << packet_index
                          << " Input beat " << input_beat_index << ":\n"
                          << "  Clock Cycle: " << clock_cycle << '\n'
                          << "  Data:        " << nf_data_to_string(in.data) << '\n'
                          << "  Keep:        " << std::hex << in.keep << std::dec << '\n'
                          << "  Last:        " << (in.last ? "true" : "false") << std::endl;

                drive_inputs(top, in);
                ++input_beat_index;
            } else {
                // Packet fully injected: go idle until outputs are drained
                default_inputs(top);
            }

            // Tick
            tick(top);
            ++clock_cycle;

            // Capture outputs (can happen while still injecting inputs)
            if (top->o__024valid) {
                OutputInterface out = capture_output(top);

                std::cout << "Packet " << packet_index
                          << " Output beat " << packet_outputs.size() << ":\n"
                          << "  Clock Cycle: " << clock_cycle << '\n'
                          << "  Data:        " << nf_data_to_string(out.data) << '\n'
                          << "  Keep:        " << std::hex << out.keep << std::dec << '\n'
                          << "  Last:        " << (out.last ? "true" : "false") << std::endl;

                packet_outputs.push_back(out);

                if (out.last) {
                    saw_last_output = true;
                }

                // Reset idle timeout whenever we make progress on outputs
                idle_cycles_left = max_idle_cycles_per_packet;
            } else {
                // Only count down idle cycles after we've finished injecting the packet.
                // (Before that, "no output yet" is normal pipeline latency.)
                if (input_beat_index >= packet_inputs.size()) {
                    --idle_cycles_left;
                }
            }
        }

        if (!saw_last_output) {
            std::cerr << "simulate: timeout waiting for last output of packet "
                      << packet_index << " after "
                      << max_idle_cycles_per_packet << " idle cycles (clock_cycle="
                      << clock_cycle << ")\n";
            std::exit(EXIT_FAILURE);
        }

        output_packets.push_back(std::move(packet_outputs));

        default_inputs(top);
        top->reset  = 1;
        top->enable = 1;
        tick(top);

        top->reset = 0;
        tick(top);
    }

    std::cout << "Finished simulation after " << clock_cycle << " clock cycles" << std::endl;
    return output_packets;
}

bool check_simulation_success(
    const std::vector<std::vector<OutputInterface>>& expected_packets,
    const std::vector<std::vector<OutputInterface>>& output_packets
) {
    // Make a padded copy of outputs:
    // - pad with "real" beats: data=0, keep=all 1s
    // - move 'last' to the final padded beat (not the original early last)
    std::vector<std::vector<OutputInterface>> padded_outputs = output_packets;

    const size_t packets_to_pad = std::min(expected_packets.size(), padded_outputs.size());
    for (size_t p = 0; p < packets_to_pad; ++p) {
        const size_t expected_beats = expected_packets[p].size();
        auto&        outs          = padded_outputs[p];

        if (outs.empty()) {
            // Nothing produced, but expected something. We'll pad to the right.
            outs.reserve(expected_beats);
        }

        if (outs.size() < expected_beats) {
            // If the DUT already asserted last early, clear it. We'll re-assert later.
            for (auto& beat : outs) {
                beat.last = false;
            }

            // Append padding beats.
            OutputInterface pad{};
            pad.data.fill(0);
            pad.keep = 0xFFFFFFFFu;
            pad.last = false;

            const size_t original_size = outs.size();
            outs.resize(expected_beats, pad);

            // Ensure only the final beat has last=true
            if (!outs.empty()) {
                outs.back().last = true;
            }

            // (Optional) If you want padding beats to be distinguishable in logs,
            // you could print original_size..expected_beats-1 elsewhere.
        } else if (!outs.empty()) {
            // If we didn't pad, leave last as produced by the DUT.
            // (We don't "fix" last here because you only asked to move it when padding happens.)
        }
    }

    bool simulation_success = true;

    if (expected_packets.size() != padded_outputs.size()) {
        std::cerr << "Test Error: expected and outputs packet-count mismatch\n"
                  << "  Expected packets: " << expected_packets.size() << '\n'
                  << "  Actual packets:   " << padded_outputs.size() << std::endl;
        return false;
    }

    for (size_t p = 0; p < expected_packets.size(); ++p) {
        const auto& expected = expected_packets[p];
        const auto& outputs  = padded_outputs[p];

        if (expected.size() != outputs.size()) {
            std::cerr << "Test Error: packet " << p << " beat-count mismatch\n"
                      << "  Expected beats: " << expected.size() << '\n'
                      << "  Actual beats:   " << outputs.size() << std::endl;
            simulation_success = false;
            continue;
        }

        for (size_t i = 0; i < expected.size(); ++i) {
            const auto& exp = expected[i];
            const auto& out = outputs[i];

            bool data_matches = true;
            for (size_t w = 0; w < 8; ++w) {
                if (exp.data[w] != out.data[w]) {
                    data_matches = false;
                    break;
                }
            }

            const bool keep_matches = (exp.keep == out.keep);
            const bool last_matches = (exp.last == out.last);
            const bool matches = data_matches && keep_matches && last_matches;

            std::cout << "Packet " << p << " Output " << i << std::endl;

            if (!matches) {
                std::cerr << "Test Error: mismatch at packet " << p << ", output " << i << '\n'
                          << "  Expected data: " << nf_data_to_string(exp.data) << '\n'
                          << "  Actual data:   " << nf_data_to_string(out.data) << '\n'
                          << "  Expected keep: " << std::hex << exp.keep << std::dec << '\n'
                          << "  Actual keep:   " << std::hex << out.keep << std::dec << '\n'
                          << "  Expected last: " << (exp.last ? "true" : "false") << '\n'
                          << "  Actual last:   " << (out.last ? "true" : "false") << std::endl;
                simulation_success = false;
            } else {
                std::cerr << "Test Success: match at packet " << p << ", output " << i << '\n'
                          << "  Data: " << nf_data_to_string(out.data) << '\n'
                          << "  Keep: " << std::hex << out.keep << std::dec << '\n'
                          << "  Last: " << (out.last ? "true" : "false") << std::endl;
            }
        }
    }

    return simulation_success;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    options options = get_options(argc, argv);
    std::cout << "Using parameters..." << std::endl;
    print_options(options);

    Vpacket_body_processor* top = new Vpacket_body_processor();

    if (!options.waveform_path.empty()) {
        Verilated::traceEverOn(true);
        waveform = new VerilatedVcdC;
        top->trace(waveform, 99);
        waveform->open(options.waveform_path.c_str());
    }

    // Convert to internal packetized representation
    std::vector<std::vector<InputInterface>> inputs =
        make_inputs(options.inputs);

    std::vector<std::vector<OutputInterface>> expected_outputs =
        make_expected_outputs(options.expected_outputs);

    // Simulate packet-by-packet (drain until last for each packet)
    std::vector<std::vector<OutputInterface>> outputs =
        simulate(top, inputs);

    top->final();

    if (waveform) {
        waveform->close();
        delete waveform;
        waveform = nullptr;
    }

    delete top;

    const bool test_pass = check_simulation_success(expected_outputs, outputs);
    return test_pass ? 0 : 1;
}
