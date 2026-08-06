//
// Created by uab on 5/15/24.
//

#ifndef TRAFFIC_GENERATOR_SOCKET_H
#define TRAFFIC_GENERATOR_SOCKET_H

#include <string>

int create_transmit_socket(const std::string& interface_name);
int create_receive_socket(const std::string& interface_name);

// Installs a permanent ARP entry mapping dest_ip to dest_mac on interface_name, so the kernel
// can address outgoing packets without ever needing a live ARP reply. Requires CAP_NET_ADMIN.
void set_static_arp_entry(const std::string& interface_name, const std::string& dest_ip, const std::string& dest_mac);

#endif //TRAFFIC_GENERATOR_SOCKET_H
