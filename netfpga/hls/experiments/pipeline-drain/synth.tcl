# vivado -mode batch -source synth.tcl -tclargs <rtl dir> <out dir>
set rtl [lindex $argv 0]
set out [lindex $argv 1]
file mkdir $out
foreach f [lsort [glob $rtl/*.v]] { read_verilog $f }
synth_design -top toy -part xc7vx690tffg1761-3 -mode out_of_context
create_clock -period 4.000 -name ap_clk [get_ports ap_clk]
report_utilization -file $out/util.rpt
report_timing_summary -file $out/timing.rpt
set wns [get_property SLACK [get_timing_paths -max_paths 1 -nworst 1 -setup]]
set util [report_utilization -return_string]
regexp {Slice LUTs\*?\s*\|\s*(\d+)} $util -> luts
regexp {Slice Registers\s*\|\s*(\d+)} $util -> ffs
puts "SYNTH_RESULT wns=$wns luts=$luts ffs=$ffs"
