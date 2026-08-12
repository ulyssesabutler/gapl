import org.gradle.api.tasks.Exec
import java.util.Properties

plugins { base }

// ---- Helpers ----
fun Project.propOrEnv(prop: String, env: String, default: String? = null): String =
    (findProperty(prop) as String?)
        ?: System.getenv(env)
        ?: default
        ?: error("Missing required setting: -P$prop or env $env")

fun Project.optPropOrEnv(prop: String, env: String, default: String? = null): String? =
    (findProperty(prop) as String?) ?: System.getenv(env) ?: default

// Resolve the vendored NetFPGA checkout inside this repo
val defaultSumeFolder = rootProject.projectDir.resolve("netfpga/packet-processor").absolutePath

// ---- Core inputs ----
val vivadoSettings = propOrEnv(
    prop = "vivadoSettings",
    env  = "VIVADO_SETTINGS",
    default = "/tools/Xilinx/Vivado/2020.1/settings64.sh"
)

// Default SUME_FOLDER is the vendored subtree inside THIS repo
val sumeFolder = propOrEnv(
    prop = "sumeFolder",
    env  = "SUME_FOLDER",
    default = defaultSumeFolder
)

// Optional explicit tool paths
val xilinxPath = optPropOrEnv("xilinxPath", "XILINX_PATH", "/tools/Xilinx/Vivado/2020.1")
val vitisPath  = optPropOrEnv("vitisPath",  "VITIS_PATH",  "/tools/Xilinx/Vitis/2020.1")

// Project name within SUME projects
val nfProjectName = propOrEnv("nfProjectName", "NF_PROJECT_NAME", "reference_switch")

// Program Name
val programName = propOrEnv("programName", "PROGRAM_NAME", null)
val programVariationName = propOrEnv("programVariationName", "PROGRAM_VARIATION_NAME", null)

// GAPL: the exact part string create_project.tcl itself uses (`set device`) - solveClkWizConfig
// needs the same one, since the MMCM's valid VCO/divide range is speed-grade-specific.
val clockWizPart = propOrEnv("clockWizPart", "CLOCK_WIZ_PART", "xc7vx690t-3-ffg1761")

// GAPL: the board's fixed reference oscillator feeding clk_wiz_ip (see axi_clocking.v) - not
// something solveClkWizConfig can change, only its own multiply/divide in response to it.
val clockWizPrimInFreqMhz = propOrEnv("clockWizPrimInFreqMhz", "CLOCK_WIZ_PRIM_IN_FREQ_MHZ", "200.00")

// Log Level
val logLevel = propOrEnv("logLevel", "LOG_LEVEL", "info")

// Derived paths
val projects         = "$sumeFolder/projects"
val contribProjects  = "$sumeFolder/contrib-projects"
val ipFolder         = "$sumeFolder/lib/hw/std/cores"
val constraints      = "$sumeFolder/lib/hw/std/constraints"
val xilinxIpFolder   = "$sumeFolder/lib/hw/xilinx/cores"

// NF_DESIGN_DIR can be overridden, else derive from projects/NF_PROJECT_NAME
val nfDesignDir = optPropOrEnv("nfDesignDir", "NF_DESIGN_DIR") ?: "$projects/$nfProjectName"

// Work dir default
val tmpDir    = System.getenv("TMPDIR") ?: "/tmp"
val nfWorkDir = optPropOrEnv("nfWorkDir", "NF_WORK_DIR")
    ?: "$tmpDir/${System.getenv("USER") ?: System.getProperty("user.name")}"

// Driver/app names
val driverName   = optPropOrEnv("driverName", "DRIVER_NAME", "sume_riffa_v1_0_0")
val driverFolder = "$sumeFolder/lib/sw/std/driver/$driverName"
val appsFolder   = "$sumeFolder/lib/sw/std/apps/$driverName"

// PYTHONPATH aggregate
val pythonPath = listOf(
    ".",
    "$sumeFolder/tools/scripts/",
    "$nfDesignDir/lib/Python",
    "$sumeFolder/tools/scripts/NFTest"
).joinToString(":")

val gaplSrcRoot = layout.projectDirectory.dir("src/$programName").asFile
val configSrcRoot = layout.projectDirectory.dir("src/$programName/$programVariationName").asFile

val delayModelPath = propOrEnv("delayModelPath", "DELAY_MODEL_PATH", "delay.yaml")

// A relative path is resolved against the selected variation's own directory first (so a
// variation that wants to override the model - e.g. delaymodel's own probe variations, or a
// future variation calibrated for a different part - still can), falling back to this
// subproject's root delay.yaml (the real, measured model - see netfpga/delay.yaml's own header)
// when the variation doesn't carry one of its own. NOTE: every existing variation as of this
// change (md5/aes/cms/regex) still has its own `delay.yaml` sitting in its directory, and every
// one of those is just `default: 1` - identical to the old hardcoded uniform model - so this
// fallback does NOT yet change behavior for any of them; it only takes effect for a variation
// that doesn't create a delay.yaml of its own. Rolling the new model out to an existing variation
// is a deliberate follow-up: delete that variation's own delay.yaml so it falls through to this one.
val delayModelFile = File(delayModelPath)
    .let { candidate ->
        if (candidate.isAbsolute) {
            candidate
        } else {
            val variationSpecific = File(configSrcRoot, candidate.path)
            if (variationSpecific.exists()) variationSpecific
            else layout.projectDirectory.file(candidate.path).asFile
        }
    }

// Validate: under src, exists, and ends with .gapl
fun ensureUnder(parent: File, child: File): Boolean =
    child.canonicalPath.startsWith(parent.canonicalPath + File.separator)

val gaplTargetFile = File(gaplSrcRoot, "processor.gapl").also {
    if (!it.exists()) throw GradleException("Missing .gapl file under src/$programName (looked at ${it.absolutePath})")
}

// Output directory for generated Verilog
val gaplVerilogOut = layout.buildDirectory.dir("verilog")

fun targetVerilogName(gaplFile: File) = "GAPL" + gaplFile.nameWithoutExtension + ".v"

// Build/install location of the compiler binary
val compilerPath = project(":compiler")
    .layout.buildDirectory.file("install/gapl/bin/gapl")

// Compiler settings (retime, flatten, ...) are per-variation
val compilePropsFile = configSrcRoot.resolve("compile.properties")
val compileProps = Properties().apply {
    compilePropsFile.inputStream().use { load(it) }
}

// Test vectors (testInputs, testExpectedOutputs) exercise processor.gapl itself, which is
// shared by every variation of an application, so they live one level up from the variation
// directory. Retiming/flattening are meant to be semantics-preserving, so the same vectors
// must hold regardless of which variation is selected.
val testPropsFile = gaplSrcRoot.resolve("test.properties")
val testProps = Properties().apply {
    testPropsFile.inputStream().use { load(it) }
}

fun propString(name: String, default: String? = null): String? =
    providers.gradleProperty(name).orNull
        ?: compileProps.getProperty(name)
        ?: default

