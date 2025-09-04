#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <random>

// Simple simulation time for Verilator
static vluint64_t sim_time = 0;
double sc_time_stamp() { return sim_time; }

// One clock tick: 0 -> 1 -> 0 with evals
static void tick(Vtest* top) {
    top->clock = 0;
    top->eval();
    ++sim_time;

    top->clock = 1;
    top->eval();
    ++sim_time;

    top->clock = 0;
    top->eval();
    ++sim_time;
}

// Expected behavior for passthrough: identity
static inline uint8_t expected(uint8_t x) { return x; }

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    Vtest* top = new Vtest();

    // Initialize signals
    top->clock = 0;
    top->reset = 1;   // assert reset initially
    top->enable = 1;  // keep enabled
    top->i = 0;
    top->eval();

    // Apply one reset cycle
    tick(top);
    top->reset = 0;

    // Deterministic random sequence for reproducibility
    std::mt19937 rng(12345);
    std::uniform_int_distribution<int> dist(0, 255);

    bool all_ok = true;
    const int cycles = 10;

    for (int t = 0; t < cycles; ++t) {
        uint8_t in = static_cast<uint8_t>(dist(rng));
        top->i = in;

        // Advance one cycle
        tick(top);

        uint8_t out = static_cast<uint8_t>(top->o);
        uint8_t exp = expected(in);

        if (out != exp) {
            std::cerr << "FAIL cycle " << t
                      << ": in=" << static_cast<int>(in)
                      << " out=" << static_cast<int>(out)
                      << " expected=" << static_cast<int>(exp) << std::endl;
            all_ok = false;
        } else {
            std::cout << "PASS cycle " << t
                      << ": " << static_cast<int>(in)
                      << " -> " << static_cast<int>(out) << std::endl;
        }
    }

    top->final();
    delete top;

    if (!all_ok) {
        std::cerr << "Test FAILED" << std::endl;
        return 1;
    }

    std::cout << "All tests passed" << std::endl;
    return 0;
}
