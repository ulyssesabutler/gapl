#!/usr/bin/env bash
# Out-of-context Vivado 2020.1 synthesis of selected runs' RTL for the NetFPGA SUME part.
HERE=$(cd "$(dirname "$0")" && pwd)
source /tools/Xilinx/Vivado/2020.1/settings64.sh >/dev/null 2>&1
for tv in vivadohls2020/NB vitishls2020/NB vitishls2024/NB vitishls2024/FLP vivadohls2020/FLUSH; do
    d="$HERE/runs/$tv"; v=$(basename "$tv")
    (
        mkdir -p "$d/synth" && cd "$d/synth"
        vivado -mode batch -nojournal -source "$HERE/synth.tcl" \
            -tclargs "$d/proj_$v/sol/syn/verilog" "$d/synth" > vivado.log 2>&1
        echo "$tv: $(grep -m1 SYNTH_RESULT vivado.log || grep -m1 -E 'ERROR' vivado.log || echo 'no result')"
    ) &
done
wait
