#!/usr/bin/env bash
# For each HLS tool x pipeline variant: synthesize toy.cpp, then simulate the generated Verilog with
# Vivado 2020.1's xsim against tb.v. Prints one summary line per combination.
# (no set -u: Vitis HLS 2024.2 settings64.sh reads unset variables)
HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS="${TOOLS_OVERRIDE:-vivadohls2020:/tools/Xilinx/Vivado/2020.1/settings64.sh:vivado_hls \
vitishls2020:/tools/Xilinx/Vitis/2020.1/settings64.sh:vitis_hls \
vitishls2024:/tools/Xilinx/Vitis_HLS/2024.2/settings64.sh:vitis_hls}"
VARIANTS="STP FLP FRP FLUSH NB"

run_one() {
    local name=$1 settings=$2 bin=$3 v=$4
    local dir="$HERE/runs/$name/$v"
    rm -rf "$dir"; mkdir -p "$dir"
    cp "$HERE/toy.cpp" "$HERE/run_hls.tcl" "$HERE/tb.v" "$dir/"
    cd "$dir"
    ( source "$settings" >/dev/null 2>&1; VARIANT=$v "$bin" -f run_hls.tcl > hls.log 2>&1 )
    local rtl="$dir/proj_$v/sol/syn/verilog"
    if ! ls "$rtl"/*.v >/dev/null 2>&1; then
        echo "$name $v: HLS FAILED ($(grep -m1 -E 'ERROR' hls.log | cut -c1-150))"
        return
    fi
    local lat=$(grep -m1 -A6 "Latency (cycles)" "$dir"/proj_$v/sol/syn/report/toy_csynth.rpt | grep -m1 -E "^\s*\|" | tr -s ' ')
    local warn=$(grep -c -i "WARNING" hls.log)
    (
        source /tools/Xilinx/Vivado/2020.1/settings64.sh >/dev/null 2>&1
        mkdir -p sim && cd sim
        xvlog "$rtl"/*.v ../tb.v > xvlog.log 2>&1 && \
        xelab -debug off tb -s tb_sim > xelab.log 2>&1 && \
        xsim tb_sim -R > xsim.log 2>&1
    )
    local res=$(grep -m1 "RESULT" sim/xsim.log 2>/dev/null)
    [ -z "$res" ] && res="SIM FAILED ($(grep -h -m1 ERROR sim/*.log | cut -c1-150))"
    echo "$name $v: $res | csynth latency row: $lat | hls warnings: $warn"
}

for t in $TOOLS; do
    IFS=: read -r name settings bin <<< "$t"
    for v in $VARIANTS; do
        run_one "$name" "$settings" "$bin" "$v" &
    done
    wait
done