fun propBool(name: String, default: Boolean = false): Boolean =
    (providers.gradleProperty(name).orNull ?: compileProps.getProperty(name))
        ?.trim()
        ?.toBooleanStrictOrNull()
        ?: default

val testInputs = providers.gradleProperty("testInputs").orNull ?: testProps.getProperty("testInputs")!!
val testExpectedOutputs = providers.gradleProperty("testExpectedOutputs").orNull ?: testProps.getProperty("testExpectedOutputs")!!

val retime = propBool("retime", true)

// GAPL: this is the only value a person should ever need to edit to change clk_200's speed. It's
// a *request*, not a guarantee - solveClkWizConfig (below) resolves it against Vivado's own
// Clocking Wizard solver, which fails the build loudly if it's not achievable by a single MMCM
// stage. Every other place that used to need hand-editing (clk_wiz_ip's multiply/divide/jitter
// config, and the create_clock constraint) is now generated *from* the solver's real output, so
// they can't drift out of sync with each other or with the physical silicon the way they did
// before - see the netfpga clock-period workflow investigation for the incident this replaced.
//
// Per-variation like retime/flatten above, not a global gradle.properties default: different
// variations of the same design can need very different periods (an unretimed design's single
// combinational block needs far more room than a retimed one), so each variation's own
// compile.properties is what should carry its natural clock period, not one shared repo-wide
// value. Falls back to NetFPGA's traditional 10.000ns when a variation doesn't set one -
// still overridable from the command line with -PclockPeriodNs=... same as retime/flatten are.
val clockPeriodNs = propString("clockPeriodNs", "10.000")!!

val retimingClockPeriod = propString("retimingClockPeriod", "min")!!

val retimingSolver = propString("retimingSolver")
val retimingMaintainsTiming = propBool("retimingMaintainsTiming", false)

val flattenMode = propString("flatten", "recursive")!!

// Bash runner
fun bash(cmd: String) = listOf("bash", "-lc", cmd)

fun Exec.exportNetfpgaEnv() {
    environment(
        mapOf(
            "SUME_FOLDER"      to sumeFolder,
            "XILINX_PATH"      to (xilinxPath ?: ""),
            "VITIS_PATH"       to (vitisPath  ?: ""),
            "NF_PROJECT_NAME"  to nfProjectName,
            // GAPL: resolved the same way as every other setting here - -PprogramName overrides
            // gradle.properties. run.py (test/*/run.py) prefers this over its own gradle.properties
            // read for exactly that reason: a -PprogramName override on the command line otherwise
            // silently only affected which app Gradle compiled/packaged, not which app's
            // test.properties the Python test harness actually read.
            "PROGRAM_NAME"     to (programName ?: ""),
            "PROJECTS"         to projects,
            "CONTRIB_PROJECTS" to contribProjects,
            "IP_FOLDER"        to ipFolder,
            "CONSTRAINTS"      to constraints,
            "XILINX_IP_FOLDER" to xilinxIpFolder,
            "NF_DESIGN_DIR"    to nfDesignDir,
            "NF_WORK_DIR"      to nfWorkDir,
            "PYTHONPATH"       to pythonPath,
            "DRIVER_NAME"      to driverName,
            "DRIVER_FOLDER"    to driverFolder,
            "APPS_FOLDER"      to appsFolder,
        )
    )
}

// Handy debug task
tasks.register<Exec>("printEnv") {
    group = "netfpga"
    description = "Print the NetFPGA-related environment Gradle will export"
    exportNetfpgaEnv()
    commandLine(bash("""
        set -e
        echo "SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "NF_PROJECT_NAME=${'$'}NF_PROJECT_NAME"
        echo "PROJECTS=${'$'}PROJECTS"
        echo "NF_DESIGN_DIR=${'$'}NF_DESIGN_DIR"
        echo "PYTHONPATH=${'$'}PYTHONPATH"
    """.trimIndent()))
}

tasks.register("generateGaplVerilog") {
    group = "netfpga"
    description = "Compile specified *.gapl and copy wrapper.v (under src/$programName) to Verilog into build/verilog"
    dependsOn(":compiler:installDist")

    // Incremental inputs/outputs. compilePropsFile is the resolved-at-configuration-time
    // compile.properties for the selected -PprogramVariationName - it's what drives every
    // compiler flag added below (retime, flatten, retimingClockPeriod, retimingSolver,
    // retimingMaintainsTiming), and it resolves to a *different* physical file whenever the
    // variation is switched. Without it declared here, this task's only tracked input was
    // gaplTargetFile (processor.gapl itself), so switching -PprogramVariationName without
    // touching processor.gapl left Gradle believing a stale previous variation's already-built
    // build/verilog/*.v was still UP-TO-DATE - silently shipping the wrong compiled design.
    inputs.files(gaplTargetFile)
    inputs.file(compilePropsFile)
    if (retime) {
        inputs.file(delayModelFile)
    }
    outputs.dir(gaplVerilogOut)

    doLast {
        val compiler = compilerPath.get().asFile
        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
        }
        val outDir = gaplVerilogOut.get().asFile
        outDir.mkdirs()

        val verilogFile = outDir.resolve(targetVerilogName(gaplTargetFile))
        println("Compiling ${gaplTargetFile.relativeTo(gaplSrcRoot)} -> ${verilogFile.name}")

        val compilerCommand = buildList {
            add(compiler.absolutePath)
            add(gaplTargetFile.absolutePath)
            add("--output")
            add(verilogFile.absolutePath)

            if (retime) {
                if (!delayModelFile.exists())
                    throw GradleException("Delay model file not found: ${delayModelFile.absolutePath}")

                add("--retime")
                add(delayModelFile.absolutePath)

                add("--retiming-clock-period")
                add(retimingClockPeriod.lowercase())

                if (retimingSolver != null) {
                    add("--retiming-solver")
                    add(retimingSolver.lowercase())
                }
                if (retimingMaintainsTiming) { add("--retiming-maintains-timing") }
            }

            add("--flatten")
            add(flattenMode.lowercase())

            add("--log-level")
            add(logLevel.lowercase())
        }

        val err = java.io.ByteArrayOutputStream()
        println("Running: ${compilerCommand.joinToString(" ")}")
        val result = project.exec {
            isIgnoreExitValue = true
            commandLine(compilerCommand)
            errorOutput = err
            standardOutput = System.out
        }
        if (result.exitValue != 0) {
            println("❌ Failed to compile ${gaplTargetFile.relativeTo(gaplSrcRoot)}")
            println("---- Compiler Error Output ----")
            println(err.toString().trim())
            println("--------------------------------")
            throw GradleException("${gaplTargetFile.absolutePath} failed to compile")
        }
    }
}

tasks.register<Copy>("installGaplVerilog") {
    group = "netfpga"
    description = "Install generated Verilog for specified .gapl files (and wrapper.v) into \$NF_DESIGN_DIR/hw/hdl"
    dependsOn("generateGaplVerilog")

    val outDirProvider = gaplVerilogOut

    from(provider {
        outDirProvider.get().asFile.resolve(targetVerilogName(gaplTargetFile))
    })
    into(provider { file("$nfDesignDir/hw/hdl") })

    // Incremental wiring
    inputs.files(
        outDirProvider.map { it.asFile.resolve(targetVerilogName(gaplTargetFile)) },
    )
    outputs.files(
        file("$nfDesignDir/hw/hdl/${targetVerilogName(gaplTargetFile)}"),
    )
}

