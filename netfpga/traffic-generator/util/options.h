//
// Created by uab on 5/16/24.
//

#ifndef OPTIONS_H
#define OPTIONS_H

#include <string>
#include <vector>

typedef struct
{
    std::vector<std::string> receive_interfaces;
    std::string transmit_interface;
    std::string src_ip_addr;
    std::string dest_ip_addr;
    std::vector<std::string> inputs;
    std::vector<std::string> expected_outputs;
    int port;
} options;

void print_options(const options& opts);
options get_options(int argc, char** argv);

#endif //OPTIONS_H
