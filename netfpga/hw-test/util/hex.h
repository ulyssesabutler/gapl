//
// Created by uab on 5/15/24.
//

#ifndef TRAFFIC_GENERATOR_HEX_H
#define TRAFFIC_GENERATOR_HEX_H

#include <string>
#include <vector>

std::string buffer_to_hex(const uint8_t* buffer, ssize_t buffer_size);
std::vector<uint8_t> string_to_hex(const std::string& hex_string);

#endif //TRAFFIC_GENERATOR_HEX_H
