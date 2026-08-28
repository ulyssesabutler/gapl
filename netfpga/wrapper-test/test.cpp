// Wrapper-level Verilator harness.
//
// netfpga/kernel-test drives the generated `packet_body_processor` directly: it supplies `enable`
// and `reset` itself, holds `enable` high for the whole run, and pulses `reset` between packets.
// That tests the kernel in isolation, but it cannot test the kernel *as the wrapper actually
// drives it* - `processor_controller` derives `enable` from input availability and downstream
// readiness, and `axis_mutual_exclusion` decides when the kernel is reset. Those are precisely the
// signals a retiming bug can desynchronise, and no amount of driving them independently at the
// kernel boundary reproduces the combination the wrapper produces.
//
// So this harness instantiates `gapl_wrapper` - the real thing, with processor_controller, both
// axis_queues, axis_mutual_exclusion, axis_pad_output and the byte reversers - and speaks only
// AXI-Stream to it. It never touches `enable` or `gapl_reset`; the wrapper owns those, exactly as
// on hardware. It is the fast counterpart to `:netfpga:runSimulation`, without Vivado.
//
// It reads the same test.properties vectors as kernel-test (-i/-o/-w), so a design can be run
// through both and the results compared directly.

#include "Vgapl_wrapper.h"
#include "util/options.h"
#include "util/hex.h"
#include <verilated.h>
#include <verilated_vcd_c.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <iostream>
#include <string>
#include <vector>

using Wire256 = std::array<uint32_t, 8>;
using Wire32  = uint32_t;

static VerilatedVcdC* waveform = nullptr;
static vluint64_t sim_time = 0;
double sc_time_stamp() { return sim_time; }

static void tick(Vgapl_wrapper* top)
{
    top->axis_aclk = 1;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;

    top->axis_aclk = 0;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;
}

// Beat packing. This deliberately does NOT match kernel-test/test.cpp byte-for-byte, because the
// two harnesses sit on opposite sides of the wrapper's byte reversers.
//
// kernel-test drives `packet_body_processor` directly, so it pre-reverses each message to land the
// first hex byte at the wire's most-significant end - the order GAPL sees. gapl_wrapper does that
// reversal in hardware (`reverse_bytes gapl_order_in`/`gapl_order_out`), so this harness must
// present bytes in natural AXI order (first hex byte in tdata[7:0]) and let the wrapper reverse
// them. Reversing here as well would double-reverse: the symptom is byte-mirrored output, plus
// genuinely wrong values for any input that is not palindromic.
//
// One consequence worth knowing: for a message whose length is not a multiple of 32 bytes,
// kernel-test transmits the short beat FIRST, whereas this harness transmits it LAST with tkeep
// marking the valid lanes - which is what a real AXI-Stream packet does, and what the wrapper is
// built to receive. For 32-byte-multiple vectors (all current test.properties files) the two agree
// exactly.
struct Transmission {
    Wire256 data{};
    Wire32  keep{};
};

