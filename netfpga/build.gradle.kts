import org.gradle.api.tasks.Exec

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

val gaplSrcRoot = layout.projectDirectory.dir("src").asFile

// Comma-separated list of .gapl files relative to netfpga/src/, e.g. "foo.gapl,subdir/bar.gapl"
val gaplSourcesProp = (findProperty("gaplSources") as String?)
    ?: throw GradleException(
        "Missing -PgaplSources. Provide a comma-separated list of .gapl files relative to netfpga/src/, e.g. " +
                "-PgaplSources=my_top.gapl,subdir/alu.gapl"
    )

val gaplTargetRelPaths: List<String> = gaplSourcesProp
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }

// Validate: under src, exists, and ends with .gapl
fun ensureUnder(parent: File, child: File): Boolean =
    child.canonicalPath.startsWith(parent.canonicalPath + File.separator)

val gaplTargetFiles: List<File> = gaplTargetRelPaths.map { rel ->
    val f = File(gaplSrcRoot, rel)
    when {
        !rel.endsWith(".gapl") -> throw GradleException("Target must be a .gapl file: $rel")
        !ensureUnder(gaplSrcRoot, f) -> throw GradleException("Target not under src/: $rel -> ${f.absolutePath}")
        !f.exists() -> throw GradleException("Missing .gapl file under src/: $rel (looked at ${f.absolutePath})")
        else -> f
    }
}


// Output directory for generated Verilog
val gaplVerilogOut = layout.buildDirectory.dir("verilog")

fun targetVerilogName(gaplFile: File) = "GAPL" + gaplFile.nameWithoutExtension + ".v"

// Build/install location of the compiler binary
val compilerPath = project(":compiler")
    .layout.buildDirectory.file("install/gapl/bin/gapl")

// Bash runner
fun bash(cmd: String) = listOf("bash", "-lc", cmd)

