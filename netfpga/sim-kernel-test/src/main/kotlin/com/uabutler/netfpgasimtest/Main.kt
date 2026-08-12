package com.uabutler.netfpgasimtest

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.simengine.Engine
import com.uabutler.simengine.PortValue
import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import com.uabutler.simgen.RootModuleResolver
import com.uabutler.simtrace.VcdTracer
import com.uabutler.util.StandardLibraryFunctions
import com.uabutler.vcd.VcdWriter
import java.io.File
import java.io.FileWriter
import java.math.BigInteger
import kotlin.system.exitProcess

/**
 * A single beat on the netfpga_packet_body interface (data/keep/valid/last) - "valid" is implicit
 * (true for every beat that's actually driven/captured; idle cycles never produce a Beat at all).
 */
data class Beat(val data: List<Boolean>, val keep: List<Boolean>, val last: Boolean)

private const val BEAT_BYTES = 32 // 256-bit data bus = 32 bytes/beat, mirroring netfpga/kernel-test
private const val BEAT_HEX_CHARS = BEAT_BYTES * 2

/**
 * Splits a packet's hex string into wire beats. Equivalent to (and cross-checked against)
 * netfpga/kernel-test/test.cpp's string_to_nf_stream + the reverse() that follows it in
 * make_messages(): treat the whole hex string as one big-endian number, and chunk it into 256-bit
 * (64 hex char) beats aligned to the LSB end - so if the total length isn't a multiple of 64 hex
 * chars, the short/partial beat is the FIRST one sent (the leftover at the string's own start),
 * not the last. The final beat sent is always the one nearest the end of the hex string, and that's
 * the one marked `last`.
 */
