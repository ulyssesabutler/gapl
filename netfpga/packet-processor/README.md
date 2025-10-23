# NetFPGA Data Preprocessor

## Building the reference switch on Cyclops

Once this package has been downloaded, and Vivado has been installed (on Cyclops, we're using the Vivado installation in `/home/uab/Xilinx/Vivado/2020.1`), build the reference switch using the instrutions found on the NetFPGA SUME Wiki, [here](https://github.com/NetFPGA/NetFPGA-SUME-public/wiki/NetFPGA-SUME-Reference-Learning-Switch).

## Testing the implementation on Ravnica

Copy the resulting `reference_switch.bit` file onto Ravnica (where the FPGA is located) and flash it to the FPGA.

Now, we can test it using the traffic generator, found in [this repository](https://github.com/ulyssesabutler/traffic-generator).

This program will create raw IP packets. We want to send these raw IP packets to a dummy IP address.
To send these IP packets, our system's networking stack will have to construct an Ethernet frame using the destination MAC address corresponding to our destination IP address in our system's APR table.
That said, since our destination is a dummy IP address, a real entry won't exist in the APR table.
We can add an entry to the APR entry using the following command
```
sudo arp -s 172.24.71.238 00:18:3E:02:0E:74 -i enp7s0f0
```
In this example, `172.24.71.238` is the destination IP address, `00:18:3E:02:0E:74` is the desired destination MAC address, and `enp7s0f0` is the interface we will be sending the packets from.

Next, to detect the packets that are returned by the switch, we can use `tcpdump`.
```
sudo tcpdump -vvvexxXX -i enp7s0f1
```
where `enp7s0f1` is the interface we plan on retrieving packets on.

We can optionally filter out DHCP packets which make up background noise
```
sudo tcpdump -vvvexxXX -i enp7s0f1 'not (udp port 67 or udp port 68)'
```

Finally, we can start sending the packet using the binary produced by making the traffic generator package
```
sudo ./generator -t enp7s0f0 -r enp7s0f0 -r enp7s0f1 -s 172.24.71.241 -d 172.24.71.238 -p 1
```
Here, we specify the transmitting interface using `-t enp7s0f0`.
We can specify any number of receiving interfaces using `-r enp7s0f0 -r enp7s0f1` (though, we are using `tcpdump` instead of this program).
We specify the source and destination IP addresses using `-s 172.24.71.241 -d 172.24.71.238`.
Finally, we specify the TCP port with `-p 1`.