// GAPL: solves clk_wiz_ip's MMCM multiply/divide/jitter/phase-error configuration for
// clockPeriodNs using Vivado's own Clocking Wizard solver (solve_clk_wiz.tcl), instead of the
// hand-computed VCO arithmetic and hand-copied jitter numbers this replaced. Runs in seconds
// (an in-memory throwaway IP customization, not a real project build), so it's cheap enough to
// run on every build and catch an unachievable request immediately - Vivado's own
// "Please enter valid freq in range (X - Y)" error fails this task loudly - rather than 20+
// minutes into a real synthesis run.
val clkWizGeneratedDir = layout.buildDirectory.dir("netfpga-clk-wiz")
val clkWizConfigTcl = clkWizGeneratedDir.map { it.file("clk_wiz_config.tcl") }
val achievedPeriodFile = clkWizGeneratedDir.map { it.file("achieved_period_ns.txt") }

tasks.register<Exec>("solveClkWizConfig") {
    group = "vivado"
    description = "Solve clk_wiz_ip's MMCM config for clockPeriodNs via Vivado's own Clocking Wizard solver"

    inputs.property("clockPeriodNs", clockPeriodNs)
    inputs.property("clockWizPart", clockWizPart)
    inputs.property("clockWizPrimInFreqMhz", clockWizPrimInFreqMhz)
    outputs.file(clkWizConfigTcl)
    outputs.file(achievedPeriodFile)

    val solverScript = layout.projectDirectory.file(
        "packet-processor/projects/reference_switch/hw/tcl/solve_clk_wiz.tcl"
    )

    doFirst {
        clkWizGeneratedDir.get().asFile.mkdirs()
    }

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        vivado -mode batch -nojournal -nolog -source "${solverScript.asFile.absolutePath}" -tclargs \
          "$clockWizPart" "$clockWizPrimInFreqMhz" "$clockPeriodNs" \
          "${clkWizConfigTcl.get().asFile.absolutePath}" \
          "${achievedPeriodFile.get().asFile.absolutePath}"
    """.trimIndent()))
}

tasks.register<Copy>("installClkWizConfig") {
    group = "netfpga"
    description = "Install the solved clk_wiz_ip config into \$NF_DESIGN_DIR/hw/tcl_generated"
    dependsOn("solveClkWizConfig")

    from(clkWizConfigTcl)
    into(provider { file("$nfDesignDir/hw/tcl_generated") })

    inputs.files(clkWizConfigTcl)
    outputs.files(file("$nfDesignDir/hw/tcl_generated/clk_wiz_config.tcl"))
}

tasks.register("generateConstraints") {
    group = "vivado"
    description = "Generate XDC from template, using solveClkWizConfig's achieved period (not the raw request) for clk_200's create_clock"
    dependsOn("solveClkWizConfig")

    val templateFile = layout.projectDirectory.file("constraints/nf_sume_general.xdc.template")
    val outputFile = layout.buildDirectory.file("constraints/nf_sume_general.xdc")

    inputs.file(templateFile)
    inputs.file(achievedPeriodFile)
    outputs.file(outputFile)

    doLast {
        // GAPL: reads the achieved period from solveClkWizConfig's own output, not the raw
        // clockPeriodNs request - the two are usually equal but aren't guaranteed to be (divide
        // granularity can round the achievable frequency slightly), and it's the achieved value
        // that's physically true of the silicon, which is what the constraint must reflect.
        val achievedPeriod = achievedPeriodFile.get().asFile.readText().trim()
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(
            templateFile.asFile.readText().replace("@CLOCK_PERIOD_NS@", achievedPeriod)
        )
    }
}

tasks.register<Copy>("installConstraints") {
    group = "netfpga"
    description = "Install generate XDC from template using gradle.properties"
    dependsOn("generateConstraints")

    val outDirProvider = layout.buildDirectory.dir("constraints")

    from(provider {
        outDirProvider.get().asFile.resolve("nf_sume_general.xdc")
    })
    into(provider { file("$nfDesignDir/hw/constraints") })

    // Incremental wiring
    inputs.files(
        outDirProvider.map { it.asFile.resolve("nf_sume_general.xdc") },
    )
    outputs.files(
        file("$nfDesignDir/hw/constraints/nf_sume_general.xdc"),
    )
}

// ---- NetFPGA IP-core packaging ----
//
// Every vendored core Makefile under lib/hw/{std,contrib,xilinx}/cores/*/ is a thin `all: clean`
// wrapper around a single `vivado -mode {batch,tcl} -source <core>.tcl` call - `make` itself is
// never incremental here, it wipes and repackages every core on every invocation. Gradle instead
// invokes the same underlying Vivado scripts directly, with real inputs/outputs (each core's
// `hdl/` + its `.tcl` script as inputs, the `component.xml`/`xgui/` it packages as outputs - verified
// against an actual successful build), so these only rerun when something they depend on changed.

data class NetfpgaCoreBuild(
    val taskSuffix: String,
    val relativeDir: String,
    val tclFile: String,
    val vivadoMode: String = "batch",
    // Paths (relative to the core dir) to delete before each run. Only needed where the .tcl
    // script itself does `file copy -force <dir>/ <dest>/` to pull in a sibling core's sources:
    // Tcl's file copy does NOT merge into an already-existing destination directory - on a repeat
    // run it nests the source under it instead, colliding with what the previous run left behind
    // and erroring out. The vendored Makefiles' `clean:` targets deleted these first for exactly
    // this reason; everything else here relies on `create_project -force` for idempotency instead.
    val preCleanPaths: List<String> = emptyList(),
    // taskSuffix values of other cores (in this list, or Tcam/Cam registered separately below)
    // whose packaging must complete first, extracted by reading every core's `ipx::add_subcore`
    // calls (including the two that resolve the VLNV dynamically via `get_ipdefs`) and keeping
    // only the ones referencing another one of our own packaged cores - a real dependency graph,
    // not the Makefile's incidental line order, so genuinely-independent cores can build in
    // parallel (with `--parallel`) while the real chains still wait correctly. See git history
    // for the concrete "IP is locked ... subcore(s) not found" failure that motivated this.
    val mustRunAfterCores: List<String> = emptyList(),
)

// Mirrors packet-processor/Makefile's `sume:` target (the active, uncommented lines only)
val netfpgaStdCoreBuilds = listOf(
    NetfpgaCoreBuild("NfEndianessManager", "lib/hw/contrib/cores/nf_endianess_manager_v1_0_0", "nf_endianess_manager.tcl"),
    NetfpgaCoreBuild("FallthroughSmallFifo", "lib/hw/std/cores/fallthrough_small_fifo_v1_0_0", "fallthrough_small_fifo.tcl"),
    NetfpgaCoreBuild("AxisFifo", "lib/hw/std/cores/axis_fifo_v1_0_0", "axis_fifo.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("InputArbiter", "lib/hw/std/cores/input_arbiter_v1_0_0", "input_arbiter.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("OutputQueues", "lib/hw/std/cores/output_queues_v1_0_0", "output_queues.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    // Also depends on Tcam/Cam (xilinx:xilinx:{tcam,cam}:1.10 subcores) - wired below, once those
    // tasks exist.
    NetfpgaCoreBuild("RouterOutputPortLookup", "lib/hw/std/cores/router_output_port_lookup_v1_0_0", "router_output_port_lookup.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    // Also depends on Cam (xilinx:xilinx:cam:1.10 subcore) - wired below.
    NetfpgaCoreBuild("SwitchOutputPortLookup", "lib/hw/std/cores/switch_output_port_lookup_v1_0_1", "switch_output_port_lookup.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("SwitchLiteOutputPortLookup", "lib/hw/std/cores/switch_lite_output_port_lookup_v1_0_0", "switch_lite_output_port_lookup.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("NicOutputPortLookup", "lib/hw/std/cores/nic_output_port_lookup_v1_0_0", "nic_output_port_lookup.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("NfAxisConverter", "lib/hw/std/cores/nf_axis_converter_v1_0_0", "nf_axis_converter.tcl", mustRunAfterCores = listOf("AxisFifo", "FallthroughSmallFifo")),
    NetfpgaCoreBuild("NfRiffaDma", "lib/hw/std/cores/nf_riffa_dma_v1_0_0", "nf_riffa_dma_tcl.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
    NetfpgaCoreBuild("Barrier", "lib/hw/std/cores/barrier_v1_0_0", "barrier.tcl"),
    NetfpgaCoreBuild("AxisSimRecord", "lib/hw/std/cores/axis_sim_record_v1_0_0", "axis_sim_record.tcl"),
    NetfpgaCoreBuild("AxisSimStim", "lib/hw/std/cores/axis_sim_stim_v1_0_0", "axis_sim_stim.tcl", preCleanPaths = listOf("hdl/axis_sim_pkg")),
    NetfpgaCoreBuild("AxiSimTransactor", "lib/hw/std/cores/axi_sim_transactor_v1_0_0", "axi_sim_transactor.tcl", preCleanPaths = listOf("hdl/axis_sim_pkg")),
    NetfpgaCoreBuild("BarrierGluelogic", "lib/hw/std/cores/barrier_gluelogic_v1_0_0", "barrier_gluelogic.tcl"),
    NetfpgaCoreBuild("Identifier", "lib/hw/std/cores/identifier_v1_0_0", "nf_identifier.tcl"),
    NetfpgaCoreBuild("Nf10geAttachment", "lib/hw/std/cores/nf_10ge_attachment_v1_0_0", "nf_10ge_attachment_tcl.tcl", mustRunAfterCores = listOf("NfAxisConverter", "FallthroughSmallFifo")),
    NetfpgaCoreBuild("Nf10geInterfaceShared", "lib/hw/std/cores/nf_10ge_interface_shared_v1_0_0", "nf_10ge_interface_shared.tcl", mustRunAfterCores = listOf("NfAxisConverter", "Nf10geAttachment", "FallthroughSmallFifo")),
    NetfpgaCoreBuild("Nf10geInterface", "lib/hw/std/cores/nf_10ge_interface_v1_0_0", "nf_10ge_interface.tcl", mustRunAfterCores = listOf("NfAxisConverter", "Nf10geAttachment", "FallthroughSmallFifo")),
    NetfpgaCoreBuild("NicOutputQueues", "lib/hw/std/cores/nic_output_queues_v1_0_0", "output_queues.tcl", mustRunAfterCores = listOf("FallthroughSmallFifo")),
)

fun registerNetfpgaCoreBuildTask(build: NetfpgaCoreBuild) =
    tasks.register<Exec>("packageCore${build.taskSuffix}") {
        group = "netfpga-init"
        description = "Package ${build.relativeDir} as a Vivado IP core (${build.tclFile})"

        val coreDir = file("$sumeFolder/${build.relativeDir}")
        workingDir = coreDir
        exportNetfpgaEnv()

        // preCleanPaths (e.g. hdl/axis_sim_pkg) are deleted and then recreated by the task's own
        // tcl script every time it runs (see preCleanPaths' own doc comment) - if included in the
        // declared "hdl" input, the directory's on-disk content after execution never matches the
        // pre-execution snapshot from the run before, so Gradle can never converge to UP-TO-DATE:
        // the task would rebuild on every single invocation forever, confirmed against a real back
        // -to-back rerun. Excluding them from the input snapshot lets Gradle actually cache this.
        inputs.files(fileTree(coreDir.resolve("hdl")) {
            exclude(build.preCleanPaths.mapNotNull {
                it.removePrefix("hdl/").takeIf { relative -> relative != it }?.let { relative -> "$relative/**" }
            })
        })
        inputs.file(coreDir.resolve(build.tclFile))
        outputs.file(coreDir.resolve("component.xml"))
        outputs.dir(coreDir.resolve("xgui"))

        doFirst {
            build.preCleanPaths.forEach { delete(coreDir.resolve(it)) }
        }

        commandLine(bash("""
            set -euo pipefail
            [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
            source "$vivadoSettings"
            vivado -mode ${build.vivadoMode} -source ${build.tclFile}
        """.trimIndent()))
    }

val netfpgaStdCoreBuildTasksBySuffix = netfpgaStdCoreBuilds.associateWith { registerNetfpgaCoreBuildTask(it) }
    .mapKeys { (build, _) -> build.taskSuffix }
val netfpgaStdCoreBuildTasks = netfpgaStdCoreBuildTasksBySuffix.values.toList()

// Wire each core's real subcore dependencies (see NetfpgaCoreBuild.mustRunAfterCores) as Gradle
// ordering constraints. mustRunAfter (not dependsOn) only constrains ordering among tasks that
// are already both scheduled to run - it doesn't force a rebuild of an otherwise-up-to-date core
// just because one of its dependencies needs to rebuild, and it doesn't prevent unrelated cores
// from running in parallel under --parallel.
netfpgaStdCoreBuilds.forEach { build ->
    val task = netfpgaStdCoreBuildTasksBySuffix.getValue(build.taskSuffix)
    build.mustRunAfterCores.forEach { dep ->
        task.configure { mustRunAfter(netfpgaStdCoreBuildTasksBySuffix.getValue(dep)) }
    }
}

// lib/sw/std/hwtestlib is a two-line `cc` compile - cheap regardless, kept simple
tasks.register<Exec>("buildHwTestLib") {
    group = "netfpga-init"
    description = "Compile the NetFPGA hw-test C library (lib/sw/std/hwtestlib)"

    val dir = file("$sumeFolder/lib/sw/std/hwtestlib")
    workingDir = dir
    inputs.file(dir.resolve("sume_reg.c"))
    outputs.file(dir.resolve("libsume.so"))

    commandLine(bash("""
        set -euo pipefail
        cc -c -Wall -Werror -fPIC sume_reg.c -I../driver/sume_riffa_v1_0_0/
        cc -shared -o libsume.so sume_reg.o
    """.trimIndent()))
}

tasks.register("makeInit") {
    group = "netfpga"
    description = "Package every NetFPGA std/contrib IP core and the hw-test C library " +
        "(each task only rebuilds when its own sources changed)"
    dependsOn(netfpgaStdCoreBuildTasks)
    dependsOn("buildHwTestLib")
}

// ---- Xilinx CAM/TCAM IPs ----
//
// These require a vendor zip (xapp1151_Param_CAM.zip) placed manually - see README.md - which is
// then extracted once into hdl/vhdl/{tcam,cam}/. That extraction is itself now a tracked Gradle
// task (inputs = the zip, outputs = the extracted .vhd sources), so placing the zip and running
// this is a true one-time step: rerunning later is a no-op unless the zip actually changes.

val xappZipName = "xapp1151_Param_CAM.zip"

fun registerXappExtractTask(name: String, coreDirName: String): TaskProvider<Exec> {
    val coreDir = file("$xilinxIpFolder/$coreDirName")
    val zipFile = coreDir.resolve(xappZipName)
    val vhdlOutDir = coreDir.resolve("hdl/vhdl/$name")

    return tasks.register<Exec>("extract${name.replaceFirstChar { it.uppercase() }}VendorSources") {
        group = "netfpga-init"
        description = "Extract vendor $name VHDL sources from $xappZipName"
        workingDir = coreDir

        inputs.file(zipFile)
        outputs.dir(vhdlOutDir)

        doFirst {
            if (!zipFile.exists()) {
                throw GradleException(
                    "Missing $xappZipName in ${coreDir.absolutePath} - see netfpga/README.md for " +
                        "how to obtain it (a one-time manual download due to Xilinx licensing)."
                )
            }
        }

        commandLine(bash("""
            set -euo pipefail
            rm -rf xapp1151_cam_v1_1
            unzip -o $xappZipName
            bash ./scripts/run_update_lib.sh
            cp -f ./xapp1151_cam_v1_1/src/vhdl/*.vhd ./hdl/vhdl/$name/
        """.trimIndent()))
    }
}

val extractTcamVendorSources = registerXappExtractTask("tcam", "tcam_v1_1_0")
val extractCamVendorSources = registerXappExtractTask("cam", "cam_v1_1_0")

val packageCoreTcam = registerNetfpgaCoreBuildTask(
    NetfpgaCoreBuild("Tcam", "lib/hw/xilinx/cores/tcam_v1_1_0", "tcam.tcl", vivadoMode = "tcl")
).apply { configure { dependsOn(extractTcamVendorSources) } }

val packageCoreCam = registerNetfpgaCoreBuildTask(
    NetfpgaCoreBuild("Cam", "lib/hw/xilinx/cores/cam_v1_1_0", "cam.tcl", vivadoMode = "tcl")
).apply { configure { dependsOn(extractCamVendorSources) } }

// router_output_port_lookup and switch_output_port_lookup declare xilinx:xilinx:{tcam,cam}:1.10
// as subcores (matching Tcam/Cam's own registered VLNV) - same ordering hazard as the std-core
// dependencies above, just crossing from makeIPs into makeInit's task set.
netfpgaStdCoreBuildTasksBySuffix.getValue("RouterOutputPortLookup").configure { mustRunAfter(packageCoreTcam, packageCoreCam) }
netfpgaStdCoreBuildTasksBySuffix.getValue("SwitchOutputPortLookup").configure { mustRunAfter(packageCoreCam) }

tasks.register("makeIPs") {
    group = "netfpga"
    description = "Package the Xilinx CAM/TCAM IP cores from the vendor zip " +
        "(only re-extracts/rebuilds what's stale - safe to depend on from every build)"
    dependsOn(packageCoreTcam, packageCoreCam)
}

// ---- GAPL kernel checkpointed IP ----
//
// Packaged like every other core, but with Vivado's synthesis checkpoint left enabled (see
// create_project.tcl and lib/hw/contrib/cores/gapl_kernel_v1_0_0/gapl_kernel.tcl for why). This
// task's inputs are the freshly-installed GAPLprocessor.v (varies per application/variation) and
// the static gapl_wrapper.v, so it - and therefore the expensive synthesis checkpoint it produces
// - only reruns when the selected application actually changes.
val gaplKernelCoreDir = file("$sumeFolder/lib/hw/contrib/cores/gapl_kernel_v1_0_0")

tasks.register<Exec>("packageCoreGaplKernel") {
    group = "netfpga-init"
    description = "Package the compiled GAPL kernel as its own Vivado IP core (synthesis checkpoint enabled)"
    dependsOn("installGaplVerilog")
    // axis_queue.v (one of the copied util deps below) instantiates fallthrough_small_fifo as a
    // subcore reference (see gapl_kernel.tcl) - same ordering hazard as the std-core dependency
    // graph above, needs that core's component.xml already registered in the IP catalog.
    mustRunAfter(netfpgaStdCoreBuildTasksBySuffix.getValue("FallthroughSmallFifo"))

    val installedGaplProcessor = file("$nfDesignDir/hw/hdl/GAPLprocessor.v")
    val installedGaplWrapper = file("$nfDesignDir/hw/hdl/gapl_wrapper.v")
    // gapl_wrapper.v isn't actually a self-contained leaf - it internally instantiates these
    // static NetFPGA infra utility modules (found the hard way: an OOC synthesis run for this IP
    // is an isolated compile scope containing only what's copied into this core's own hdl/, so
    // without these, Vivado can't find them - "module 'axis_pad_output' not found"). They're
    // static (not per-application), so no need to reinstall them each time, just keep in sync
    // with what gapl_wrapper.v actually instantiates.
    val staticUtilDeps = listOf(
        "util/axis/axis_pad_output.v",
        "util/axis/axis_mutual_exclusion.v",
        "util/axis/axis_queue.v",
        "util/processor_controller.v",
        "util/reverse_bytes.v",
    )
    val coreHdlDir = gaplKernelCoreDir.resolve("hdl")

    workingDir = gaplKernelCoreDir
    exportNetfpgaEnv()

    inputs.file(installedGaplProcessor)
    inputs.file(installedGaplWrapper)
    inputs.files(staticUtilDeps.map { file("$nfDesignDir/hw/hdl/$it") })
    inputs.file(gaplKernelCoreDir.resolve("gapl_kernel.tcl"))
    outputs.file(gaplKernelCoreDir.resolve("component.xml"))
    outputs.dir(gaplKernelCoreDir.resolve("xgui"))

    doFirst {
        coreHdlDir.mkdirs()
        installedGaplProcessor.copyTo(coreHdlDir.resolve("GAPLprocessor.v"), overwrite = true)
        installedGaplWrapper.copyTo(coreHdlDir.resolve("gapl_wrapper.v"), overwrite = true)
        staticUtilDeps.forEach {
            file("$nfDesignDir/hw/hdl/$it").copyTo(coreHdlDir.resolve(File(it).name), overwrite = true)
        }
    }

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        vivado -mode batch -source gapl_kernel.tcl
    """.trimIndent()))
}

// The static NetFPGA "shell" (everything except the selected GAPL application) synthesized on its
// own via create_project+run_synth, deliberately bypassing the project-level Makefile's `all: clean`
// target - that target unconditionally deletes hw/project/ before rebuilding, which is why nothing
// about Vivado's own Design Runs staleness tracking ever got a chance to help even after Phase 1's
// checkpointed-IP work: there was never a persistent project for it to apply to. Verified directly
// against real Vivado that once hw/project/ is left in place, `synth_1`'s completed status persists
// correctly across separate `vivado -mode batch` sessions, so this task only needs to rerun
// launch_runs synth_1's ~12 min of work when something in the static shell actually changed - a pure
// GAPL application switch leaves every input below untouched, so Gradle skips this task entirely.
//
// Deliberately excludes GAPLprocessor.v from the hdl inputs (the one file that varies per
// application) and depends on packageCoreGaplKernel for *ordering* only (dependsOn, not
// inputs.file/inputs.dir) - not its component.xml content - since top-level shell synthesis only
// needs the GAPL kernel IP's port interface (stable across applications) to exist in the IP catalog,
// not its internal logic (which stays a synthesis-checkpointed black box until makeBuild's
// run_impl-time link_design stitches in whichever application is currently installed).
tasks.register<Exec>("makeSynthShell") {
    group = "netfpga"
    description = "Synthesize the static NetFPGA shell (create_project + run_synth), skipped when nothing static changed"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    dependsOn("installGaplVerilog", "installConstraints", "installClkWizConfig", "makeInit", "makeIPs", "packageCoreGaplKernel")

    inputs.files(fileTree(file("$nfDesignDir/hw/hdl")) { exclude("GAPLprocessor.v") })
    inputs.dir(file("$nfDesignDir/hw/constraints"))
    inputs.dir(file("$nfDesignDir/hw/tcl"))
    inputs.file(file("$nfDesignDir/hw/tcl_generated/clk_wiz_config.tcl"))
    outputs.file(file("$nfDesignDir/hw/project/$nfProjectName.runs/synth_1/top.dcp"))

    // GAPL: clk_wiz_ip's multiply/divide config is only ever read once, when create_project.tcl's
    // `create_ip`/`generate_target` first creates it - the Makefile's own create_project target
    // just checks whether project/$PROJ.xpr already exists and silently no-ops if so, so a clock
    // period change alone would otherwise never actually reach the physical MMCM on an existing
    // project (confirmed directly: this bit an earlier session, requiring a manually-remembered
    // `make clean`). Stamp the installed clk_wiz config's hash inside the project directory and
    // wipe it before rebuilding whenever that hash no longer matches, so this can't be forgotten.
    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        [ -d "${'$'}NF_DESIGN_DIR" ] || { echo "NF_DESIGN_DIR not found: ${'$'}NF_DESIGN_DIR" >&2; exit 2; }
        echo "[netfpga] SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "[netfpga] NF_DESIGN_DIR=${'$'}NF_DESIGN_DIR"

        clk_wiz_config="${'$'}NF_DESIGN_DIR/hw/tcl_generated/clk_wiz_config.tcl"
        stamp_file="${'$'}NF_DESIGN_DIR/hw/project/.gapl_clk_wiz_stamp"
        if [ -d "${'$'}NF_DESIGN_DIR/hw/project" ]; then
            new_hash="${'$'}(sha256sum "${'$'}clk_wiz_config" | cut -d' ' -f1)"
            old_hash="${'$'}(cat "${'$'}stamp_file" 2>/dev/null || true)"
            if [ "${'$'}new_hash" != "${'$'}old_hash" ]; then
                echo "[netfpga] clk_wiz_ip config changed since project/ was created - recreating the project"
                rm -rf "${'$'}NF_DESIGN_DIR/hw/project"
            fi
        fi

        make -C "${'$'}NF_DESIGN_DIR/hw" identifier create_project run_synth

        mkdir -p "${'$'}NF_DESIGN_DIR/hw/project"
        sha256sum "${'$'}clk_wiz_config" | cut -d' ' -f1 > "${'$'}stamp_file"
    """.trimIndent()))
}

// :netfpga:build -> everything downstream of the static shell: refresh the GAPL kernel's own OOC
// checkpoint, implement, and export - the part that must rerun on every application switch, but no
// longer pays synth_1's ~12 min since makeSynthShell above only reruns when the shell itself changed.
// Deliberately calls the hw/-level and sw/-level make targets directly (run_impl, export_to_sdk,
// project, load_elf) rather than the project-level Makefile's `all`, to avoid its `clean`
// prerequisite - see makeSynthShell's comment above for why that matters. `all` itself is left
// untouched for anyone who wants a guaranteed from-scratch build.
tasks.register<Exec>("makeBuild") {
    group = "netfpga"
    description = "Refresh the GAPL kernel checkpoint, implement, and export - reuses the shell synthesized by makeSynthShell"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    dependsOn("makeSynthShell")

    // Gradle-level incrementality independent of Vivado's own internal run-staleness tracking
    // (which turned out unreliable in practice for a true no-op across separate batch invocations -
    // see brainstorming/todo.md for the investigation). This can't make Vivado itself skip
    // resynthesizing when something real changes, and it can't see any Vivado-internal staleness
    // reason that isn't reflected in these files - but it does guarantee an instant, true no-op at
    // the Gradle layer for repeated builds where nothing tracked here changed.
    inputs.file(gaplKernelCoreDir.resolve("component.xml"))
    // run_impl.tcl/load_elf.tcl/export_hardware.tcl aren't rerun by makeSynthShell (that task only
    // tracks them to decide whether the *shell* needs resynthesizing) - track them here too, or
    // editing this step's own Tcl wouldn't invalidate this task's cached result.
    inputs.dir(file("$nfDesignDir/hw/tcl"))
    // GAPL: makeSynthShell's own output (synth_1/top.dcp) wasn't tracked here, so a change that
    // forces makeSynthShell to rerun without touching component.xml or hw/tcl/ (e.g. clockPeriodNs,
    // which flows into clk_wiz_config.tcl and makeSynthShell's own project-recreation check) left
    // this task believing nothing relevant had changed - confirmed directly: a clock-period-only
    // change correctly forced a full shell resynthesis, but makeBuild then reported UP-TO-DATE and
    // silently skipped run_impl entirely, leaving bitfiles/$nfProjectName.bit stale against the new
    // clock domain with no error or warning.
    inputs.file(file("$nfDesignDir/hw/project/$nfProjectName.runs/synth_1/top.dcp"))
    outputs.file(file("$nfDesignDir/bitfiles/$nfProjectName.bit"))

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        [ -d "${'$'}NF_DESIGN_DIR" ] || { echo "NF_DESIGN_DIR not found: ${'$'}NF_DESIGN_DIR" >&2; exit 2; }
        echo "[netfpga] SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "[netfpga] NF_DESIGN_DIR=${'$'}NF_DESIGN_DIR"
        make -C "${'$'}NF_DESIGN_DIR/hw" identifier
        make -C "${'$'}NF_DESIGN_DIR/hw" run_impl
        make -C "${'$'}NF_DESIGN_DIR/hw" export_to_sdk
        make -C "${'$'}NF_DESIGN_DIR/sw/embedded" project
        make -C "${'$'}NF_DESIGN_DIR/hw" load_elf
    """.trimIndent()))
}

tasks.register<Exec>("runSimulation") {
    group = "netfpga"
    description = "Run tools/scripts/nf_test.py sim with NetFPGA env and Vivado"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    // reference_switch_sim.tcl reads hw/constraints/nf_sume_general.xdc into a constraints
    // fileset just like create_project.tcl does, so simulation needs it installed too.
    // packageCoreGaplKernel: reference_switch_sim.tcl now create_ips gapl_kernel_ip from
    // lib/hw/contrib/cores/gapl_kernel_v1_0_0 (see that file) just like create_project.tcl does -
    // without this dependency, a pure application switch could simulate a stale previously-packaged
    // kernel instead of the currently-installed one.
    // installClkWizConfig: reference_switch_sim.tcl also sources tcl_generated/clk_wiz_config.tcl,
    // same as create_project.tcl, so simulation's clk_wiz_ip can't drift from the real one either.
    dependsOn("installGaplVerilog", "installConstraints", "installClkWizConfig", "makeInit", "makeIPs", "packageCoreGaplKernel")

    // Allow overrides: -Pmajor=..., -Pminor=..., -Pgui=false
    val major = (findProperty("netfpgaSimTestMajor") as String?) ?: "simple"
    val minor = (findProperty("netfpgaSimTestMinor") as String?) ?: "padded"
    val gui   = ((findProperty("netfpgaSimTestGui") as String?) ?: "true").toBoolean()

    val guiFlag = if (gui) "--gui" else ""

    commandLine(listOf("bash", "-lc", """
        set -euo pipefail

        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"

        # Resolve script path purely in bash (avoid Kotlin ${'$'} escaping headaches)
        script_path="${'$'}SUME_FOLDER/tools/scripts/nf_test.py"
        script_dir="${'$'}SUME_FOLDER/tools/scripts"

        [ -f "${'$'}script_path" ] || { echo "ERROR: ${'$'}script_path not found" >&2; exit 3; }

        echo "[netfpga:runSimulation] SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "[netfpga:runSimulation] PYTHONPATH=${'$'}PYTHONPATH"
        echo "[netfpga:runSimulation] Running: ./nf_test.py sim --major $major --minor $minor $guiFlag"

        cd "${'$'}script_dir"
        if [ -x "./nf_test.py" ]; then
          ./nf_test.py sim --major "$major" --minor "$minor" $guiFlag
        else
          python3 ./nf_test.py sim --major "$major" --minor "$minor" $guiFlag
        fi
    """.trimIndent()))
}

tasks.register<Delete>("uninstallGaplVerilog") {
    group = "netfpga"
    description = "Remove Verilog installed from -PgaplSources under \$NF_DESIGN_DIR/hw/hdl"

    doFirst {
        val nfHdlDir = file("$nfDesignDir/hw/hdl")
        if (!nfHdlDir.exists()) {
            println("[uninstallGaplVerilog] Skipping: $nfHdlDir does not exist")
            return@doFirst
        }

        val processorInstalled = nfHdlDir.resolve(targetVerilogName(gaplTargetFile))

        listOf(processorInstalled).forEach { f ->
            if (f.exists()) {
                println("[uninstallGaplVerilog] Deleting ${f.relativeToOrSelf(nfHdlDir)}")
                delete(f)
            }
        }
    }
}

tasks.register<Delete>("uninstallConstraints") {
    group = "netfpga"
    description = "Remove constraints installed"

    doFirst {
        val constraintsDir = file("$nfDesignDir/hw/constraints")
        if (!constraintsDir.exists()) {
            println("[uninstallGaplVerilog] Skipping: $constraintsDir does not exist")
            return@doFirst
        }

        val processorInstalled = constraintsDir.resolve("nf_sume_general.xdc")

        listOf(processorInstalled).forEach { f ->
            if (f.exists()) {
                println("[uninstallGaplVerilog] Deleting ${f.relativeToOrSelf(constraintsDir)}")
                delete(f)
            }
        }
    }
}

tasks.register<Delete>("uninstallClkWizConfig") {
    group = "netfpga"
    description = "Remove the installed clk_wiz_ip config"

    doFirst {
        val installed = file("$nfDesignDir/hw/tcl_generated/clk_wiz_config.tcl")
        if (installed.exists()) {
            println("[uninstallClkWizConfig] Deleting ${installed.relativeToOrSelf(file(nfDesignDir))}")
            delete(installed)
        }
    }
}

// :netfpga:clean -> make clean in $NF_DESIGN_DIR
tasks.register<Exec>("makeClean") {
    group = "netfpga"
    description = "Run make clean in \$NF_DESIGN_DIR after sourcing Vivado"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        [ -d "${'$'}NF_DESIGN_DIR" ] || { echo "NF_DESIGN_DIR not found: ${'$'}NF_DESIGN_DIR" >&2; exit 2; }
        make -s -C "${'$'}SUME_FOLDER" clean
        make -s -C "${'$'}NF_DESIGN_DIR" clean
        # GAPL: run_impl.tcl's routed-checkpoint cache for incremental_checkpoint reuse - lives one
        # level above hw/ (see run_impl.tcl's incremental_dcp_dir), so neither vendored Makefile
        # target above (which only touch hw/ and test/) ever removes it.
        rm -rf "${'$'}NF_DESIGN_DIR/incremental"
    """.trimIndent()))
}

val verilatorBin = optPropOrEnv("verilator", "VERILATOR", "verilator")!!

val kernelTestDir = layout.projectDirectory.dir("kernel-test").asFile
val kernelTestMain = kernelTestDir.resolve("test.cpp")

fun inferVerilatorTopFromMain(main: File): String {
    if (!main.exists()) throw GradleException("kernel-test test.cpp not found at ${main.absolutePath}")
    val text = main.readText()
    val m = Regex("#include\\s+\\\"V([A-Za-z_][A-Za-z0-9_]*)\\.h\\\"").find(text)
    return m?.groupValues?.get(1)
        ?: throw GradleException(
            "Could not infer Verilator top module from ${main.absolutePath}. " +
                "Expected an include like #include \"V<top>.h\". " +
                "Pass -PverilatorTop=<top-module> to override."
        )
}

val kernelTestCppSources = fileTree(kernelTestDir) {
    include("**/*.cpp")
}.files.sortedBy { it.absolutePath }

val kernelTestHeaders = fileTree(kernelTestDir) {
    include("**/*.h", "**/*.hpp")
}.files.sortedBy { it.absolutePath }

val verilatorKernelOutDir = layout.buildDirectory.dir("verilator/kernel-test")
val verilatorKernelExe = verilatorKernelOutDir.map { it.asFile.resolve("kernel_test") }

tasks.register<Exec>("buildKernelTest") {
    group = "verilator"
    description = "Build kernel-test Verilator executable from generated GAPL Verilog + C++ wrapper"
    dependsOn("generateGaplVerilog")

    val vProcProvider = gaplVerilogOut.map { it.asFile.resolve(targetVerilogName(gaplTargetFile)) }

    inputs.files(vProcProvider)
    inputs.files(kernelTestCppSources)
    inputs.files(kernelTestHeaders)
    outputs.file(verilatorKernelExe)

    doFirst {
        val outDir = verilatorKernelOutDir.get().asFile
        outDir.mkdirs()

        val top = "packet_body_processor"

        val vProc = vProcProvider.get()

        val cppArgs = kernelTestCppSources.joinToString(" ") { "\"${it.absolutePath}\"" }
        val incDirs = listOf(
            kernelTestDir,
            kernelTestDir.resolve("util")
        ).filter { it.exists() }
            .joinToString(" ") { "-I\\\"${it.absolutePath}\\\"" }

        commandLine(bash("""
            set -euo pipefail

            "$verilatorBin" --version

            # Build into: $outDir
            "$verilatorBin" -Wall -Wno-DECLFILENAME -Wno-UNUSEDSIGNAL --trace --cc \
              --top-module "$top" \
              --Mdir "$outDir" \
              "$vProc" \
              --exe $cppArgs \
              -CFLAGS "-std=c++17 $incDirs" \
              --build -j 0 \
              -o kernel_test
        """.trimIndent()))
    }
}

tasks.register<Exec>("runKernelTest") {
    group = "verilator"
    description = "Run kernel-test executable built with Verilator"
    dependsOn("buildKernelTest")
    outputs.upToDateWhen { false } // always run

    doFirst {
        val outDir = verilatorKernelOutDir.get().asFile
        val exe = verilatorKernelExe.get()
        if (!exe.exists()) throw GradleException("kernel-test executable not found at ${exe.absolutePath}")

        // Read properties at execution time (so -P... works reliably)
        val testInputsProp = testInputs.trim()
        val testExpectedProp = testExpectedOutputs.trim()

        fun splitCsv(s: String): List<String> =
            s.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        val inputs = splitCsv(testInputsProp)
        val expected = splitCsv(testExpectedProp)

        // Waveform output (VCD, since --trace)
        val waveFile = layout.buildDirectory
            .file("verilator/kernel-test/kernel_test.vcd")
            .get().asFile
        waveFile.parentFile.mkdirs()

        // Build argv: -i <...> ... -o <...> ... -w <file>
        val args = mutableListOf<String>()
        inputs.forEach { args += listOf("-i", it) }
        expected.forEach { args += listOf("-o", it) }
        args += listOf("-w", waveFile.absolutePath)

        workingDir = outDir
        commandLine(listOf(exe.absolutePath) + args)
    }
}

// simengine counterpart to buildKernelTest/runKernelTest above: runs the SAME test.properties
// packet vectors against packet_body_processor directly through simengine's Engine, bypassing
// Verilog/Verilator (and the compiler entirely - it reads gaplTargetFile's source directly, not
// gaplVerilogOut). Only -PprogramName matters here, not -PprogramVariationName: retime/flatten/
// clockPeriodNs are all compiler-only settings a pre-compile semantic check has no use for, and
// every variation of a given application shares the exact same processor.gapl source.
val simKernelTestBinary = project(":netfpga:sim-kernel-test")
    .layout.buildDirectory.file("install/sim-kernel-test/bin/sim-kernel-test")

tasks.register<Exec>("runSimKernelTest") {
    group = "simengine"
    description = "Run kernel-test's packet vectors against packet_body_processor directly " +
        "through simengine, no Verilog/Verilator involved"
    dependsOn(":netfpga:sim-kernel-test:installDist")
    outputs.upToDateWhen { false } // always run

    doFirst {
        val exe = simKernelTestBinary.get().asFile
        if (!exe.exists()) throw GradleException("sim-kernel-test executable not found at ${exe.absolutePath}")

        fun splitCsv(s: String): List<String> =
            s.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        val inputs = splitCsv(testInputs.trim())
        val expected = splitCsv(testExpectedOutputs.trim())

        val waveFile = layout.buildDirectory
            .file("simKernelTest/sim_kernel_test.vcd")
            .get().asFile
        waveFile.parentFile.mkdirs()

        val args = mutableListOf("-f", gaplTargetFile.absolutePath)
        inputs.forEach { args += listOf("-i", it) }
        expected.forEach { args += listOf("-o", it) }
        args += listOf("-w", waveFile.absolutePath)

        commandLine(listOf(exe.absolutePath) + args)
    }
}

// Wire lifecycle
tasks.named("build") {
    dependsOn("makeBuild")
}
tasks.named("clean") {
    dependsOn("uninstallGaplVerilog")
    dependsOn("uninstallConstraints")
    dependsOn("uninstallClkWizConfig")
    dependsOn("makeClean")
}

tasks.register<Exec>("programFPGA") {
    group = "vivado"
    description = "Program the FPGA with the built bitstream via Vivado batch + Tcl"
    dependsOn("build")

    val bitfile = layout.projectDirectory.file("packet-processor/projects/reference_switch/bitfiles/reference_switch.bit")

    inputs.file(bitfile)
    // Deliberately always reprogram rather than trying to detect whether the board already has
    // this bitstream loaded - FPGA configuration here is volatile SRAM loaded over JTAG (not
    // flashed to nonvolatile memory), so it has no persistent identity Gradle could compare
    // against, and the design has no build-ID/version register software could read back either.
    // A stale "already flashed" skip (e.g. after a power cycle, a host reboot, or someone else
    // reprogramming the shared board via the Vivado GUI) would silently run hardware tests
    // against outdated logic with no error - far worse than the cost of an unconditional reflash.
    outputs.upToDateWhen { false }

    commandLine(
        "bash", "-lc",
        """
        set -euo pipefail

        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"

        vivado -mode batch -nojournal -nolog \
          -source tcl/program.tcl \
          -tclargs "${bitfile.asFile.absolutePath}"
        """.trimIndent()
    )
}

tasks.register<Exec>("rebuildAndTest") {
    group = "build"
    description = "Hacky sequential: ./gradlew clean && runKernelTest && runSimulation && :hw-test:runTest"

    workingDir = rootProject.rootDir

    // Linux/macOS
    // makeInit/makeIPs are no longer separate manual steps - :netfpga:build and :netfpga:runSimulation
    // now dependOn them directly and only rebuild what's actually stale. Building and programming the
    // FPGA are likewise no longer separate explicit steps here - :netfpga:hw-test:runTest now
    // dependsOn :netfpga:programFPGA dependsOn :netfpga:build, so running it alone pulls both in;
    // calling them explicitly too would just reprogram the board twice in one invocation.
    commandLine("bash", "-lc", """
        set -euo pipefail
        ./gradlew clean
        ./gradlew :netfpga:runKernelTest
        ./gradlew :netfpga:runSimulation
        ./gradlew :netfpga:hw-test:runTest
    """.trimIndent())

    // This kind of task is basically never up-to-date in a meaningful way:
    outputs.upToDateWhen { false }
}
