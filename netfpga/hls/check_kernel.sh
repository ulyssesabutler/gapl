#!/usr/bin/env bash
# Builds and checks one application's HLS kernel end to end, without any NetFPGA build:
#   1. Vitis HLS 2024.2: C-sim, csynth and C/RTL cosim against the app's test.properties
#      (run_hls.tcl + common/kernel_tb.cpp),
#   2. Vivado 2020.1 xsim - the NetFPGA flow's own simulator version - of the generated RTL against
#      common/drain_tb.v (drain + flow-control stress).
#
# Usage: netfpga/hls/check_kernel.sh <app> [work dir]
#   e.g. netfpga/hls/check_kernel.sh md5      (work dir defaults to netfpga/build/hls/<app>)
# Tool locations can be overridden with VITIS_HLS_SETTINGS / VIVADO_SETTINGS. HLS_CLOCK_NS and
# HLS_STEPS pass through to run_hls.tcl.
set -eo pipefail
here=$(cd "$(dirname "$0")" && pwd)
app=${1:?usage: check_kernel.sh <app> [work dir]}
app_dir="$here/../src/$app"
work=$(mkdir -p "${2:-$here/../build/hls/$app}" && cd "${2:-$here/../build/hls/$app}" && pwd)
vitis_hls_settings=${VITIS_HLS_SETTINGS:-/tools/Xilinx/Vitis_HLS/2024.2/settings64.sh}
vivado_settings=${VIVADO_SETTINGS:-/tools/Xilinx/Vivado/2020.1/settings64.sh}

[ -f "$app_dir/hls-processor.cpp" ] || { echo "no $app_dir/hls-processor.cpp" >&2; exit 2; }

echo "== [$app] Vitis HLS: ${HLS_STEPS:-csim csynth cosim} (log: $work/hls.log)"
(
    source "$vitis_hls_settings" >/dev/null 2>&1
    cd "$work"
    HLS_APP_DIR="$app_dir" HLS_WORK_DIR="$work" vitis-run --mode hls --tcl "$here/run_hls.tcl"
) > "$work/hls.log" 2>&1 || { echo "Vitis HLS failed:"; grep -E "ERROR|RESULT" "$work/hls.log" | head -20; exit 1; }
grep -E "RESULT (PASS|FAIL)" "$work/hls.log" | sed 's/^/   /' || true
if grep -q "RESULT FAIL" "$work/hls.log"; then echo "kernel_tb failed - see $work/hls.log"; exit 1; fi
grep -h "C/RTL co-simulation finished" "$work/hls.log" | sed 's/^/   /' || true
if grep -q "C/RTL co-simulation finished: FAIL" "$work/hls.log"; then exit 1; fi

rtl="$work/proj/sol/syn/verilog"
if ls "$rtl"/*.v >/dev/null 2>&1; then
    report="$work/proj/sol/syn/report/packet_body_processor_csynth.rpt"
    grep -m1 -A8 "Latency (cycles)" "$report" 2>/dev/null | sed 's/^/   /' || true
    echo "== [$app] Vivado 2020.1 xsim: drain_tb.v (log: $work/drain/xsim.log)"
    (
        source "$vivado_settings" >/dev/null 2>&1
        mkdir -p "$work/drain" && cd "$work/drain"
        xvlog "$rtl"/*.v "$here/common/drain_tb.v" > xvlog.log 2>&1
        xelab -debug off drain_tb -s drain_sim > xelab.log 2>&1
        xsim drain_sim -R > xsim.log 2>&1
    ) || { echo "xsim failed - see $work/drain/"; exit 1; }
    grep -E "RESULT|drain:" "$work/drain/xsim.log" | sed 's/^/   /'
    grep -q "RESULT PASS" "$work/drain/xsim.log" || exit 1
fi
