#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <random>

// Layer 3, stage 2b: a @valid function's compiler-wired valid ports (see test.gapl). The IR names them
// p$$valid, a$$valid, ...; Verilator encodes '$' as __024, so they appear here as p__024__024valid etc.
// Unretimed, every island is combinational, so outputs and their valids follow the inputs within a cycle.

static vluint64_t sim_time = 0;
double sc_time_stamp() { return sim_time; }

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

    top->reset = 1;
    top->enable = 1;
    top->p__024a = 0;
    top->p__024b = 0;
    top->p__024__024valid = 0;
    tick(top);
    top->reset = 0;
    tick(top);

    std::mt19937 rng(12345);
    std::uniform_int_distribution<int> byte(0, 255);
    std::bernoulli_distribution present(0.6);

    bool all_ok = true;
    auto check = [&](int cycle, const char* what, int actual, int expected) {
        if (actual != expected) {
            std::cerr << "FAIL cycle " << cycle << ": " << what << "=" << actual << " expected=" << expected << std::endl;
            all_ok = false;
        }
    };

    for (int t = 0; t < 50; ++t) {
        uint8_t a = static_cast<uint8_t>(byte(rng));
        uint8_t b = static_cast<uint8_t>(byte(rng));
        bool valid = present(rng);

        top->p__024a = a;
        top->p__024b = b;
        top->p__024__024valid = valid;
        top->eval();

        // Everything derived from p carries p's valid; the constant island is always valid.
        check(t, "a$$valid", top->a__024__024valid, valid);
        check(t, "b$$valid", top->b__024__024valid, valid);
        check(t, "d$$valid", top->d__024__024valid, valid);
        check(t, "c$$valid", top->c__024__024valid, 1);
        check(t, "c", top->c, 42);

        if (valid) {
            check(t, "a", top->a, static_cast<uint8_t>(a + 1));
            check(t, "b", top->b, static_cast<uint8_t>(b + 1));
            check(t, "d", top->d, static_cast<uint8_t>(a + 1));
        }

        tick(top);
    }

    top->final();
    delete top;
    return all_ok ? 0 : 1;
}
