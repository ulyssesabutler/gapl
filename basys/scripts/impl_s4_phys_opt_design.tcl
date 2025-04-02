# tcl guide: https://docs.xilinx.com/r/2021.1-English/ug835-vivado-tcl-commands/phys_opt_design

source ./scripts/color_func.tcl

# get start time
set start_time [clock seconds]


# open previous checkpoint
print_yellow "reading checkpoint: ${name_chkp_impl3}"
open_checkpoint ${dir_chkp}/${name_chkp_impl3}.dcp

# run phys_opt_design
print_yellow "running implementation phase: phys_opt_design"

phys_opt_design \
    -bram_register_opt -dsp_register_opt -shift_register_opt \
    -fanout_opt -placement_opt \
    -rewire -retime \
    -verbose

# write checkpoint
print_yellow "writing checkpoint: ${name_chkp_impl4}"
write_checkpoint -force ${dir_chkp}/${name_chkp_impl4}.dcp



# * get elapsed time
# get end time
set end_time [clock seconds]
# calculate elapsed time in seconds
set elapsed_time [expr {$end_time - $start_time}]
# convert seconds to dd:hh:mm:ss format
set days [expr {$elapsed_time / (24 * 3600)}]
set rem_sec [expr {$elapsed_time % (24 * 3600)}]
set hours [expr {$rem_sec / 3600}]
set rem_sec [expr {$rem_sec % 3600}]
set minutes [expr {$rem_sec / 60}]
set seconds [expr {$rem_sec % 60}]
# print total time taken
print_blue "Simulation started at:  [clock format $start_time -format "%d-%b-%Y - %I:%M:%S - %p"]"
print_blue "Simulation ended at:    [clock format $end_time -format "%d-%b-%Y - %I:%M:%S - %p"]"
print_green "Implementation (phys_opt_design) time taken: [format \"%02d:%02d:%02d:%02d\" $days $hours $minutes $seconds]"