static std::vector<Transmission> string_to_nf_stream(const std::string& hex_string)
{
    std::vector<uint8_t> bytes = string_to_hex(hex_string);
    if (bytes.empty()) return {};

    constexpr size_t kBytesPerBeat = 32;

    std::vector<Transmission> out;
    out.reserve((bytes.size() + kBytesPerBeat - 1) / kBytesPerBeat);

    for (size_t offset = 0; offset < bytes.size(); offset += kBytesPerBeat)
    {
        const size_t chunk_bytes = std::min(kBytesPerBeat, bytes.size() - offset);

        Transmission transmission{};
        transmission.data.fill(0);

        transmission.keep = (chunk_bytes == 32)
            ? 0xFFFFFFFFu
            : (1u << static_cast<uint32_t>(chunk_bytes)) - 1u;

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

static std::string nf_data_to_string(const Wire256& value)
{
    std::array<uint8_t, 32> bytes{};
    for (size_t i = 0; i < value.size(); ++i) {
        const uint32_t w = value[i];
        const size_t base = i * 4;
        bytes[base + 0] = static_cast<uint8_t>((w >> 0)  & 0xFF);
        bytes[base + 1] = static_cast<uint8_t>((w >> 8)  & 0xFF);
        bytes[base + 2] = static_cast<uint8_t>((w >> 16) & 0xFF);
        bytes[base + 3] = static_cast<uint8_t>((w >> 24) & 0xFF);
    }

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

struct Beat {
    Wire256 data{};
    Wire32  keep{};
    bool    last{};
};

// Beats go out in message order and the final beat carries `last` - see the byte-order note above
// for why this differs from kernel-test's short-beat-first rule.
static std::vector<std::vector<Beat>> make_messages(const std::vector<std::string>& hex_strings)
{
    std::vector<std::vector<Beat>> messages;
    messages.reserve(hex_strings.size());

    for (const auto& hex_string : hex_strings)
    {
        const std::vector<Transmission> beats = string_to_nf_stream(hex_string);

        std::vector<Beat> msg;
        msg.reserve(beats.size());
        for (size_t i = 0; i < beats.size(); ++i) {
            msg.push_back(Beat{beats[i].data, beats[i].keep, (i + 1 == beats.size())});
        }

        messages.push_back(std::move(msg));
    }

    return messages;
}

static void drive_idle(Vgapl_wrapper* top)
{
    for (int w = 0; w < 8; ++w) top->packet_body_in_axis_tdata[w] = 0;
    top->packet_body_in_axis_tkeep  = 0;
    top->packet_body_in_axis_tvalid = 0;
    top->packet_body_in_axis_tlast  = 0;
}

static void drive_beat(Vgapl_wrapper* top, const Beat& b)
{
    for (int w = 0; w < 8; ++w) top->packet_body_in_axis_tdata[w] = b.data[w];
    top->packet_body_in_axis_tkeep  = b.keep;
    top->packet_body_in_axis_tvalid = 1;
    top->packet_body_in_axis_tlast  = b.last ? 1 : 0;
}

int main(int argc, char** argv)
{
    Verilated::commandArgs(argc, argv);

    const options opts = get_options(argc, argv);
    print_options(opts);

    const std::vector<std::vector<Beat>> input_packets    = make_messages(opts.inputs);
    const std::vector<std::vector<Beat>> expected_packets = make_messages(opts.expected_outputs);

    if (input_packets.size() != expected_packets.size()) {
        std::cerr << "wrapper-test: " << input_packets.size() << " input packet(s) but "
                  << expected_packets.size() << " expected-output packet(s)\n";
        return EXIT_FAILURE;
    }

    Verilated::traceEverOn(true);
    auto* top = new Vgapl_wrapper;

    if (!opts.waveform_path.empty()) {
        waveform = new VerilatedVcdC;
        top->trace(waveform, 99);
        waveform->open(opts.waveform_path.c_str());
        std::cout << "  Waveform Path:           " << opts.waveform_path << std::endl;
    }

    // Reset. Note this is the ONLY reset this harness applies - it is not repeated between
    // packets. axis_mutual_exclusion issues the kernel's own per-packet reset internally, which is
    // the behaviour under test.
    top->axis_aclk    = 0;
    top->axis_resetn  = 0;
    drive_idle(top);
    top->packet_body_out_axis_tready = 1;
    for (int i = 0; i < 8; ++i) tick(top);
    top->axis_resetn = 1;
    tick(top);

    // Deep pipelines need a generous budget: a retimed design can be hundreds of stages, and the
    // wrapper only advances the kernel on enabled cycles.
    const size_t max_idle_cycles_per_packet = 20000;

    bool failed = false;

    for (size_t packet_index = 0; packet_index < input_packets.size(); ++packet_index)
    {
        const std::vector<Beat>& beats = input_packets[packet_index];

        std::vector<Beat> captured;
        size_t beat_index = 0;
        bool   saw_last   = false;
        size_t idle_left  = max_idle_cycles_per_packet;

        while (!saw_last && idle_left > 0)
        {
            if (beat_index < beats.size()) drive_beat(top, beats[beat_index]);
            else                           drive_idle(top);

            top->packet_body_out_axis_tready = 1;

            // Settle combinational outputs (tready / out tvalid) for THIS cycle before the edge.
            top->eval();

            const bool in_fire  = top->packet_body_in_axis_tvalid && top->packet_body_in_axis_tready;
            const bool out_fire = top->packet_body_out_axis_tvalid && top->packet_body_out_axis_tready;

            Beat out{};
            if (out_fire) {
                for (int w = 0; w < 8; ++w) out.data[w] = top->packet_body_out_axis_tdata[w];
                out.keep = top->packet_body_out_axis_tkeep;
                out.last = top->packet_body_out_axis_tlast != 0;
            }

            tick(top);

            if (in_fire)  ++beat_index;
            if (out_fire) {
                std::cout << "Packet " << packet_index << " Output beat " << captured.size() << ":\n"
                          << "  Data: " << nf_data_to_string(out.data) << '\n'
                          << "  Keep: " << std::hex << out.keep << std::dec << '\n'
                          << "  Last: " << (out.last ? "true" : "false") << std::endl;
                captured.push_back(out);
                if (out.last) saw_last = true;
                idle_left = max_idle_cycles_per_packet;
            } else if (beat_index >= beats.size()) {
                --idle_left;
            }
        }

        if (!saw_last) {
            std::cerr << "wrapper-test: timeout waiting for last output beat of packet "
                      << packet_index << " after " << max_idle_cycles_per_packet
                      << " idle cycles\n";
            failed = true;
            break;
        }

        const std::vector<Beat>& expected = expected_packets[packet_index];
        if (captured.size() != expected.size()) {
            std::cerr << "Test Failure: packet " << packet_index << " produced " << captured.size()
                      << " output beat(s), expected " << expected.size() << '\n';
            failed = true;
            continue;
        }

        for (size_t i = 0; i < captured.size(); ++i) {
            const std::string got  = nf_data_to_string(captured[i].data);
            const std::string want = nf_data_to_string(expected[i].data);
            if (got != want || captured[i].last != expected[i].last) {
                std::cerr << "Test Failure: packet " << packet_index << ", output beat " << i << '\n'
                          << "  Expected: " << want << " (last=" << expected[i].last << ")\n"
                          << "  Actual:   " << got  << " (last=" << captured[i].last << ")\n";
                failed = true;
            } else {
                std::cout << "Test Success: match at packet " << packet_index
                          << ", output " << i << std::endl;
            }
        }
    }

    if (waveform) { waveform->close(); delete waveform; waveform = nullptr; }
    top->final();
    delete top;

    if (failed) {
        std::cout << "wrapper-test: FAILED" << std::endl;
        return EXIT_FAILURE;
    }
    std::cout << "wrapper-test: all packets matched" << std::endl;
    return EXIT_SUCCESS;
}
