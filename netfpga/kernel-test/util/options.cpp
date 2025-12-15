//
// Created by uab on 5/16/24.
//

#include "options.h"

#include <iostream>
#include <unistd.h>

#include "../util/string_utils.h"

void print_options(const options& opts)
{
    std::cout << "  Inputs:                  " << vec_to_string(opts.inputs) << std::endl;
    std::cout << "  Expected Outputs:        " << vec_to_string(opts.expected_outputs) << std::endl;
    std::cout << "  Waveform Path:           " << opts.waveform_path << std::endl;
}

void print_help()
{
    std::cout << "Usage" << std::endl;
    std::cout << "  -i Inputs (specify as hex strings)" << std::endl;
    std::cout << "  -o Expected Outputs (specify as hex strings)" << std::endl;
    std::cout << "  -w Waveform Path" << std::endl;
}

options get_options(int argc, char** argv)
{
    std::vector<std::string> inputs;
    std::vector<std::string> expected_outputs;

    std::string waveform_path;

    int input;
    while ((input = getopt(argc, argv, "i:o:w:h")) != -1)
    {
        switch (input)
        {
            case 'i':
                inputs.emplace_back(optarg);
                break;
            case 'o':
                expected_outputs.emplace_back(optarg);
                break;
            case 'w':
                waveform_path = std::string(optarg);
                break;
            case 'h':
            default:
                print_help();
                exit(0);
        }
    }

    return options
    {
        .inputs = std::move(inputs),
        .expected_outputs = std::move(expected_outputs),
        .waveform_path = std::move(waveform_path)
    };
}