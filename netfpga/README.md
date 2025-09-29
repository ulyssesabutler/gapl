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

## Initial setup

1. Build the NetFPGA to setup the project
- `./gradlew :netfpga:makeInit`
- This will load the Vivado settings using the file specified in `gradle.properties`, and then run `make` in the main NetFPGA project.
- This is expected to take a while, normally, 5-10 minutes.

2. Build the CAM and TCAM IPs
- This is a closed-source submodule that is not distributed by NetFPGA, but can be downloaded from Xilinx.
- Download the [module](https://www.xilinx.com/member/forms/download/design-license.html?cid=154257&filename=xapp1151_Param_CAM.zip).
- Copy the `xapp1151_Param_CAM.zip` file into `packet-processor/lib/hw/xilinx/cores/tcam_v1_1_0`.
- Copy the `xapp1151_Param_CAM.zip` file into `packet-processor/lib/hw/xilinx/cores/cam_v1_1_0`.
- Run `./gradlew :netfpga:makeIPs`

## Building the project

```
./gradlew :netfpga:build
```

## References

These instructions are 
