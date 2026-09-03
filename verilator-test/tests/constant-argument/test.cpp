// Checks that a constant passed as a wire argument survives a mid-stream reset.
//
// Retiming preserves steady-state behaviour, not reset state. A register placed on a constant path
// resets to 0 rather than to the constant it carries, so the first beat after any reset is computed
// against zero. A testbench that resets once at time zero, before any data, can never see this -
// which is why the rest of this suite does not catch it. This one resets again with the pipeline
// already running, and tracks one specific beat through.
//
// The design is `o = i + 40`: accumulate(offset=3) adds 3 four times, then accumulate(offset=7)
// adds 7 four times, both instances of the same function reached through different constants.

#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <memory>

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

// Applies `in` for one cycle and returns the output observed during that same cycle. With L
// registers in the design, that output belongs to the input applied L cycles earlier.
static uint8_t step(Vtest* top, uint8_t in) {
    top->i = in;
    top->eval();
    uint8_t out = static_cast<uint8_t>(top->o);
    tick(top);
    return out;
}

static inline uint8_t expected(uint8_t x) { return static_cast<uint8_t>(x + 40); }

static const int MAX_LATENCY = 128;

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    Vtest* top = new Vtest();

    top->reset = 1;
    top->enable = 1;
    top->i = 0;
    tick(top);
    top->reset = 0;

    // Fill the pipeline so the design is in steady state before anything is measured.
    for (int k = 0; k < MAX_LATENCY; ++k) step(top, 0);

    // Measure the latency by pushing a marker through: the design's depth depends on the variation
    // (none when unretimed), so it cannot be hard-coded.
    int latency = -1;
    uint8_t marker = step(top, 1);
    if (marker == expected(1)) {
        latency = 0;
    } else {
        for (int k = 1; k < MAX_LATENCY; ++k) {
            if (step(top, 0) == expected(1)) { latency = k; break; }
        }
    }
    if (latency < 0) {
        std::cerr << "FAIL: could not determine latency; the design never produced "
                  << static_cast<int>(expected(1)) << " for input 1" << std::endl;
        top->final();
        delete top;
        return 1;
    }
    std::cout << "latency = " << latency << " cycle(s)" << std::endl;

    bool ok = true;

    // Steady-state check first, so a gross miscompile is reported as such rather than as a reset
    // problem.
    for (int k = 0; k < MAX_LATENCY; ++k) step(top, 0);
    const uint8_t probes[] = {0x00, 0x01, 0x5A, 0x7F, 0xC3, 0xFF};
    for (uint8_t probe : probes) {
        uint8_t observed = step(top, probe);
        for (int k = 1; k <= latency; ++k) observed = step(top, 0);
        if (observed != expected(probe)) {
            std::cerr << "FAIL steady state: in=" << static_cast<int>(probe)
                      << " out=" << static_cast<int>(observed)
                      << " expected=" << static_cast<int>(expected(probe)) << std::endl;
            ok = false;
        }
        for (int k = 0; k < MAX_LATENCY; ++k) step(top, 0);
    }

    // The point of this test: reset with the pipeline running, then check the very first beat
    // afterwards. If a constant sits behind a register, that register is still 0 on this cycle and
    // the beat is computed against the wrong constant.
    for (uint8_t probe : probes) {
        top->reset = 1;
        tick(top);
        top->reset = 0;

        uint8_t observed = step(top, probe);
        for (int k = 1; k <= latency; ++k) observed = step(top, 0);

        if (observed != expected(probe)) {
            std::cerr << "FAIL first beat after reset: in=" << static_cast<int>(probe)
                      << " out=" << static_cast<int>(observed)
                      << " expected=" << static_cast<int>(expected(probe))
                      << "  (a constant behind a register still reads 0 on this cycle)" << std::endl;
            ok = false;
        }
        for (int k = 0; k < MAX_LATENCY; ++k) step(top, 0);
    }

    top->final();
    delete top;
    return ok ? 0 : 1;
}
