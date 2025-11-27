#include "Vtest.h"
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

using Wide128 = std::array<uint32_t, 4>;

static VerilatedVcdC* waveform = nullptr;

// Simple simulation time for Verilator
static vluint64_t sim_time = 0;
double sc_time_stamp() { return sim_time; }

// One clock tick: 0 -> 1 -> 0 with evals
static void tick(Vtest* top) {
    top->clock = 0;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;

    top->clock = 1;
    top->eval();
    if (waveform) waveform->dump(sim_time);
    ++sim_time;
}

Wide128 hexToWide128(std::string hex) {
    // Brought to you by the bot
    if (hex.size() >= 2 && hex[0] == '0' &&
        (hex[1] == 'x' || hex[1] == 'X')) {
        hex = hex.substr(2);
    }

    hex.erase(std::remove_if(hex.begin(), hex.end(), ::isspace), hex.end());

    if (hex.size() > 32) throw std::runtime_error("hexToWide128: hex string longer than 128 bits");
    if (hex.size() < 32) hex = std::string(32 - hex.size(), '0') + hex;

    Wide128 result{};

    for (int w = 0; w < 4; ++w) {
        const std::size_t start = 32 - (w + 1) * 8;
        const std::string chunk = hex.substr(start, 8);
        result[w] = static_cast<uint32_t>(std::stoul(chunk, nullptr, 16));
    }

    return result;
}

std::string wide128ToHex(const Wide128& value) {
    // Brought to you by the bot
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (int w = 3; w >= 0; --w) oss << std::setw(8) << value[w];
    return oss.str();
}

struct TestVector {
    std::string i_hex;
    std::string key_hex;
    std::string expected_o_hex;
};

// Input interface struct
struct InputInterface {
    Wide128 i;
    Wide128 key;
};

// Output interface struct
struct OutputInterface {
    Wide128 o;
};

std::vector<InputInterface> makeInputs(const std::vector<TestVector>& tvs) {
    std::vector<InputInterface> inputs;
    inputs.reserve(tvs.size());

    for (const auto& tv : tvs) {
        inputs.push_back(InputInterface{
            .i   = hexToWide128(tv.i_hex),
            .key = hexToWide128(tv.key_hex)
        });
    }

    return inputs;
}

std::vector<Wide128> makeExpectedOutputs(const std::vector<TestVector>& tvs) {
    std::vector<Wide128> expected;
    expected.reserve(tvs.size());

    for (const auto& tv : tvs) {
        expected.push_back(hexToWide128(tv.expected_o_hex));
    }

    return expected;
}

static void drive_inputs(Vtest* top, const InputInterface& in) {
    for (int w = 0; w < 4; ++w) {
        top->i[w]   = in.i[w];
        top->key[w] = in.key[w];
    }
}

static OutputInterface capture_output(Vtest* top) {
    OutputInterface out;
    for (int w = 0; w < 4; ++w) {
        out.o[w] = top->o[w];
    }
    return out;
}

// Simulation function
std::vector<OutputInterface> simulate(
    Vtest* top,
    const std::vector<InputInterface>& inputs
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


    for (const auto& in : inputs) {
        drive_inputs(top, in);

        tick(top);

        outputs.push_back(capture_output(top));
    }

    return outputs;
}

// Check if simulation was successful
bool checkSimulationSuccess(
    const std::vector<Wide128>& expected,
    const std::vector<OutputInterface>& outputs
) {
    if (expected.size() != outputs.size()) {
        std::cerr << "Test Error: expected and outputs size mismatch\n";
        return false;
    }

    for (std::size_t i = 0; i < expected.size(); ++i) {
        const auto& exp = expected[i];
        const auto& out = outputs[i].o;

        if (exp != out) {
            std::cerr << "Test Error at index " << i << ":\n"
                      << "  expected: 0x" << wide128ToHex(exp) << "\n"
                      << "  got     : 0x" << wide128ToHex(out) << "\n";
            return false;
        }
    }

    return true;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    std::string waveformPath;
    if (argc > 1) waveformPath = argv[1];

    Vtest* top = new Vtest();

    if (!waveformPath.empty()) {
        Verilated::traceEverOn(true);
        waveform = new VerilatedVcdC;
        top->trace(waveform, 99);      // trace depth
        waveform->open(waveformPath.c_str());
    }

    std::vector<TestVector> testVectors = {
        {
            "000102030405060708090a0b0c0d0e0f",
            "000102030405060708090a0b0c0d0e0f",
            "0a940bb5416ef045f1c39458c653ea5a"
        },
        {
            "00112233445566778899aabbccddeeff",
            "00112233445566778899aabbccddeeff",
            "62f679be2bf0d931641e039ca3401bb2"
        },
        {
            "00112233445566778899aabbccddeeff",
            "000102030405060708090a0b0c0d0e0f",
            "69c4e0d86a7b0430d8cdb78070b4c55a"
        },
        {
            "000102030405060708090a0b0c0d0e0f",
            "00112233445566778899aabbccddeeff",
            "279fb74a7572135e8f9b8ef6d1eee003"
        },
        {
            "00000000000000000000000000000000",
            "00000000000000000000000000000000",
            "66e94bd4ef8a2c3b884cfa59ca342b2e"
        }
    };

    // 2) Convert to internal representation
    std::vector<InputInterface> inputs = makeInputs(testVectors);
    std::vector<Wide128> expectedOutputs = makeExpectedOutputs(testVectors);

    // 3) Simulate
    std::vector<OutputInterface> outputs = simulate(top, inputs);

    top->final();

    if (waveform) {
        waveform->close();
        delete waveform;
        waveform = nullptr;
    }

    delete top;

    bool testPass = checkSimulationSuccess(expectedOutputs, outputs);

    if (testPass) {
        return 0;
    } else {
        return 1;
    }
}
