//
// Created by uab on 5/16/24.
//

#ifndef OPTIONS_H
#define OPTIONS_H

#include <string>
#include <vector>

typedef struct
{
    std::vector<std::string> inputs;
    std::vector<std::string> expected_outputs;
    std::string waveform_path;
} options;

void print_options(const options& opts);
options get_options(int argc, char** argv);

#endif //OPTIONS_H
