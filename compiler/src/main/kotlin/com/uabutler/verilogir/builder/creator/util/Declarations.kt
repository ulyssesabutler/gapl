package com.uabutler.verilogir.builder.creator.util

import com.uabutler.netlistir.netlist.Node
import com.uabutler.verilogir.module.statement.Declaration
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.util.DataType

object Declarations {
    fun create(node: Node): List<Statement> = buildList {
        node.inputWireVectors().forEach { wireVector ->
            add(
                Declaration(
                    name = Identifier.wire(wireVector),
                    type = DataType.WIRE,
                    startIndex = wireVector.wires.size - 1,
                    endIndex = 0,
                )
            )
        }

        node.outputWireVectors().forEach { wireVector ->
            add(
                Declaration(
                    name = Identifier.wire(wireVector),
                    type = DataType.WIRE,
                    startIndex = wireVector.wires.size - 1,
                    endIndex = 0,
                )
            )
        }
    }
}