fun hexToBeats(hex: String): List<Beat> {
    if (hex.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    val remainder = hex.length % BEAT_HEX_CHARS
    var pos = 0
    if (remainder != 0) {
        chunks += hex.substring(0, remainder)
        pos = remainder
    }
    while (pos < hex.length) {
        chunks += hex.substring(pos, pos + BEAT_HEX_CHARS)
        pos += BEAT_HEX_CHARS
    }

    return chunks.mapIndexed { index, chunkHex ->
        val byteCount = chunkHex.length / 2
        val data = unsignedBigIntegerToBits(BigInteger(chunkHex, 16), BEAT_BYTES * 8)
        val keepValue = if (byteCount == BEAT_BYTES) {
            BigInteger.TWO.pow(32) - BigInteger.ONE
        } else {
            BigInteger.TWO.pow(byteCount) - BigInteger.ONE
        }
        val keep = unsignedBigIntegerToBits(keepValue, 32)
        Beat(data, keep, last = index == chunks.size - 1)
    }
}

fun beatDataToHex(bits: List<Boolean>): String =
    bitsToUnsignedBigInteger(bits).toString(16).padStart(BEAT_HEX_CHARS, '0')

fun beatKeepToHex(bits: List<Boolean>): String =
    bitsToUnsignedBigInteger(bits).toString(16)

private fun idleInput(): PortValue = PortValue.Fields(
    mapOf(
        "data" to PortValue.Bits(List(BEAT_BYTES * 8) { false }),
        "keep" to PortValue.Bits(List(32) { false }),
        "valid" to PortValue.Bits(listOf(false)),
        "last" to PortValue.Bits(listOf(false)),
    )
)

private fun beatInput(beat: Beat): PortValue = PortValue.Fields(
    mapOf(
        "data" to PortValue.Bits(beat.data),
        "keep" to PortValue.Bits(beat.keep),
        "valid" to PortValue.Bits(listOf(true)),
        "last" to PortValue.Bits(listOf(beat.last)),
    )
)

private fun readOutputBeat(engine: Engine): Beat? {
    val fields = (engine.readOutputPortValue("o") as PortValue.Fields).fields
    val valid = (fields.getValue("valid") as PortValue.Bits).bits.single()
    if (!valid) return null
    val data = (fields.getValue("data") as PortValue.Bits).bits
    val keep = (fields.getValue("keep") as PortValue.Bits).bits
    val last = (fields.getValue("last") as PortValue.Bits).bits.single()
    return Beat(data, keep, last)
}

/**
 * Drives one packet's input beats in, one per tick, then idles (draining any output beats) until
 * the output side asserts `last` or the idle budget runs out - the same reset-free, per-packet
 * drain loop as kernel-test/test.cpp's simulate(), just against Engine.tick() instead of a Verilator
 * clock toggle. There's no netlist-level "reset" port to pulse between packets (that's a compiler/
 * retiming-wrapper concept, absent from the untransformed netlist this runs against) - so the
 * caller builds a fresh Engine per packet instead, which is the untimed equivalent.
 */
private fun simulatePacket(
    engine: Engine,
    tick: () -> Unit,
    inputBeats: List<Beat>,
    packetIndex: Int,
    maxIdleCycles: Int,
): List<Beat> {
    val outputs = mutableListOf<Beat>()
    var inputIndex = 0
    var idleLeft = maxIdleCycles
    var sawLast = false
    var cycle = 0

    while (!sawLast && idleLeft > 0) {
        if (inputIndex < inputBeats.size) {
            val beat = inputBeats[inputIndex]
            println(
                "Packet $packetIndex Input beat $inputIndex:\n" +
                    "  Cycle: $cycle\n" +
                    "  Data:  ${beatDataToHex(beat.data)}\n" +
                    "  Keep:  ${beatKeepToHex(beat.keep)}\n" +
                    "  Last:  ${beat.last}"
            )
            engine.writeInputPort("i", beatInput(beat))
            inputIndex++
        } else {
            engine.writeInputPort("i", idleInput())
        }

        tick()
        cycle++

        val outBeat = readOutputBeat(engine)
        if (outBeat != null) {
            println(
                "Packet $packetIndex Output beat ${outputs.size}:\n" +
                    "  Cycle: $cycle\n" +
                    "  Data:  ${beatDataToHex(outBeat.data)}\n" +
                    "  Keep:  ${beatKeepToHex(outBeat.keep)}\n" +
                    "  Last:  ${outBeat.last}"
            )
            outputs += outBeat
            if (outBeat.last) sawLast = true
            idleLeft = maxIdleCycles
        } else if (inputIndex >= inputBeats.size) {
            idleLeft--
        }
    }

    if (!sawLast) {
        error(
            "timeout waiting for last output beat of packet $packetIndex after " +
                "$maxIdleCycles idle cycles (cycle=$cycle)"
        )
    }

    return outputs
}

/**
 * Pads a short actual-beat list up to expectedCount with filler beats (data=0, keep=all-1s),
 * moving `last` onto the final padded beat - mirrors kernel-test/test.cpp's
 * check_simulation_success() padding exactly. Needed because some existing test.properties
 * expected-output vectors are framed as more beats than packet_body_processor actually emits per
 * packet (e.g. regex's design only ever registers one real result beat per packet, however many
 * input beats it took): the Verilator harness silently pads to match, and this harness needs to
 * accept the same vectors the same way rather than flagging a false beat-count mismatch.
 */
private fun padOutputs(actual: List<Beat>, expectedCount: Int): List<Beat> {
    if (actual.size >= expectedCount) return actual
    val cleared = actual.map { it.copy(last = false) }
    val pad = Beat(data = List(BEAT_BYTES * 8) { false }, keep = List(32) { true }, last = false)
    val padded = cleared + List(expectedCount - cleared.size) { pad }
    return padded.mapIndexed { i, beat -> if (i == padded.size - 1) beat.copy(last = true) else beat }
}

private fun checkPacket(packetIndex: Int, expected: List<Beat>, rawActual: List<Beat>): Boolean {
    val actual = padOutputs(rawActual, expected.size)

    if (expected.size != actual.size) {
        System.err.println(
            "Test Error: packet $packetIndex beat-count mismatch\n" +
                "  Expected beats: ${expected.size}\n" +
                "  Actual beats:   ${actual.size} (${rawActual.size} produced)"
        )
        return false
    }

    var success = true
    for (i in expected.indices) {
        val exp = expected[i]
        val act = actual[i]
        val matches = exp.data == act.data && exp.keep == act.keep && exp.last == act.last

        println("Packet $packetIndex Output $i")

        if (!matches) {
            System.err.println(
                "Test Error: mismatch at packet $packetIndex, output $i\n" +
                    "  Expected data: ${beatDataToHex(exp.data)}\n" +
                    "  Actual data:   ${beatDataToHex(act.data)}\n" +
                    "  Expected keep: ${beatKeepToHex(exp.keep)}\n" +
                    "  Actual keep:   ${beatKeepToHex(act.keep)}\n" +
                    "  Expected last: ${exp.last}\n" +
                    "  Actual last:   ${act.last}"
            )
            success = false
        } else {
            System.err.println(
                "Test Success: match at packet $packetIndex, output $i\n" +
                    "  Data: ${beatDataToHex(act.data)}\n" +
                    "  Keep: ${beatKeepToHex(act.keep)}\n" +
                    "  Last: ${act.last}"
            )
        }
    }
    return success
}

private fun waveformPathForPacket(base: File, packetIndex: Int, packetCount: Int): File {
    if (packetCount <= 1) return base
    val name = base.name
    val dot = name.lastIndexOf('.')
    val newName = if (dot >= 0) {
        "${name.substring(0, dot)}.pkt$packetIndex${name.substring(dot)}"
    } else {
        "$name.pkt$packetIndex"
    }
    return File(base.parentFile, newName)
}

fun runSimKernelTest(
    gaplFile: File,
    inputs: List<String>,
    expectedOutputs: List<String>,
    waveformPath: File?,
    targetModuleName: String?,
    maxIdleCycles: Int,
) {
    if (inputs.size != expectedOutputs.size) {
        println(
            "Error: got ${inputs.size} -i input(s) but ${expectedOutputs.size} -o expected-output(s) " +
                "- every input packet needs exactly one expected-output packet."
        )
        exitProcess(1)
    }

    val analysis = Analyzer.analyzeFull(gaplFile.readText())
    if (analysis.modules == null) {
        println("Error: failed to compile ${gaplFile.path}:\n" + analysis.diagnostics.joinToString("\n"))
        exitProcess(1)
    }
    val modules = analysis.modules!!

    // Same stdlib-root-filtering as interpreter/Main.kt's buildEngine(): an unused stdlib helper
    // function has no incoming invocation edges either, and would otherwise falsely look like a
    // second root module.
    val stdlibNames = StandardLibraryFunctions.entries.map { it.identifier }.toSet()
    val candidateRoots = InvocationGraph(modules).rootModules()
        .filterNot { it.invocation.gaplFunctionName in stdlibNames }
    val target: Module = try {
        RootModuleResolver.resolve(candidateRoots, targetModuleName)
    } catch (e: IllegalStateException) {
        println("Error: ${e.message}")
        exitProcess(1)
    }

    println("Using parameters...")
    println("  GAPL file: ${gaplFile.path}")
    println("  Module:    ${target.invocation.gaplFunctionName}")
    println("  Packets:   ${inputs.size}")
    if (waveformPath != null) println("  Waveform:  ${waveformPath.path}")

    val inputPackets = inputs.map { hexToBeats(it) }
    val expectedPackets = expectedOutputs.map { hexToBeats(it) }

    var allPassed = true

    inputPackets.forEachIndexed { packetIndex, packetInputs ->
        val engine = Engine.build(modules, target.invocation)

        val tracer = waveformPath?.let {
            val path = waveformPathForPacket(it, packetIndex, inputPackets.size)
            path.parentFile?.mkdirs()
            val writer = VcdWriter(FileWriter(path))
            VcdTracer(engine, writer).also { t -> t.dumpInitial() }
        }
        val tick: () -> Unit = tracer?.let { { it.tick() } } ?: { engine.tick() }

        val outputs = simulatePacket(engine, tick, packetInputs, packetIndex, maxIdleCycles)
        println("Finished packet $packetIndex after ${outputs.size} output beat(s)")

        if (!checkPacket(packetIndex, expectedPackets[packetIndex], outputs)) {
            allPassed = false
        }
    }

    exitProcess(if (allPassed) 0 else 1)
}

class SimKernelTest : CliktCommand(name = "sim-kernel-test") {

    override fun help(context: Context) =
        "Runs netfpga's packet_body_processor GAPL design against packet-in/packet-out test " +
            "vectors, directly through simengine (no Verilog/Verilator). The simengine counterpart " +
            "to netfpga/kernel-test's Verilator harness - same -i/-o hex packet convention, one " +
            "packet per pair, so the same test.properties vectors work against either."

    private val gaplFile: File by option("-f", "--file", help = "GAPL source file to compile (processor.gapl).")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true).required()

    private val inputs: List<String> by option(
        "-i", "--input",
        help = "One packet's hex payload. Repeatable; pairs positionally with -o.",
    ).multiple()

    private val expectedOutputs: List<String> by option(
        "-o", "--expected-output",
        help = "One packet's expected hex payload. Repeatable; pairs positionally with -i.",
    ).multiple()

    private val waveformPath: File? by option(
        "-w", "--waveform",
        help = "VCD output path. With more than one packet, each gets its own file " +
            "(name.pktN.vcd) since each packet runs against a fresh Engine.",
    ).file(canBeDir = false)

    private val targetModule: String? by option(
        "--module",
        help = "Root module to run. Required if the source has more than one root module; " +
            "defaults to packet_body_processor's sole root.",
    )

    private val maxIdleCycles: Int by option(
        "--max-idle-cycles",
        help = "Idle cycles to wait for a packet's final output beat before giving up.",
    ).int().default(1000)

    override fun run() {
        runSimKernelTest(gaplFile, inputs, expectedOutputs, waveformPath, targetModule, maxIdleCycles)
    }
}

fun main(args: Array<String>) = SimKernelTest().main(args)
