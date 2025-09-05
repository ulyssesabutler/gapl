#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <memory>
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
}

// Expected behavior for passthrough: identity
static inline uint8_t expected(uint8_t x) { return x; }

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    Vtest* top = new Vtest();

    // Initialize signals
    top->reset = 1;   // assert reset initially
    top->enable = 1;  // keep enabled
    top->i = 0;
    tick(top);

    // Finish reset
    top->reset = 0;
    tick(top);

    // Deterministic random sequence for reproducibility
    std::mt19937 rng(12345);
    std::uniform_int_distribution<int> dist(0, 255);

    bool all_ok = true;
    const int cycles = 10;
    uint8_t prev = 0;

    for (int t = 0; t < cycles; ++t) {
        uint8_t in = static_cast<uint8_t>(dist(rng));
        top->i = in;

        top->eval();

        uint8_t out = static_cast<uint8_t>(top->o);
        uint8_t exp = expected(in);

        if (out != prev) {
            std::cerr << "FAIL cycle " << t
                      << ": in=" << static_cast<int>(in)
                      << " out=" << static_cast<int>(out)
                      << " expected=" << static_cast<int>(prev) << std::endl;
            all_ok = false;
        }

        // Advance one cycle
        tick(top);

        out = static_cast<uint8_t>(top->o);
        exp = expected(in);

        if (out != exp) {
            std::cerr << "FAIL cycle " << t
                      << ": in=" << static_cast<int>(in)
                      << " out=" << static_cast<int>(out)
                      << " expected=" << static_cast<int>(exp) << std::endl;
            all_ok = false;
        }

        prev = exp;
    }

    top->final();
    delete top;

    if (!all_ok) {
        return 1;
    } else {
        return 0;
    }
}
