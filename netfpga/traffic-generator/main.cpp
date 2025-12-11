#include <iostream>
#include <thread>
#include <sstream>
#include <string>
#include <cstring>

#include "network/socket.h"
#include "network/packet.h"
#include "util/hex.h"
#include "util/options.h"

void transmit_thread(
    int socket_fd,
    const std::string interface_name,
    const std::string& src_ip,
    const std::string& dest_ip,
    uint port,
    const std::vector<std::string>& messages
) {
    char buffer[65536];

    for (const auto& msg : messages)
    {
        std::vector<uint8_t> hex_data = string_to_hex(msg);

        const char* data = reinterpret_cast<const char*>(hex_data.data());
        const size_t data_size = hex_data.size();

        // Create the packet
        size_t packet_size = 0;
        create_padded_udp_packet(buffer, src_ip, dest_ip, port, port, data, data_size, packet_size);

        std::stringstream log_string;

        log_string << interface_name << ": "
            << "Sending " << packet_size << " bytes of data: " << '\n'
            << "  Full Packet: " << buffer_to_hex(reinterpret_cast<const uint8_t*>(buffer), packet_size) << '\n'
            << "  Message:     " << buffer_to_hex(reinterpret_cast<const uint8_t*>(data), data_size) << '\n';

        std::cout << log_string.str() << std::flush;

        // Send the packet
        send_packet(socket_fd, buffer, packet_size, dest_ip, port);
    }
}

void receive_thread(
    int socket_fd,
    const std::string& interface_name,
    const std::vector<std::string>& expected_messages,
    bool& any_failures
) {
    char buffer[65536];

    for (const auto& expected_message : expected_messages)
    {
        std::vector<uint8_t> expected_output_hex_data = string_to_hex(expected_message);

        const char* expected_data = reinterpret_cast<const char*>(expected_output_hex_data.data());
        const size_t expected_data_size = expected_output_hex_data.size();

        ssize_t data_size;
        if (!receive_packet(socket_fd, buffer, sizeof(buffer), data_size, 3000))
        {
            std::cout << interface_name << ": " << "Timed out waiting for packet" << std::endl;
            any_failures = true;
            return;
        }

        const uint8_t* payload = nullptr;
        size_t payload_len = 0;
        uint16_t src_port = 0;
        uint16_t dst_port = 0;

        // 1) Drop anything that's not IPv4+UDP
        if (!extract_padded_udp_payload(reinterpret_cast<const uint8_t*>(buffer), static_cast<size_t>(data_size), payload, payload_len, src_port, dst_port))
            continue;

        if (is_dhcp_packet(src_port, dst_port))
            continue;

        bool do_messages_match = (payload_len == expected_data_size) && (memcmp(payload, expected_data, payload_len) == 0);

        if (!do_messages_match) any_failures = true;

        std::stringstream log_string;

        log_string << interface_name << ": "
            << "Received " << data_size << " bytes of data: " << '\n'
            << "  Full Packet:      " << buffer_to_hex(reinterpret_cast<const uint8_t*>(buffer), data_size) << '\n'
            << "  Message:          " << buffer_to_hex(payload, payload_len) << '\n'
            << "  Expected Message: " << buffer_to_hex(reinterpret_cast<const uint8_t*>(expected_data), expected_data_size) << '\n'
            << "  Match?:           " << do_messages_match << '\n';

        std::cout << log_string.str() << std::flush;
    }
}

int main(int argc, char** argv)
{
    bool any_receiver_failures = false;

    options options = get_options(argc, argv);
    std::cout << "Using parameters..." << std::endl;
    print_options(options);

    std::cout << "Starting receiver threads" << std::endl;
    std::vector<std::thread> receivers;
    for (const std::string& interface_name: options.receive_interfaces)
    {
        std::cout << "  Creating receiver for " << interface_name << std::endl;
        receivers.emplace_back(receive_thread, create_receive_socket(interface_name), interface_name, options.expected_outputs, std::ref(any_receiver_failures));
        std::cout << "    Done" << std::endl;
    }

    std::cout << "Starting transmit thread" << std::endl;
    std::string interface_name = options.transmit_interface;
    std::cout << "  Creating transmitter for " << interface_name << std::endl;
    std::thread transmitter
    (
        transmit_thread,
        create_transmit_socket(interface_name),
        interface_name,
        options.src_ip_addr,
        options.dest_ip_addr,
        options.port,
        options.inputs
    );
    std::cout << "    Done" << std::endl;

    for (std::thread& receiver: receivers) receiver.join();
    transmitter.join();

    return any_receiver_failures ? 1 : 0;
}
