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
    uint32_t value;  // VALUE_WIDTH bits wide
    bool last;       // 1-bit signal
};

// Output interface struct
struct OutputInterface {
    bool value;      // 1-bit output value
    bool valid;      // 1-bit valid signal
};

// Simulation function
std::vector<OutputInterface> simulate(
    Vtest* top,
    const std::vector<InputInterface>& inputs,
    std::function<bool(const OutputInterface&)> stopCondition
) {
    std::vector<OutputInterface> outputs;
    
    // Initialize signals
    top->reset = 1;   // assert reset initially
    top->enable = 1;  // keep enabled
    top->i__024value = 0;
    top->i__024last  = 0;
    tick(top);

    // Finish reset
    top->reset = 0;
    tick(top);

    OutputInterface currentOutput;
    size_t input_index = 0;
    bool shouldStop = false;

    do {
        // Apply input
        if (input_index < inputs.size()) {
            top->i__024value = inputs[input_index].value;
            top->i__024last = inputs[input_index].last;

            input_index++;
        }

        top->eval();

        // Capture output
        OutputInterface output = {
            .value = static_cast<bool>(top->o__024value),
            .valid = static_cast<bool>(top->o__024valid)
        };

        // Save output
        outputs.push_back(output);

        // Check the stop condition
        shouldStop = stopCondition(output);

        // Advance the simulation
        tick(top);
    } while (!shouldStop);
    
    return outputs;
}

bool isValidOutput(const OutputInterface& output) {
    return output.valid;
}

std::vector<InputInterface> createInputsForString(const std::string &input) {
    std::vector<InputInterface> inputs;
    // Process string in chunks of 3 characters (3x8 = 24 bits)
    for (size_t i = 0; i < input.length(); i += 3) {
        InputInterface inputInterface;
        uint32_t value = 0;

        // Process up to 3 characters for this chunk
        for (size_t j = 0; j < 3 && (i + j) < input.length(); j++) {
            // Shift each character into position (MSB first)
            // First char goes to bits 23-16, second to 15-8, third to 7-0
            uint32_t charValue = static_cast<uint8_t>(input[i + j]);
            value |= (charValue << (8 * (2 - j)));
        }

        inputInterface.value = value;

        // Set last bit for the final chunk
        inputInterface.last = (i + 3 >= input.length());

        inputs.push_back(inputInterface);
    }

    return inputs;
}

// Check if simulation was successful
bool checkSimulationSuccess(
    const std::vector<InputInterface>& inputs,
    const std::vector<OutputInterface>& outputs
) {
    // Look for an output with valid=true
    for (const auto& output : outputs) {
        if (output.valid) {
            if (output.value) {
                return true;
            } else {
                std::cerr << "Test 1: Unexpected output value" << std::endl;
                return false;
            }
        }
    }

    std::cerr << "Test 1: No output value found" << std::endl;
    return false;
}

// Check if simulation was successful
bool checkSimulationFailure(
    const std::vector<InputInterface>& inputs,
    const std::vector<OutputInterface>& outputs
) {
    // Look for an output with valid=true
    for (const auto& output : outputs) {
        if (output.valid) {
            if (!output.value) {
                return true;
            } else {
                std::cerr << "Test 2: Unexpected output value" << std::endl;
                return false;
            }
        }
    }

    std::cerr << "Test 2: No output value found" << std::endl;
    return false;
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);

    Vtest* top = new Vtest();


    // Create input sequence from a test string
    std::string testString1 = "USER@DOMAIN.COM";
    std::vector<InputInterface> inputs1 = createInputsForString(testString1);

    // Run simulation with the isValidOutput stop condition
    std::vector<OutputInterface> outputs1 = simulate(top, inputs1, isValidOutput);


    // Create input sequence from a test string
    std::string testString2 = "NOT AN EMAIL";
    std::vector<InputInterface> inputs2 = createInputsForString(testString2);

    // Run simulation with the isValidOutput stop condition
    std::vector<OutputInterface> outputs2 = simulate(top, inputs2, isValidOutput);


    top->final();
    delete top;

    bool testPass = checkSimulationSuccess(inputs1, outputs1) && checkSimulationFailure(inputs2, outputs2);

    if (testPass) {
        return 0;
    } else {
        return 1;
    }
}
