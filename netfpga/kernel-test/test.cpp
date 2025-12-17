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

Wire256 string_to_nf_data(const std::string& hex_string)
{
    std::vector<uint8_t> bytes = string_to_hex(hex_string);

    constexpr size_t kExpectedBytes = 32; // 256 bits
    if (bytes.size() != kExpectedBytes)
    {
        std::cerr << "string_to_nf_data: expected " << kExpectedBytes
                  << " bytes (256 bits), got " << bytes.size()
                  << " bytes from hex string of length " << hex_string.size()
                  << "\n";
        std::exit(EXIT_FAILURE);
    }

    // Reverse byte order (MSB<->LSB)
    std::reverse(bytes.begin(), bytes.end());

    // Pack into 8x 32-bit words.
    // word[0] is the least-significant 32 bits after the byte-reversal.
    Wire256 out{};
    for (size_t i = 0; i < out.size(); ++i)
    {
        const size_t base = i * 4;
        uint32_t w = 0;
        w |= static_cast<uint32_t>(bytes[base + 0]) << 0;
        w |= static_cast<uint32_t>(bytes[base + 1]) << 8;
        w |= static_cast<uint32_t>(bytes[base + 2]) << 16;
        w |= static_cast<uint32_t>(bytes[base + 3]) << 24;
        out[i] = w;
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

std::vector<InputInterface> make_inputs(const std::vector<std::string>& inputs_strings) {
    std::vector<InputInterface> inputs;
    inputs.reserve(inputs_strings.size());

    for (const auto& input_string : inputs_strings) {
        inputs.push_back(InputInterface{
            .data = string_to_nf_data(input_string),
            .keep = 0xFFFFFFFFu,
            .last = true
        });
    }

    return inputs;
}

std::vector<OutputInterface> make_expected_outputs(const std::vector<std::string>& expected_outputs_strings) {
    std::vector<OutputInterface> expected_outputs;
    expected_outputs.reserve(expected_outputs_strings.size());

    for (const auto& expected_output_string : expected_outputs_strings) {
        expected_outputs.push_back(OutputInterface{
            .data = string_to_nf_data(expected_output_string),
            .keep = 0xFFFFFFFFu,
            .last = true
        });
    }

    return expected_outputs;
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

// Simulation function
std::vector<OutputInterface> simulate(
    Vpacket_body_processor* top,
    const std::vector<InputInterface>& inputs,
    size_t expected_output_count
) {
    std::vector<OutputInterface> outputs;
    
    // Initialize signals
    top->reset = 1;   // assert reset initially
    top->enable = 1;  // keep enabled
    tick(top);

    // Finish reset
    top->reset = 0;
    tick(top);

    OutputInterface currentOutput;

    size_t current_input_index = 0;
    size_t expected_outputs_remaining = expected_output_count;
    size_t waiting_clock_cycles_remaining = 100;
    size_t clock_cycle = 0;

    while (expected_outputs_remaining > 0 && waiting_clock_cycles_remaining > 0) {
        // Inputs
        if (current_input_index < inputs.size()) {
            std::cout << "Input " << current_input_index << ":\n"
                << "  Clock Cycle: " << clock_cycle << '\n'
                << "  Data:        " << nf_data_to_string(inputs[current_input_index].data) << std::endl;

            drive_inputs(top, inputs[current_input_index]);
            ++current_input_index;
        } else {
            default_inputs(top);
        }

        // Tick
        tick(top);
        clock_cycle++;

        // Outputs
        if (top->o__024valid) {
            std::cout << "Output " << outputs.size() << ":\n"
                << "  Clock Cycle: " << clock_cycle << '\n'
                << "  Data:        " << nf_data_to_string(capture_output(top).data) << std::endl;

            outputs.push_back(capture_output(top));
            expected_outputs_remaining--;
        } else {
            waiting_clock_cycles_remaining--;
        }
    }

    std::cout << "Finished simulation after " << clock_cycle << " clock cycles" << std::endl;

    return outputs;
}

// Check if simulation was successful
bool check_simulation_success(
    const std::vector<OutputInterface>& expected,
    const std::vector<OutputInterface>& outputs
) {
    bool simulation_success = true;

    if (expected.size() != outputs.size()) {
        std::cerr << "Test Error: expected and outputs size mismatch\n"
            << "  Expected: " << expected.size() << '\n'
            << "  Actual:   " << outputs.size() << std::endl;

        return false;
    }

    for (std::size_t i = 0; i < expected.size(); ++i) {
        std::cout << "Output " << i << std::endl;

        const auto& exp = expected[i].data;
        const auto& out = outputs[i].data;

        bool doesOutputMatchExpected = true;

        for (std::size_t w = 0; w < 8; w++)
            if (exp[w] != out[w]) doesOutputMatchExpected = false;

        if (!doesOutputMatchExpected) {
            std::cerr << "Test Error: At output " << i << '\n'
                << "  Expected: " << nf_data_to_string(exp) << '\n'
                << "  Actual:   " << nf_data_to_string(out) << std::endl;

            simulation_success = false;
        } else {
            std::cerr << "Test Success: At output " << i << '\n'
                << "  Expected: " << nf_data_to_string(exp) << '\n'
                << "  Actual:   " << nf_data_to_string(out) << std::endl;
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
        top->trace(waveform, 99);      // trace depth
        waveform->open(options.waveform_path.c_str());
    }

    // 2) Convert to internal representation
    std::vector<InputInterface> inputs = make_inputs(options.inputs);
    std::vector<OutputInterface> expected_outputs = make_expected_outputs(options.expected_outputs);

    // 3) Simulate
    std::vector<OutputInterface> outputs = simulate(top, inputs, expected_outputs.size());

    top->final();

    if (waveform) {
        waveform->close();
        delete waveform;
        waveform = nullptr;
    }

    delete top;

    bool test_pass = check_simulation_success(expected_outputs, outputs);

    if (test_pass) {
        return 0;
    } else {
        return 1;
    }
}
