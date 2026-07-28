#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <random>
#include <vector>

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
    std::uniform_int_distribution<uint32_t> dist(0, 0xFFFFFFFFu);

    const int cycles = 48;
    std::vector<uint32_t> inputs(cycles);
    std::vector<uint32_t> outputs(cycles);

    for (int t = 0; t < cycles; ++t) {
        uint32_t in = dist(rng);
        inputs[t] = in;
        top->i = in;

        tick(top);

        outputs[t] = static_cast<uint32_t>(top->o);
    }

    top->final();
    delete top;

    // test.gapl's pipeline is a single straight-line chain of four bitwise_not stages
    // (an even count, so the whole chain composes to the identity function) with one
    // register() in source. The DAG retiming solver is free to insert additional
    // pipeline registers anywhere along that chain to hit its target clock period, so
    // the number of registers between input and output (and hence the end-to-end
    // latency) isn't fixed by the source alone. Regardless of where those registers
    // land, the composed identity function guarantees o[t] == i[t - N] for whatever
    // fixed latency N the retimer settles on, so discover N empirically instead of
    // hardcoding it.
    const int maxLatency = 24;
    const int minSamplesToConfirm = 16;

    int foundLatency = -1;
    for (int n = 0; n <= maxLatency && cycles - n >= minSamplesToConfirm; ++n) {
        bool ok = true;
        for (int t = n; t < cycles; ++t) {
            if (outputs[t] != inputs[t - n]) {
                ok = false;
                break;
            }
        }
        if (ok) {
            foundLatency = n;
            break;
        }
    }

    if (foundLatency < 0) {
        std::cerr << "FAIL: no fixed pipeline latency (0.." << maxLatency
                   << ") reproduces the expected identity behavior" << std::endl;
        for (int t = 0; t < cycles; ++t) {
            std::cerr << "  t=" << t << " in=0x" << std::hex << inputs[t]
                       << " out=0x" << outputs[t] << std::dec << std::endl;
        }
        return 1;
    }

    std::cerr << "Detected pipeline latency: " << foundLatency << " cycle(s)" << std::endl;
    return 0;
}
