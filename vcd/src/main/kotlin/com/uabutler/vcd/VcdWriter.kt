package com.uabutler.vcd

/**
 * Streaming/incremental VCD (Value Change Dump) writer. Standalone: zero dependency on GAPL's
 * netlist IR or simengine — a generic serializer from named-signal value-change events to VCD
 * text. Usage:
 *   1. declareSignal(...) for every signal, in any order/depth of scope nesting
 *   2. writeHeader() — emits $date?/$version/$timescale/$scope.../$var.../$enddefinitions
 *   3. dumpInitialValues(map of every declared SignalId -> its initial value)
 *   4. repeatedly: advanceTime(t), then writeValue(id, value) for whatever changed
 *
 * Internally deduplicates: writeValue silently no-ops if the value matches what's already
 * recorded for that signal, so a live driver can blast every signal's current value each tick
 * without its own diffing logic.
 */
class VcdWriter(
    private val sink: Appendable,
    private val timescale: String = "1ns",
    private val date: String? = null,
    private val version: String = "simengine",
) {
    private class DeclaredSignal(val scope: List<String>, val name: String, val width: Int, val vcdId: String)

    private val declared = mutableListOf<DeclaredSignal>() // index into this list == SignalId.index
    private val lastWritten = mutableMapOf<Int, List<Boolean>>() // SignalId.index -> last value written
    private var headerWritten = false
    private var lastTime: Long? = null

    fun declareSignal(scope: List<String>, name: String, width: Int): SignalId {
        check(!headerWritten) {
            "Cannot declare signal '$name': writeHeader() has already been called. All " +
                "declareSignal calls must happen before the header is written."
        }
        require(width > 0) { "Signal '$name' must have width > 0, got $width" }
        require(!name.any { it.isWhitespace() } && scope.none { segment -> segment.any { it.isWhitespace() } }) {
            "Signal/scope names must not contain whitespace (VCD's format is whitespace-delimited): '$name' in scope $scope"
        }
        val index = declared.size
        declared += DeclaredSignal(scope, name, width, IdentifierCodes.forIndex(index))
        return SignalId(index)
    }

    fun writeHeader() {
        check(!headerWritten) { "writeHeader() has already been called" }
        headerWritten = true

        if (date != null) sink.append("\$date $date \$end\n")
        sink.append("\$version $version \$end\n")
        sink.append("\$timescale $timescale \$end\n")

        emitScopeTree(buildScopeTree())

        sink.append("\$enddefinitions \$end\n")
    }

    fun dumpInitialValues(values: Map<SignalId, List<Boolean>>) {
        check(headerWritten) { "writeHeader() must be called before dumpInitialValues()" }
        val missing = declared.indices.filter { SignalId(it) !in values }
        require(missing.isEmpty()) {
            "dumpInitialValues is missing values for: ${missing.map { declared[it].name }}"
        }
        sink.append("\$dumpvars\n")
        declared.indices.forEach { i -> writeValueLine(SignalId(i), values.getValue(SignalId(i)), forceWrite = true) }
        sink.append("\$end\n")
    }

    fun advanceTime(time: Long) {
        check(headerWritten) { "writeHeader() must be called before advanceTime()" }
        val previous = lastTime
        require(previous == null || time > previous) {
            "advanceTime requires strictly increasing time; got $time after $previous"
        }
        lastTime = time
        sink.append("#$time\n")
    }

    fun writeValue(id: SignalId, value: List<Boolean>) {
        check(headerWritten) { "writeHeader() must be called before writeValue()" }
        writeValueLine(id, value, forceWrite = false)
    }

    private fun writeValueLine(id: SignalId, value: List<Boolean>, forceWrite: Boolean) {
        val signal = declared[id.index]
        require(value.size == signal.width) {
            "Value for signal '${signal.name}' has width ${value.size}, expected ${signal.width}"
        }
        if (!forceWrite && lastWritten[id.index] == value) return
        lastWritten[id.index] = value

        if (signal.width == 1) {
            sink.append(if (value[0]) "1" else "0").append(signal.vcdId).append("\n")
        } else {
            // VCD's b<...> format is MOST-significant-bit first; our internal convention is
            // LSB-first (bit 0 = LSB, matching simengine's BitUtils), so reverse before rendering.
            val bits = value.reversed().joinToString("") { if (it) "1" else "0" }
            sink.append("b").append(bits).append(" ").append(signal.vcdId).append("\n")
        }
    }

    // ---- scope tree (private, header-emission-only) ----

    private class ScopeNode {
        val children = LinkedHashMap<String, ScopeNode>() // preserves first-seen sibling order
        val signals = mutableListOf<DeclaredSignal>()      // leaves declared exactly at this path
    }

    private fun buildScopeTree(): ScopeNode {
        val root = ScopeNode()
        for (signal in declared) {
            var node = root
            for (segment in signal.scope) node = node.children.getOrPut(segment) { ScopeNode() }
            node.signals += signal
        }
        return root
    }

    private fun emitScopeTree(node: ScopeNode) {
        for (signal in node.signals) {
            sink.append("\$var wire ${signal.width} ${signal.vcdId} ${signal.name} \$end\n")
        }
        for ((name, child) in node.children) {
            sink.append("\$scope module $name \$end\n")
            emitScopeTree(child)
            sink.append("\$upscope \$end\n")
        }
    }
}
