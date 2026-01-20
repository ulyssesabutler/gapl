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
using Wide512 = std::array<uint32_t, 16>;

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

static std::string sanitizeHex(std::string hex) {
    if (hex.size() >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X')) {
        hex = hex.substr(2);
    }
    hex.erase(std::remove_if(hex.begin(), hex.end(),
                             [](unsigned char c){ return std::isspace(c); }),
              hex.end());
    return hex;
}

static uint8_t hexNibble(char c) {
    if (c >= '0' && c <= '9') return static_cast<uint8_t>(c - '0');
    if (c >= 'a' && c <= 'f') return static_cast<uint8_t>(c - 'a' + 10);
    if (c >= 'A' && c <= 'F') return static_cast<uint8_t>(c - 'A' + 10);
    throw std::runtime_error("Invalid hex digit");
}

static std::vector<uint8_t> hexToBytes(std::string hex) {
    hex = sanitizeHex(std::move(hex));
    if (hex.size() % 2 != 0) {
        throw std::runtime_error("hexToBytes: odd number of hex digits");
    }

    std::vector<uint8_t> bytes;
    bytes.reserve(hex.size() / 2);

    for (std::size_t i = 0; i < hex.size(); i += 2) {
        uint8_t hi = hexNibble(hex[i]);
        uint8_t lo = hexNibble(hex[i + 1]);
        bytes.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }
    return bytes;
}

static std::string bytesToHex(const std::vector<uint8_t>& bytes) {
    static const char* kHex = "0123456789abcdef";
    std::string out;
    out.reserve(bytes.size() * 2);
    for (uint8_t b : bytes) {
        out.push_back(kHex[(b >> 4) & 0xF]);
        out.push_back(kHex[b & 0xF]);
    }
    return out;
}

// MD5 padding for *single-block* messages (i.e., resulting padded message is exactly 64 bytes).
static std::string md5PadToSingleBlockHex(const std::string& message_hex) {
    std::vector<uint8_t> msg = hexToBytes(message_hex);

    const std::uint64_t original_len_bytes = static_cast<std::uint64_t>(msg.size());
    const std::uint64_t original_len_bits  = original_len_bytes * 8ULL;

    // Append 0x80
    msg.push_back(0x80);

    // Pad with 0x00 until length is 56 mod 64
    while ((msg.size() % 64) != 56) {
        msg.push_back(0x00);
        // If we exceed one block before adding length, this is a multi-block padded message.
        if (msg.size() > 64) {
            throw std::runtime_error("md5PadToSingleBlockHex: message requires multiple 512-bit blocks after padding");
        }
    }

    // Append original length in bits as 64-bit little-endian
    for (int i = 0; i < 8; ++i) {
        msg.push_back(static_cast<uint8_t>((original_len_bits >> (8 * i)) & 0xFF));
    }

    if (msg.size() != 64) {
        throw std::runtime_error("md5PadToSingleBlockHex: internal error, padded block is not 64 bytes");
    }

    // Return 128 hex chars (512 bits)
    return bytesToHex(msg);
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

Wide512 hexToWide512(std::string hex) {
    // Brought to you by the bot
    if (hex.size() >= 2 && hex[0] == '0' &&
        (hex[1] == 'x' || hex[1] == 'X')) {
        hex = hex.substr(2);
    }

    hex.erase(std::remove_if(hex.begin(), hex.end(), ::isspace), hex.end());

    if (hex.size() > 128) throw std::runtime_error("hexToWide512: hex string longer than 512 bits");
    if (hex.size() < 128) hex = std::string(128 - hex.size(), '0') + hex;

    Wide512 result{};

    for (int w = 0; w < 16; ++w) {
        const std::size_t start = 128 - (w + 1) * 8;
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

std::string wide512ToHex(const Wide512& value) {
    // Brought to you by the bot
    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (int w = 15; w >= 0; --w) oss << std::setw(8) << value[w];
    return oss.str();
}

struct TestVector {
    std::string i_hex;
    std::string expected_o_hex;
};

// Input interface struct
struct InputInterface {
    Wide512 i;
};

// Output interface struct
struct OutputInterface {
    Wide128 o;
};

std::vector<InputInterface> makeInputs(const std::vector<TestVector>& tvs) {
    std::vector<InputInterface> inputs;
    inputs.reserve(tvs.size());

    for (const auto& tv : tvs) {
        const std::string padded_block_hex = md5PadToSingleBlockHex(tv.i_hex);

        inputs.push_back(InputInterface{
            .i = hexToWide512(padded_block_hex),
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
    for (int w = 0; w < 16; ++w) {
        top->i[w]   = in.i[w];
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
            "1ac1ef01e96caf1be0d329331a4fc2a8"
        },
        {
            "00112233445566778899aabbccddeeff",
            "6e8311168ee16d6aa1aa48c64145003c"
        },
        {
            "00000000000000000000000000000000",
            "4ae71336e44bf9bf79d2752e234818a5"
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
