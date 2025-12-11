//
// Created by uab on 5/16/24.
//

#include "packet.h"

#include <algorithm>
#include <string>
#include <cstring>
#include <cstdint>
#include <vector>
#include <netinet/if_ether.h>
#include <netinet/ip.h>
#include <netinet/udp.h>
#include <arpa/inet.h>
#include <net/ethernet.h>

uint16_t checksum16(const void* data, size_t len) {
    const uint8_t* p = static_cast<const uint8_t*>(data);
    uint32_t sum = 0;

    // sum 16-bit words
    while (len >= 2) {
        sum += (static_cast<uint16_t>(p[0]) << 8) | p[1];
        p += 2;
        len -= 2;
    }
    // handle odd trailing byte
    if (len == 1) {
        sum += static_cast<uint16_t>(p[0]) << 8;
    }

    // fold to 16 bits
    while (sum >> 16) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }
    return static_cast<uint16_t>(~sum);
}

struct iphdr create_ip_header(const std::string& src_ip_addr, const std::string& dest_ip_addr, size_t data_size)
{
    struct iphdr header;

    // The fields that seem to be actually important
    header.ihl = 5;
    header.version = 4;
    header.saddr = inet_addr(src_ip_addr.c_str());
    header.daddr = inet_addr(dest_ip_addr.c_str());
    header.protocol = IPPROTO_UDP;
    header.ttl = 255;  // Time to live

    // A bit hacky, but it should be fine for now
    static uint id = 0;
    header.id = htonl(id++);

    // These are values that we're kinda just ignoring
    header.tos = 0;
    header.frag_off = 0;

    header.tot_len = htons(sizeof(struct iphdr) + data_size);  // Total length

    header.check = 0;
    header.check = checksum16((const uint16_t*)(&header), sizeof(header));

    return header;
}

static inline struct udphdr create_udp_header(uint16_t src_port, uint16_t dst_port, size_t data_size)
{
    struct udphdr header;
    header.source = htons(src_port);
    header.dest   = htons(dst_port);
    header.len    = htons(static_cast<uint16_t>(sizeof(struct udphdr) + data_size));
    header.check  = 0; // 0 = no checksum for IPv4

    return header;
}

void create_ip_packet(char* buffer, const std::string& src_ip_addr, const std::string& dest_ip_addr, char* data, size_t data_size, size_t& packet_size)
{
    iphdr header = create_ip_header(src_ip_addr, dest_ip_addr, data_size);
    std::memcpy(buffer, &header, sizeof(header));
    std::memcpy(buffer + sizeof(header), data, data_size);
    packet_size = sizeof(header) + data_size;
}

void create_udp_packet(char* buffer, const std::string& src_ip_addr, const std::string& dest_ip_addr, uint16_t src_port, uint16_t dst_port, char* data, size_t data_size, size_t& packet_size)
{
    udphdr udp_header = create_udp_header(src_port, dst_port, data_size);
    iphdr ip_header = create_ip_header(src_ip_addr, dest_ip_addr, data_size + sizeof(udp_header));
    std::memcpy(buffer, &ip_header, sizeof(ip_header));
    std::memcpy(buffer + sizeof(ip_header), &udp_header, sizeof(udp_header));
    std::memcpy(buffer + sizeof(ip_header) + sizeof(udp_header), data, data_size);
    packet_size = sizeof(ip_header) + sizeof(udp_header) + data_size;
}

void create_padded_udp_packet(char* buffer, const std::string& src_ip_addr, const std::string& dest_ip_addr, uint16_t src_port, uint16_t dst_port, const char* data, size_t data_size, size_t& packet_size)
{
    constexpr size_t ETH_HDR_LEN  = 14;
    constexpr size_t IPV4_HDR_LEN = 20;
    constexpr size_t UDP_HDR_LEN  = 8;

    const size_t header_size = ETH_HDR_LEN + IPV4_HDR_LEN + UDP_HDR_LEN;

    const size_t padding = (32 - (header_size % 32)) % 32;

    const size_t padded_size = padding + data_size;
    std::vector<char> padded_payload(padded_size, 0);
    std::memcpy(padded_payload.data() + padding, data, data_size);

    create_udp_packet(buffer, src_ip_addr, dest_ip_addr, src_port, dst_port, padded_payload.data(), padded_payload.size(), packet_size);
}

