package com.uabutler.netlistir.builder.util

import com.uabutler.util.InterfaceType
import com.uabutler.util.Protocol

/**
 * The extra ports a protocol'd function gets for its handshake (type-system.md §5): per channel `i`, a
 * `valid` input/output alongside it under @valid. They are ordinary IR ports, so retiming, simengine,
 * Verilog emission and simgen carry them without knowing what they are.
 *
 * Named `i$$valid`: record fields are joined with `$` (a port `i: valid(T)` already flattens to
 * `i$valid`), and GAPL identifiers cannot contain `$`, so a doubled separator never collides with
 * anything a user writes.
 */
object HandshakePorts {
    private const val SEPARATOR = "$$"

    fun valid(port: String) = "$port${SEPARATOR}valid"

    fun isHandshakePort(name: String) = SEPARATOR in name

    /** The ports a function with this protocol exposes, given its data ports. */
    fun withHandshake(ports: List<InterfaceDescription>, protocol: Protocol): List<InterfaceDescription> = when (protocol) {
        Protocol.VALID -> ports + ports.map { InterfaceDescription(valid(it.name), WireInterfaceStructure, InterfaceType.SIGNAL) }
        // @ready_valid's handshake (valid and ready) arrives in stage 2c.
        Protocol.PURE, Protocol.READY_VALID -> ports
    }
}
