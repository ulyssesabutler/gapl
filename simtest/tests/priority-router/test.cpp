#include "Vtest.h"
#include <verilated.h>
#include <cstdint>
#include <iostream>
#include <memory>
#include <random>
#include <vector>
#include <functional>

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

// Input interface struct
struct InputInterface {
    uint32_t selector;
    uint32_t i1;
    uint32_t i2;
};

// Output interface struct
struct OutputInterface {
    uint32_t o;      // 1-bit output value
};

// Simulation function
std::vector<OutputInterface> simulate(
    Vtest* top,
    const std::vector<InputInterface>& inputs
) {
    std::vector<OutputInterface> outputs;
    
    // Initialize signals
    top->reset = 1;   // assert reset initially
    top->enable = 1;  // keep enabled
    top->selector = 0;
    top->i1 = 0;
    top->i2 = 0;
    tick(top);

    // Finish reset
    top->reset = 0;
    tick(top);

    OutputInterface currentOutput;

    for (size_t i = 0; i < inputs.size(); i++) {
        top->selector = inputs[i].selector;
        top->i1 = inputs[i].i1;
        top->i2 = inputs[i].i2;

        top->eval();

        // Capture output
        OutputInterface output = {
            .o = static_cast<uint32_t>(top->o),
        };

        // Save output
        outputs.push_back(output);
    }

    return outputs;
}

// Check if simulation was successful
bool checkSimulationSuccess(
    const std::vector<InputInterface>& inputs,
    const std::vector<OutputInterface>& outputs
) {
    if (outputs.size() != inputs.size()) {
        std::cerr << "Test Error: Inputs does not match outputs" << std::endl;
        exit(1);
    }

    // Zip inputs and outputs into pairs
    std::vector<std::pair<InputInterface, OutputInterface>> zipped;
    zipped.reserve(inputs.size());
    for (size_t i = 0; i < inputs.size(); ++i) {
        zipped.emplace_back(inputs[i], outputs[i]);
    }

    for (const auto& pair : zipped) {
        const InputInterface& in = pair.first;
        const OutputInterface& out = pair.second;

        uint32_t expected = 0;

        switch (in.selector) {
            case 0b0001:
                expected = in.i1 + in.i2;
                break;
            case 0b0010:
                expected = in.i1 * in.i2;
                break;
            case 0b0100:
                expected = in.i1 - in.i2;
                break;
            case 0b1000:
                expected = in.i1 & in.i2;
                break;
            case 0b0000:
                expected = in.i1 | in.i2;
                break;
        }

        if (out.o != expected) {
            std::cerr << "Test Error: Output does not match expected,"
                << " selector: " << in.selector
                << " i1: " << in.i1
                << " i2: " << in.i2
                << " o: " << out.o
                << " expected: " << expected
                << std::endl;

            return false;
        }
    }

    return true;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    Vtest* top = new Vtest();

    std::vector<InputInterface> inputs;
    for (int selectorShift = -1; selectorShift <= 3; ++selectorShift) {
        uint32_t selector;

        if (selector == -1) {
            selector = 0;
        } else {
            selector = selector << selectorShift;
        }

        for (uint32_t lhs = 0; lhs <= 9; ++lhs) {
            for (uint32_t rhs = 0; rhs <= lhs; ++rhs) {
                inputs.push_back(InputInterface{selector, lhs, rhs});
            }
        }
    }

    std::vector<OutputInterface> outputs = simulate(top, inputs);

    top->final();
    delete top;

    bool testPass = checkSimulationSuccess(inputs, outputs);

    if (testPass) {
        return 0;
    } else {
        return 1;
    }
}
