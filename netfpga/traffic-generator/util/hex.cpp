//
// Created by uab on 5/16/24.
//

#include "hex.h"

#include <sstream>
#include <iomanip>
#include <cctype>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

std::string buffer_to_hex(char* buffer, ssize_t buffer_size)
{
    std::stringstream ss;
    for (size_t i = 0; i < buffer_size; ++i)
        ss << std::hex << std::setw(2) << std::setfill('0') << (static_cast<int>(buffer[i]) & 0xff) << " ";

    return ss.str();
}

uint8_t convert_hex_char(char c)
{
    if (c >= '0' && c <= '9') return static_cast<uint8_t>(c - '0');
    if (c >= 'a' && c <= 'f') return static_cast<uint8_t>(10 + (c - 'a'));
    if (c >= 'A' && c <= 'F') return static_cast<uint8_t>(10 + (c - 'A'));
    throw std::runtime_error(std::string("Invalid hex character: ") + c);
}

std::vector<uint8_t> string_to_hex(const std::string& hex_string)
{
    // 1. Strip spaces/underscores and optional 0x prefix
    std::string hex;
    hex.reserve(hex_string.size());

    for (char c : hex_string)
    {
        if (c == ' ' || c == '\t' || c == '\n' || c == '_')
            continue;
        hex.push_back(c);
    }

    if (hex.size() >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X'))
        hex.erase(0, 2);

    if (hex.empty())
        return {};

    if (hex.size() % 2 != 0)
        throw std::runtime_error("Hex string must have an even number of digits");

    std::vector<uint8_t> bytes;
    bytes.reserve(hex.size() / 2);

    for (std::size_t i = 0; i < hex.size(); i += 2)
    {
        uint8_t hi = convert_hex_char(hex[i]);
        uint8_t lo = convert_hex_char(hex[i + 1]);
        bytes.push_back(static_cast<uint8_t>((hi << 4) | lo));
    }

    return bytes;
}