void send_packet(int socket_fd, char* buffer, ssize_t data_size, const std::string& dest_ip_addr, uint dest_port)
{
    struct sockaddr_in dest_address;
    dest_address.sin_family = AF_INET;
    dest_address.sin_port = dest_port;
    dest_address.sin_addr.s_addr = inet_addr(dest_ip_addr.c_str());

    if (sendto(socket_fd, buffer, data_size, 0, (struct sockaddr *) &dest_address, sizeof(dest_address)) < 0)
    {
        perror("Failed to send packet");
        exit(-1);
    }
}

void receive_packet(int socket_fd, char* buffer, size_t buffer_size, ssize_t& data_size)
{
    data_size = recvfrom(socket_fd, buffer, buffer_size, 0, nullptr, nullptr);
    if (data_size < 0)
    {
        perror("Error receiving packet");
        exit(-1);
    }
}

// Thanks be to the bots
bool is_filtered_packet(const char* buffer, size_t length)
{
    // 1. Ethernet header
    if (length < sizeof(ethhdr)) {
        return false;
    }

    ethhdr eth{};
    std::memcpy(&eth, buffer, sizeof(eth));

    uint16_t eth_type = ntohs(eth.h_proto);
    if (eth_type != ETH_P_IP) {
        // Not IPv4, ignore
        return false;
    }

    size_t offset = sizeof(ethhdr);

    // 2. IPv4 header (minimum 20 bytes, but ihl can be larger)
    if (length < offset + sizeof(iphdr)) {
        return false;
    }

    iphdr ip{};
    std::memcpy(&ip, buffer + offset, sizeof(ip));

    if (ip.version != 4) {
        return false;
    }

    if (ip.ihl < 5) { // ihl is in 32-bit words
        return false;
    }

    if (ip.protocol != IPPROTO_UDP) {
        return false;
    }

    size_t ip_header_len = static_cast<size_t>(ip.ihl) * 4;
    if (length < offset + ip_header_len + sizeof(udphdr)) {
        return false;
    }

    offset += ip_header_len;

    // 3. UDP header
    udphdr udp{};
    std::memcpy(&udp, buffer + offset, sizeof(udp));

    uint16_t src_port = ntohs(udp.uh_sport);
    uint16_t dst_port = ntohs(udp.uh_dport);

    // DHCP uses UDP 67 (server) and 68 (client)
    if (src_port == 67 || src_port == 68 ||
        dst_port == 67 || dst_port == 68) {
        return true; // it's a DHCP packet
    }

    return false;
}

bool extract_udp_payload(
    const uint8_t* frame,
    size_t len,
    const uint8_t*& payload,
    size_t& payload_len,
    uint16_t& src_port,
    uint16_t& dst_port
) {
    // 1. Ethernet header
    if (len < sizeof(ethhdr)) {
        return false;
    }

    ethhdr eth{};
    std::memcpy(&eth, frame, sizeof(eth));

    uint16_t eth_type = ntohs(eth.h_proto);
    if (eth_type != ETH_P_IP) {
        // Not IPv4
        return false;
    }

    size_t offset = sizeof(ethhdr);

    // 2. IPv4 header
    if (len < offset + sizeof(iphdr)) {
        return false;
    }

    iphdr ip{};
    std::memcpy(&ip, frame + offset, sizeof(ip));

    if (ip.version != 4) {
        return false;
    }

    if (ip.ihl < 5) { // ihl is in 32-bit words
        return false;
    }

    if (ip.protocol != IPPROTO_UDP) {
        // Not UDP
        return false;
    }

    size_t ip_header_len = static_cast<size_t>(ip.ihl) * 4;
    if (len < offset + ip_header_len + sizeof(udphdr)) {
        return false;
    }

    offset += ip_header_len;

    // 3. UDP header
    udphdr udp{};
    std::memcpy(&udp, frame + offset, sizeof(udp));

    src_port = ntohs(udp.uh_sport);
    dst_port = ntohs(udp.uh_dport);

    // 4. Payload
    offset += sizeof(udphdr);
    if (len < offset) {
        return false;
    }

    // UDP length from header (includes UDP header)
    uint16_t udp_len = ntohs(udp.uh_ulen);
    size_t max_payload_by_len = 0;

    if (udp_len >= sizeof(udphdr)) {
        max_payload_by_len = static_cast<size_t>(udp_len) - sizeof(udphdr);
    }

    // Actual bytes available in this captured frame
    size_t available = len - offset;

    // Payload length is the smaller of what header claims and what we captured
    payload_len = std::min(max_payload_by_len, available);
    payload = frame + offset;

    return true;
}

bool is_dhcp_packet(uint16_t src_port, uint16_t dst_port)
{
    if (src_port == 67 || src_port == 68) return true;
    if (dst_port == 67 || dst_port == 68) return true;
    return false;
}