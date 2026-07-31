package com.uabutler.interpreter

import com.uabutler.simengine.Engine
import com.uabutler.simengine.PortValue
import com.uabutler.simgen.PortShape
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class Mismatch(val portName: String, val expected: PortValue, val actual: PortValue)

data class CycleResult(val index: Int, val mismatches: List<Mismatch>) {
    val passed: Boolean get() = mismatches.isEmpty()
}

/**
 * Runs one cycle-object per JSON array element against a live Engine: writes every input port
 * present in the object (a port absent from the object holds whatever value it already had - this
 * falls out of Engine's own wire storage, no extra "hold" logic needed), settles, then checks every
 * output port present in the object (absent means "don't care", not checked) before ticking to
 * advance to the next cycle. Checked right after settle(), before that cycle's tick() - i.e. cycle N's
 * expected outputs reflect the state as of the end of cycle N-1's tick, combined with cycle N's own
 * combinational inputs.
 */
object CycleRunner {
    fun run(
        engine: Engine,
        inputShapes: Map<String, PortShape>,
        outputShapes: Map<String, PortShape>,
        cycles: JsonArray,
    ): List<CycleResult> {
        val knownPorts = inputShapes.keys + outputShapes.keys

        return cycles.mapIndexed { index, cycleJson ->
            val cycleObj = cycleJson as? JsonObject ?: error("cycle $index: expected a JSON object, got $cycleJson")
            val unknown = cycleObj.keys - knownPorts
            check(unknown.isEmpty()) { "cycle $index: unknown port(s) $unknown - known ports: $knownPorts" }

            inputShapes.forEach { (name, shape) ->
                cycleObj[name]?.let { json -> engine.writeInputPort(name, PortValueJson.decode(shape, json, name)) }
            }

            engine.settle()

            val mismatches = outputShapes.mapNotNull { (name, shape) ->
                cycleObj[name]?.let { json ->
                    val expected = PortValueJson.decode(shape, json, name)
                    val actual = engine.readOutputPortValue(name)
                    if (expected != actual) Mismatch(name, expected, actual) else null
                }
            }

            engine.tick()

            CycleResult(index, mismatches)
        }
    }
}
