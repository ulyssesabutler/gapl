//
// Created by uab on 5/16/24.
//

#include "socket.h"

#include <cstring>
#include <iostream>

#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netpacket/packet.h>
#include <net/ethernet.h>
#include <net/if.h>
#include <netinet/in.h>

ifreq create_interface_request(const char* interface_name)
{
    ifreq ifr{};
    strncpy(ifr.ifr_name, interface_name, IFNAMSIZ - 1);
    return ifr;
}

void set_promiscuous_mode(int socket_fd, const char* interface_name, bool enabled)
{
    std::cout << "    Setting promiscuous mode" << std::endl;

    // Search for the interface using its name
    ifreq ifr = create_interface_request(interface_name);

    // Retrieve the current flags
    if (ioctl(socket_fd, SIOCGIFFLAGS, &ifr) < 0)
    {
        perror(("Failed to get flags for interface " + std::string(interface_name)).c_str());
        exit(-1);
    }

    // Set the promiscuous flag
    if (enabled)
        ifr.ifr_flags |= IFF_PROMISC;
    else
        ifr.ifr_flags &= ~IFF_PROMISC;

    // Set the flags on the interface
    if (ioctl(socket_fd, SIOCSIFFLAGS, &ifr) < 0)
    {
        perror(("Failed to set flags for interface " + std::string(interface_name)).c_str());
        exit(-1);
    }
}

void bind_socket_to_interface(int socket_fd, const char* interface_name)
{
    std::cout << "    Binding socket to interface" << std::endl;
    ifreq ifr = create_interface_request(interface_name);

    if (setsockopt(socket_fd, SOL_SOCKET, SO_BINDTODEVICE, &ifr, sizeof(ifr)) < 0)
    {
        perror(("Failed bind socket to interface " + std::string(interface_name)).c_str());
        exit(-1);
    }
}

// Note: Doesn't seem to work with raw sockets, as raw socket mode implies IP_HDRINCL
void set_kernel_header_creation(int socket_fd, bool enabled)
{
    std::cout << "    Setting kernel header creation to " << (enabled ? "true" : "false") << std::endl;
    bool headers_included = !enabled;

    // We want the kernel to create the header for us by telling this socket that our packet will *not* include the IP header
    if (setsockopt(socket_fd, IPPROTO_IP, IP_HDRINCL, &headers_included, sizeof(headers_included)) < 0)
    {
        perror("Failed to set kernel header creation");
        exit(-1);
    }
}

int create_transmit_socket(const std::string& interface_name)
{
    int socket_fd;

    // This seems to imply IP_HDRINCL, so we have to construct the IP header ourselves
    if ((socket_fd = socket(AF_INET, SOCK_RAW, IPPROTO_RAW)) < 0)
    {
        perror("Failed to create socket");
        exit(-1);
    }

    bind_socket_to_interface(socket_fd, interface_name.c_str());
    set_kernel_header_creation(socket_fd, false);

    return socket_fd;
}

int create_receive_socket(const std::string& interface_name)
{
    int sockfd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    if (sockfd < 0) {
        perror("Failed to create AF_PACKET receive socket");
        exit(EXIT_FAILURE);
    }

    set_promiscuous_mode(sockfd, interface_name.c_str(), true);

    // Bind specifically to this interface
    struct sockaddr_ll sll{};
    sll.sll_family   = AF_PACKET;
    sll.sll_protocol = htons(ETH_P_ALL);
    sll.sll_ifindex  = if_nametoindex(interface_name.c_str());
    if (sll.sll_ifindex == 0) {
        perror("if_nametoindex");
        exit(EXIT_FAILURE);
    }

    if (bind(sockfd, reinterpret_cast<sockaddr*>(&sll), sizeof(sll)) < 0) {
        perror("bind(AF_PACKET)");
        exit(EXIT_FAILURE);
    }

    return sockfd;
}