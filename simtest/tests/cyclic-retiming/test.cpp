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
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    Vtest* top = new Vtest();

    // Initialize signals; assert reset to force the register to 0.
    top->reset = 1;
    top->enable = 1;
    top->i = 0;
    tick(top);

    // test.gapl's single feedback loop reduces to: o(t+1) = i(t) & o(t) (see below), which
    // is absorbing at 0 -- once any bit of o is cleared it never comes back. Feeding i =
    // all-ones on this reset-deassertion tick starts the accumulator at o = all-ones instead
    // of the degenerate all-zero state, so the check below actually exercises the AND-accumulate
    // behavior for a few cycles before it converges to 0.
    top->reset = 0;
    top->i = 0xFFFFFFFFu;
    tick(top);

    // Deterministic random sequence for reproducibility
    std::mt19937 rng(12345);
    std::uniform_int_distribution<uint32_t> dist(0, 0xFFFFFFFFu);

    bool all_ok = true;
    const int cycles = 16;

    // Derivation (see test.gapl): the loop computes
    //   cycle_entrance = i & cycle_end
    //   register.next  = ~cycle_entrance
    //   cycle_exit = o = ~register.current
    //   cycle_end  = ~~cycle_exit = cycle_exit  (the two trailing unary() calls cancel)
    // so, writing R for the register and o(t)/i(t) for this cycle's values:
    //   o(t) = ~R(t),  R(t+1) = ~(i(t) & o(t))  =>  o(t+1) = ~R(t+1) = i(t) & o(t).
    uint32_t expected_o = 0xFFFFFFFFu;

    for (int t = 0; t < cycles; ++t) {
        uint32_t in = dist(rng);
        top->i = in;

        top->eval();

        uint32_t out = static_cast<uint32_t>(top->o);
        if (out != expected_o) {
            std::cerr << "FAIL cycle " << t
                       << " (pre-tick): in=0x" << std::hex << in
                       << " out=0x" << out
                       << " expected=0x" << expected_o << std::dec << std::endl;
            all_ok = false;
        }

        // Advance one cycle; the accumulator updates using this cycle's input and output.
        expected_o = in & expected_o;
        tick(top);

        out = static_cast<uint32_t>(top->o);
        if (out != expected_o) {
            std::cerr << "FAIL cycle " << t
                       << " (post-tick): in=0x" << std::hex << in
                       << " out=0x" << out
                       << " expected=0x" << expected_o << std::dec << std::endl;
            all_ok = false;
        }
    }

    top->final();
    delete top;

    return all_ok ? 0 : 1;
}
