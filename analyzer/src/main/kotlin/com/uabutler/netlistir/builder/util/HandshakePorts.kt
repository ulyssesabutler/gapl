package com.uabutler.netlistir.builder.util

import com.uabutler.util.InterfaceType
import com.uabutler.util.Protocol

/**
 * The extra ports a protocol'd function gets for its handshake (type-system.md §5): per channel `i`, a
 * `valid` alongside it under @valid, plus a `ready` flowing the other way under @ready_valid. They are
 * ordinary IR ports, so retiming, simengine, Verilog emission and simgen carry them without knowing what
 * they are.
 *
 * Named `i$$valid` / `i$$ready`: record fields are joined with `$` (a port `i: valid(T)` already flattens
 * to `i$valid`), and GAPL identifiers cannot contain `$`, so a doubled separator never collides with
 * anything a user writes.
 */
object HandshakePorts {
    private const val SEPARATOR = "$$"

    fun valid(port: String) = "$port${SEPARATOR}valid"
    fun ready(port: String) = "$port${SEPARATOR}ready"

    fun isHandshakePort(name: String) = SEPARATOR in name

    data class Signature(val inputs: List<InterfaceDescription>, val outputs: List<InterfaceDescription>)

    /**
     * A function's full port list given its data ports: under @valid, a valid beside each data port;
     * under @ready_valid, also a ready per port, in the opposite direction - an input channel's ready is
     * an output, and an output channel's ready is an input.
     */
    fun withHandshake(inputs: List<InterfaceDescription>, outputs: List<InterfaceDescription>, protocol: Protocol): Signature {
        fun bit(name: String) = InterfaceDescription(name, WireInterfaceStructure, InterfaceType.SIGNAL)
        return when (protocol) {
            Protocol.PURE -> Signature(inputs, outputs)
            Protocol.VALID -> Signature(
                inputs = inputs + inputs.map { bit(valid(it.name)) },
                outputs = outputs + outputs.map { bit(valid(it.name)) },
            )
            Protocol.READY_VALID -> Signature(
                inputs = inputs + inputs.map { bit(valid(it.name)) } + outputs.map { bit(ready(it.name)) },
                outputs = outputs + outputs.map { bit(valid(it.name)) } + inputs.map { bit(ready(it.name)) },
            )
        }
    }
}
