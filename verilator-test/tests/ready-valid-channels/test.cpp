#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <map>
#include <random>
#include <string>
#include <vector>

// Layer 3, stage 2c: a @ready_valid design (see test.gapl) driven like real hardware. Each input is a
// source that offers items with random gaps and, once it asserts valid, holds it until accepted; each
// output's ready drops at random. Every output must receive exactly the model's sequence. Verilator
// encodes '$' as __024, so the IR's a$$valid / a$$ready appear as a__024__024valid / a__024__024ready.

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

struct Source {
    std::vector<int> items;
    size_t index = 0;
    bool holding = false;
    bool offer(std::mt19937& rng) {
        if (!holding) holding = index < items.size() && std::bernoulli_distribution(0.7)(rng);
        return holding;
    }
    int current() const { return index < items.size() ? items[index] : 0; }
    void taken() { ++index; holding = false; }
};

static bool run(unsigned seed) {
    Vtest* top = new Vtest();
    std::mt19937 rng(seed);
    std::uniform_int_distribution<int> byte(0, 255);
    std::bernoulli_distribution ready(0.6);

    const int count = 40;
    Source a, b;
    std::vector<int> t;
    for (int k = 0; k < count; ++k) {
        a.items.push_back(byte(rng));
        b.items.push_back(byte(rng));
        t.push_back((a.items[k] + b.items[k]) & 0xFF);
    }
    std::map<std::string, std::vector<int>> expected, received;
    for (int x : t) {
        expected["s"].push_back((x + 1) & 0xFF);
        expected["p"].push_back(x);
        expected["q"].push_back(x ^ 0xFF);
        expected["u"].push_back(x);
        expected["v"].push_back(x);
    }

    top->reset = 1;
    top->enable = 1;
    top->a__024__024valid = 0;
    top->b__024__024valid = 0;
    tick(top);
    top->reset = 0;
    tick(top);

    auto done = [&]() {
        for (auto& [name, items] : expected) if (received[name].size() < items.size()) return false;
        return true;
    };

    for (int cycle = 0; cycle < 5000 && !done(); ++cycle) {
        bool aValid = a.offer(rng), bValid = b.offer(rng);
        top->a = a.current(); top->a__024__024valid = aValid;
        top->b = b.current(); top->b__024__024valid = bValid;
        bool sR = ready(rng), pR = ready(rng), qR = ready(rng), uR = ready(rng), vR = ready(rng);
        top->s__024__024ready = sR; top->p__024__024ready = pR; top->q__024__024ready = qR;
        top->u__024__024ready = uR; top->v__024__024ready = vR;
        top->eval();

        if (top->s__024__024valid && sR) received["s"].push_back(top->s);
        if (top->p__024__024valid && pR) received["p"].push_back(top->p);
        if (top->q__024__024valid && qR) received["q"].push_back(top->q);
        if (top->u__024__024valid && uR) received["u"].push_back(top->u);
        if (top->v__024__024valid && vR) received["v"].push_back(top->v);
        bool aTaken = aValid && top->a__024__024ready;
        bool bTaken = bValid && top->b__024__024ready;

        tick(top);
        if (aTaken) a.taken();
        if (bTaken) b.taken();
    }

    bool ok = true;
    for (auto& [name, items] : expected) {
        if (received[name] != items) {
            std::cerr << "FAIL seed " << seed << ": output " << name << " received " << received[name].size()
                      << " items, expected " << items.size() << " (or values differ)" << std::endl;
            ok = false;
        }
    }
    top->final();
    delete top;
    return ok;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    bool ok = true;
    for (unsigned seed = 1; seed <= 5; ++seed) ok = run(seed) && ok;
    return ok ? 0 : 1;
}
