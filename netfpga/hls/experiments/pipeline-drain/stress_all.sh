#!/usr/bin/env bash
# Re-simulates already-synthesized runs/<tool>/<variant> RTL against tb_stress.v.
HERE=$(cd "$(dirname "$0")" && pwd)
source /tools/Xilinx/Vivado/2020.1/settings64.sh >/dev/null 2>&1
for d in "$HERE"/runs/*/*; do
    v=$(basename "$d"); t=$(basename "$(dirname "$d")")
    rtl="$d/proj_$v/sol/syn/verilog"
    ls "$rtl"/*.v >/dev/null 2>&1 || continue
    (
        mkdir -p "$d/stress" && cd "$d/stress"
        xvlog "$rtl"/*.v "$HERE/tb_stress.v" > xvlog.log 2>&1 && \
        xelab -debug off tb -s tb_sim > xelab.log 2>&1 && \
        xsim tb_sim -R > xsim.log 2>&1
        echo "$t $v: $(grep -m1 RESULT xsim.log || echo 'SIM FAILED')"
    ) &
done
wait
