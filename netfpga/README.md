# Setting up the NetFPGA build target

## Prerequisites

1. Vivado

This project is normally built with Vivado 2020.1.
This is normally in the /tools/Xilinx/Vivado/2020.1 directory.
The installation will ship with a settings file in the installation directory, such as `settings64.sh`.
The location of this file should be specified in using the `vivadoSettings` property in the `gradle.properties` file.

Vivado also requires a [license](https://www.xilinx.com/getlicense).

2. ncurses

This is a dependency of Vivado.

3. 64 GB of swap space

Without this, the build will likely fail.

## Editing GAPL code

The GAPL code is located in the `src` directory.
Specifically, you can edit the `src/packet_body_processor.gapl` file.

## Compiling GAPL

1. Compile gapl code to Verilog, placed in `netfpga/build/verilog`
- `./gradlew :netfpga:generateGaplVerilog`

2. Install Verilog files into the NetFPGA project
- `./gradlew :netfpga:installGaplVerilog`

## Initial setup

1. Build the NetFPGA to setup the project
- `./gradlew :netfpga:makeInit`
- This will load the Vivado settings using the file specified in `gradle.properties`, and then run `make` in the main NetFPGA project.
- This is expected to take a while, normally, 5-10 minutes.

2. Build the CAM and TCAM IPs
- This is a closed-source submodule that is not distributed by NetFPGA, but can be downloaded from Xilinx.
- Download the [module](https://www.xilinx.com/member/forms/download/design-license.html?cid=154257&filename=xapp1151_Param_CAM.zip).
- If the IPs have not been built before
    - Copy the `xapp1151_Param_CAM.zip` file into `packet-processor/lib/hw/xilinx/cores/tcam_v1_1_0`.
    - Copy the `xapp1151_Param_CAM.zip` file into `packet-processor/lib/hw/xilinx/cores/cam_v1_1_0`.
    - Run `./gradlew :netfpga:makeIPs`
- If the IPs have been built before
    - Run `./gradlew :netfpga:remakeIPs`

3. Build the packet processor
- `./gradlew :netfpga:build`

## Simulation

The simulation can be run using `./gradlew :netfpga:runSimulation`.

## Building the project

```
./gradlew :netfpga:build
```

## Flashing the FPGA

1. Log into the host
- Log into ravnica using `ssh -X uab@172.24.71.241`.

2. Load vivado
- Use `source /tools/Xilinx/Vivado/2020.1/settings64.sh`

3. Run vivado
- Use `vivado`

4. Connect to the FPGA
- Under "Tasks" click "Open Hardware Manager"
- In the green dialog box, click "Open target"
- In the dropdown, click "Auto Connect"

5. Flash the bitstream
- Right click on the part, `xc7vx690t_0 (1)`
- Click "Program Device"
- Select the bitstream file
- Click "Program"

6. Close vivado

## Testing the hardware

More information is available in the packet processor README.

1. Start tcpdump
- `sudo tcpdump -vvvexxXX -i enp7s0f1 'not (udp port 67 or udp port 68)'`

2. Start the traffic generator
- It lives in `/home/uab/traffic-generator`
- `sudo ./generator -t enp7s0f0 -r enp7s0f0 -r enp7s0f1 -s 172.24.71.241 -d 172.24.71.238 -p 1`

## References

These instructions are based on the instructions for the [reference switch](https://github.com/NetFPGA/NetFPGA-SUME-public/wiki/NetFPGA-SUME-Reference-Learning-Switch) and [CAM setup](https://github.com/NetFPGA/NetFPGA-SUME-public/wiki/NetFPGA-SUME-TCAM-IPs).
