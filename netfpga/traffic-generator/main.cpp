#include <iostream>
#include <thread>
#include <sstream>
#include <string>

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

        std::cout << interface_name << ": "
            << "Sending " << packet_size << " bytes of data: " << '\n'
            << "  Full Packet: " << buffer_to_hex(reinterpret_cast<const uint8_t*>(buffer), packet_size) << '\n'
            << "  Message: " << buffer_to_hex(reinterpret_cast<const uint8_t*>(data), data_size) << std::endl;

        // Send the packet
        send_packet(socket_fd, buffer, packet_size, dest_ip, port);
    }
}

[[noreturn]] void receive_thread(
    int socket_fd,
    const std::string& interface_name,
    const std::vector<std::string>& expected_messages
) {
    char buffer[65536];

    while (true)
    {
        ssize_t data_size;
        receive_packet(socket_fd, buffer, sizeof(buffer), data_size);

        const uint8_t* payload = nullptr;
        size_t payload_len = 0;
        uint16_t src_port = 0;
        uint16_t dst_port = 0;

        // 1) Drop anything that's not IPv4+UDP
        if (!extract_padded_udp_payload(reinterpret_cast<const uint8_t*>(buffer), static_cast<size_t>(data_size), payload, payload_len, src_port, dst_port))
            continue;

        if (is_dhcp_packet(src_port, dst_port))
            continue;

        std::cout << interface_name << ": "
            << "Received " << data_size << " bytes of data: " << '\n'
            << "  Full Packet: " << buffer_to_hex(reinterpret_cast<const uint8_t*>(buffer), data_size) << '\n'
            << "  Message: " << buffer_to_hex(payload, payload_len) << std::endl;
    }
}

int main(int argc, char** argv)
{
    options options = get_options(argc, argv);
    std::cout << "Using parameters..." << std::endl;
    print_options(options);

    std::cout << "Starting receiver threads" << std::endl;
    std::vector<std::thread> receivers;
    for (const std::string& interface_name: options.receive_interfaces)
    {
        std::cout << "  Creating receiver for " << interface_name << std::endl;
        receivers.emplace_back(receive_thread, create_receive_socket(interface_name), interface_name, options.expected_outputs);
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

    return 0;
}
