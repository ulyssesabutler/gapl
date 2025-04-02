# ======================================== VARS
prj_dir = $(realpath .)

# NOTE: chnage as needed
# random sim/synth/impl run name
config = run1


# synthesis and implementation dcp name
synth		= synthesis_$(config)
impl_1		= impl_s1_opt_design_$(config)
impl_2		= impl_s2_power_opt_design_$(config)
impl_3		= impl_s3_place_design_$(config)
impl_4		= impl_s4_phys_opt_design_$(config)
impl_5		= impl_s5_route_design_$(config)
bitstream	= bitstream_$(config)

# ======================================== PATHS

DIR_CHECKPOINT	= $(prj_dir)/bin/checkpoints/$(config)
DIR_REPORT 		= $(prj_dir)/bin/reports/$(config)
DIR_RESULTS_TB	= $(prj_dir)/bin/testbench

# testbench simulation
rtl			?= ./rtl/uart/uart_controller.v ./rtl/uart/uart_transmitter.v ./rtl/uart/uart_receiver.v ./rtl/util/fifo.v ./rtl/util/debouncer.v
tb			?= ./tb/uart/tb_uart_controller.v

# first file path
# TB_MODULE	?= $(basename $(notdir $(word 1, $(rtl))))
# last file path
#TB_MODULE	?= tb_$(basename $(notdir $(word $(words $(rtl)), $(rtl))))
TB_MODULE ?= tb_uart_controller
WORKLIB		:= work # xil_defaultlib

# Vivado testbench simulation flags
FLAGS_XVHDL	:= --2008 -v 0 --work $(WORKLIB) --incr --relax
FLAGS_XVLOG := -sv -v 0 --work $(WORKLIB) --incr --relax
FLAGS_XELAB := --incr --debug typical --relax --mt 8 -L $(WORKLIB)
FLAGS_XSIM	:= -gui -ieeewarnings


# ======================================== TARGETS

# default target
# default: help
default: run_vivado

# create vivado project
setup_project:
	@ vivado -mode tcl -source scripts/setup_project.tcl

# run synthesis and implementation
run_vivado: setup_project
	@ rm -rf .gen .srcs
	@ vivado -mode batch -nojournal -notrace -stack 2000 \
		-source ./scripts/main.tcl -tclargs \
		$(synth) \
		$(impl_1) $(impl_2) $(impl_3) $(impl_4) $(impl_5) $(bitstream)

# Combined target
tb_simulation:
	@ [[ -d ${DIR_RESULTS_TB} ]] || mkdir ${DIR_RESULTS_TB}
	@ rm -rf xsim.dir
	@ xvlog $(FLAGS_XVLOG) $(rtl)
	@ xvlog $(FLAGS_XVLOG) $(tb)
	@ xelab $(TB_MODULE) -s ${TB_MODULE}_behav $(FLAGS_XELAB) \
	-log $(DIR_RESULTS_TB)/elaborate_${TB_MODULE}.log
	xsim ${TB_MODULE}_behav $(FLAGS_XSIM) \
	-log $(DIR_RESULTS_TB)/simulate_${TB_MODULE}.log -wdb $(DIR_RESULTS_TB)/waveform_db_${TB_MODULE}.wdb

# move generated checkpoints to folder: ./bin/checkpoints/<config>
move_checkpoints:
	@ echo "moving checkpoints to folder: $(DIR_CHECKPOINT)"
	@ [ -d $(DIR_CHECKPOINT) ] || mkdir -p $(DIR_CHECKPOINT)
	mv -v ./bin/checkpoints/*.dcp $(DIR_CHECKPOINT)/

# move generated reports to folder: ./bin/reports/<config>
move_reports:
	@echo "moving reports to folder: $(DIR_REPORT)"
	@[ -d $(DIR_REPORT) ] || mkdir -p $(DIR_REPORT)
	mv -v ./bin/reports/*.rpt $(DIR_REPORT)/


clean_checkpoints:
	@ rm -rf ./bin/checkpoints

clean_logs:
	@ rm -rf ./bin/logs

clean_reports:
	@ rm -rf ./bin/reports

clean_testbenches:
	@ rm -rf ./bin/testbench

# insert you project dir here
clean_projects:
	@ rm -rf build

clean_bin:
	rm -rf *.jou *.log *.str
	rm -rf xsim.dir *.vcd *.wdb *.zip *.xml *.pb
	rm -rf .gen .srcs
	rm -rf ./-p .Xil

clean:
	@ make clean_checkpoints clean_logs clean_reports clean_testbenches
	@ make clean_projects
	@ make clean_bin
