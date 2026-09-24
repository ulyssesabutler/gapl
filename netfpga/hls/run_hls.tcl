# Builds one NetFPGA application's HLS kernel (hls-processor.cpp, top packet_body_processor).
#
#   vitis-run --mode hls --tcl run_hls.tcl        (Vitis HLS 2024.2)
#
# Everything comes from the environment, so any application (and later the Gradle task) can drive it:
#   HLS_APP_DIR     netfpga/src/<app>, holding hls-processor.cpp/.h and test.properties
#   HLS_WORK_DIR    where the HLS project is created (its syn/verilog/*.v is the kernel RTL)
#   HLS_CLOCK_NS    target clock period (default 10)
#   HLS_STEPS       any of: csim csynth cosim (default: all three, in that order)
#   HLS_PART        default xc7vx690tffg1761-3, the NetFPGA SUME part
set here      [file dirname [file normalize [info script]]]
set common    [file join $here common]
set app_dir   [file normalize $::env(HLS_APP_DIR)]
set work_dir  [file normalize $::env(HLS_WORK_DIR)]
set clock_ns  [expr {[info exists ::env(HLS_CLOCK_NS)] ? $::env(HLS_CLOCK_NS) : 10}]
set steps     [expr {[info exists ::env(HLS_STEPS)] ? $::env(HLS_STEPS) : "csim csynth cosim"}]
set part      [expr {[info exists ::env(HLS_PART)] ? $::env(HLS_PART) : "xc7vx690tffg1761-3"}]
set props     [file join $app_dir test.properties]
set cflags    "-I$common -I$app_dir"

file mkdir $work_dir
cd $work_dir
open_project -reset proj
set_top packet_body_processor
add_files [file join $app_dir hls-processor.cpp] -cflags $cflags
add_files -tb [file join $common kernel_tb.cpp] -cflags $cflags
open_solution -reset sol -flow_target vivado
set_part $part
create_clock -period $clock_ns

if {"csim" in $steps}   { csim_design -argv $props }
if {"csynth" in $steps} { csynth_design }
if {"cosim" in $steps}  { cosim_design -argv $props }
exit
