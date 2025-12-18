# program_fpga.tcl
# Usage:
#   vivado -mode batch -nojournal -nolog -source program_fpga.tcl -tclargs /path/to/top.bit

set bitfile [lindex $argv 0]
if {$bitfile eq ""} {
  puts "ERROR: No bitfile provided."
  exit 1
}
if {![file exists $bitfile]} {
  puts "ERROR: Bitfile not found: $bitfile"
  exit 1
}

open_hw_manager
connect_hw_server
open_hw_target

# Pick the first detected device (works for the common single-FPGA dev-board case)
set dev [lindex [get_hw_devices] 0]
if {$dev eq ""} {
  puts "ERROR: No hardware devices found. Is the board plugged in and JTAG drivers installed?"
  exit 1
}

current_hw_device $dev
refresh_hw_device $dev

set_property PROGRAM.FILE $bitfile $dev
program_hw_devices $dev

puts "OK: Programmed $dev with $bitfile"
exit 0