fun Exec.exportNetfpgaEnv() {
    environment(
        mapOf(
            "SUME_FOLDER"      to sumeFolder,
            "XILINX_PATH"      to (xilinxPath ?: ""),
            "VITIS_PATH"       to (vitisPath  ?: ""),
            "NF_PROJECT_NAME"  to nfProjectName,
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
    description = "Compile specified *.gapl (under src/) to Verilog into build/verilog"
    dependsOn(":compiler:installDist")

    // Incremental inputs/outputs
    inputs.files(gaplTargetFiles)
    outputs.dir(gaplVerilogOut)

    doLast {
        val compiler = compilerPath.get().asFile
        if (!compiler.exists()) {
            throw GradleException("GAPL compiler not found at $compiler")
        }
        val outDir = gaplVerilogOut.get().asFile
        outDir.mkdirs()

        var hasFailure = false
        gaplTargetFiles.forEach { gapl ->
            val verilogFile = outDir.resolve(targetVerilogName(gapl))
            println("Compiling ${gapl.relativeTo(gaplSrcRoot)} -> ${verilogFile.name}")

            val err = java.io.ByteArrayOutputStream()
            val result = project.exec {
                isIgnoreExitValue = true
                commandLine(compiler.absolutePath, "-i", gapl.absolutePath, "-o", verilogFile.absolutePath)
                errorOutput = err
                standardOutput = System.out
            }
            if (result.exitValue != 0) {
                hasFailure = true
                println("❌ Failed to compile ${gapl.relativeTo(gaplSrcRoot)}")
                println("---- Compiler Error Output ----")
                println(err.toString().trim())
                println("--------------------------------")
            }
        }
        if (hasFailure) throw GradleException("One or more specified .gapl files failed to compile")
    }
}

tasks.register<Copy>("installGaplVerilog") {
    group = "netfpga"
    description = "Install generated Verilog for specified .gapl files into \$NF_DESIGN_DIR/hw/hdl"
    dependsOn("generateGaplVerilog")

    // Copy only the produced .v for the requested targets
    val outDirProvider = gaplVerilogOut
    from(provider {
        val outDir = outDirProvider.get().asFile
        gaplTargetFiles.map { outDir.resolve(targetVerilogName(it)) }
    })
    into(provider { file("$nfDesignDir/hw/hdl") })

    // Incremental wiring
    inputs.files(gaplTargetFiles)
    outputs.files(provider {
        gaplTargetFiles.map { file("$nfDesignDir/hw/hdl/${targetVerilogName(it)}") }
    })
}

// :netfpga:makeInit ---
// Runs `make` in $SUME_FOLDER (one-time; guarded by a stamp file)
tasks.register<Exec>("makeInit") {
    group = "netfpga"
    description = "Initialize NetFPGA project by running make in \$SUME_FOLDER"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    dependsOn("installGaplVerilog")

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"

        [ -d "${'$'}SUME_FOLDER" ] || { echo "SUME_FOLDER not found: ${'$'}SUME_FOLDER" >&2; exit 2; }
        echo "[netfpga:init] SUME_FOLDER=${'$'}SUME_FOLDER"
        make -C "${'$'}SUME_FOLDER"
    """.trimIndent()))
}

// Build Xilinx CAM/TCAM IPs locally
tasks.register<Exec>("makeIPs") {
    group = "netfpga"
    description = "Check required ZIPs and build CAM/TCAM IPs in xilinx cores"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()

    // Resolve the two IP directories under the vendored SUME folder
    val ipRoot = xilinxIpFolder // e.g. $SUME_FOLDER/lib/hw/xilinx/cores
    val tcamDir = "$ipRoot/tcam_v1_1_0"
    val camDir  = "$ipRoot/cam_v1_1_0"
    val requiredZip = "xapp1151_Param_CAM.zip"

    commandLine(bash("""
        set -euo pipefail

        # Source Vivado environment
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"

        echo "[netfpga:makeIPs] SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "[netfpga:makeIPs] IP root: $ipRoot"

        # Ensure dirs exist
        for d in "$tcamDir" "$camDir"; do
          [ -d "${'$'}d" ] || { echo "ERROR: IP directory not found: ${'$'}d" >&2; exit 3; }
        done

        # Verify required ZIP exists in both
        for d in "$tcamDir" "$camDir"; do
          if [ ! -f "${'$'}d/$requiredZip" ]; then
            echo "ERROR: Missing ${requiredZip} in ${'$'}d" >&2
            echo "Please place '${requiredZip}' into:" >&2
            echo "  $tcamDir" >&2
            echo "  $camDir"  >&2
            exit 4
          fi
        done

        # Build sequence for each: make, make sim, make
        for d in "$tcamDir" "$camDir"; do
          echo "[netfpga:makeIPs] Building in ${'$'}d"
          make -C "${'$'}d" update
          make -C "${'$'}d" sim
          make -C "${'$'}d"
        done

        echo "[netfpga:makeIPs] Done."
    """.trimIndent()))
}

tasks.register<Exec>("remakeIPs") {
    group = "netfpga"
    description = "Check required ZIPs and build CAM/TCAM IPs in xilinx cores"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()

    // Resolve the two IP directories under the vendored SUME folder
    val ipRoot = xilinxIpFolder // e.g. $SUME_FOLDER/lib/hw/xilinx/cores
    val tcamDir = "$ipRoot/tcam_v1_1_0"
    val camDir  = "$ipRoot/cam_v1_1_0"

    commandLine(bash("""
        set -euo pipefail

        # Source Vivado environment
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"

        # Ensure dirs exist
        for d in "$tcamDir" "$camDir"; do
          [ -d "${'$'}d" ] || { echo "ERROR: IP directory not found: ${'$'}d" >&2; exit 3; }
        done

        # Build sequence for each: make, make sim, make
        for d in "$tcamDir" "$camDir"; do
          echo "[netfpga:makeIPs] Building in ${'$'}d"
          make -C "${'$'}d"
        done
    """.trimIndent()))
}


// :netfpga:build -> make in $NF_DESIGN_DIR after sourcing Vivado
tasks.register<Exec>("makeBuild") {
    group = "netfpga"
    description = "Run make in \$NF_DESIGN_DIR after sourcing Vivado"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    dependsOn("installGaplVerilog")

    commandLine(bash("""
        set -euo pipefail
        [ -f "$vivadoSettings" ] || { echo "Vivado settings not found: $vivadoSettings" >&2; exit 2; }
        source "$vivadoSettings"
        [ -d "${'$'}NF_DESIGN_DIR" ] || { echo "NF_DESIGN_DIR not found: ${'$'}NF_DESIGN_DIR" >&2; exit 2; }
        echo "[netfpga] SUME_FOLDER=${'$'}SUME_FOLDER"
        echo "[netfpga] NF_DESIGN_DIR=${'$'}NF_DESIGN_DIR"
        make -C "${'$'}NF_DESIGN_DIR"
    """.trimIndent()))
}

tasks.register<Exec>("runSimulation") {
    group = "netfpga"
    description = "Run tools/scripts/nf_test.py sim with NetFPGA env and Vivado"
    workingDir = rootProject.projectDir
    exportNetfpgaEnv()
    dependsOn("installGaplVerilog")

    // Allow overrides: -Pmajor=..., -Pminor=..., -Pgui=false
    val major = (findProperty("major") as String?) ?: "simple"
    val minor = (findProperty("minor") as String?) ?: "broadcast"
    val gui   = ((findProperty("gui") as String?) ?: "true").toBoolean()

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

// Remove generated/installed Verilog from $NF_DESIGN_DIR/hw/hdl
tasks.register<Delete>("uninstallGaplVerilog") {
    group = "netfpga"
    description = "Remove Verilog installed from -PgaplSources under \$NF_DESIGN_DIR/hw/hdl"

    doFirst {
        val nfHdlDir = file("$nfDesignDir/hw/hdl")
        if (!nfHdlDir.exists()) {
            println("[uninstallGaplVerilog] Skipping: $nfHdlDir does not exist")
            return@doFirst
        }

        val targetsProp = findProperty("gaplSources") as String?
        if (targetsProp.isNullOrBlank()) {
            println("[uninstallGaplVerilog] No -PgaplSources provided; skipping file removal.")
            return@doFirst
        }

        // For each target path relative to netfpga/src/, decide the installed .v name
        val toDelete = targetsProp.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { rel ->
                val relFile = File(rel)
                val vName = targetVerilogName(relFile)
                nfHdlDir.resolve(vName)
            }

        toDelete.forEach { f ->
            if (f.exists()) { delete(f) }
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
    """.trimIndent()))
}

// Wire lifecycle
tasks.named("build") {
    dependsOn("makeBuild")
}
tasks.named("clean") {
    dependsOn("uninstallGaplVerilog")
    dependsOn("makeClean")
}
