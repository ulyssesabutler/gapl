# Usage: <hls tool> -f run_hls.tcl, with VARIANT set in the environment.
set v $::env(VARIANT)
open_project -reset proj_$v
set_top toy
add_files toy.cpp -cflags "-DVARIANT_$v"
# -flow_target only exists in Vitis HLS; Vivado HLS 2020.1 rejects it.
if {[catch {open_solution -reset sol -flow_target vivado}]} {
    open_solution -reset sol
}
set_part xc7vx690tffg1761-3
create_clock -period 4
csynth_design
exit
