package com.uabutler.verilogir.builder.creator

import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.verilogir.builder.creator.util.Declarations
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.statement.Assignment
import com.uabutler.verilogir.module.statement.Invocation
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.expression.Reference
import com.uabutler.verilogir.module.statement.invocation.InvocationPort

object ModuleInvocationNodeCreator {
    fun create(node: ModuleInvocationNode): List<Statement> = buildList {
        // Create the IO wires
        addAll(Declarations.create(node))

        // Create the port list

        // Inputs
        // TODO: Make sure this lines up with how IO ports are generated
        val inputModulePortNames = node.inputWireVectors().map { Identifier.wire(it) }
        val inputOuterWirePortNames = node.inputWireVectors().map { Identifier.wire(it) }

        // Outputs
        // TODO: Make sure this lines up with how IO ports are generated
        val outputModulePortNames = node.outputWireVectors().map { Identifier.wire(it) }
        val outputOuterWirePortNames = node.outputWireVectors().map { Identifier.wire(it) }

        // The actual port list
        val ports = (inputModulePortNames + outputModulePortNames).zip(inputOuterWirePortNames + outputOuterWirePortNames)

        // Add the module invocation
        add(
            Invocation(
                invocationName = node.identifier,
                moduleName = Identifier.module(node.invocation),
                ports = ports.map { InvocationPort(modulePortName = it.first, variablePortName = it.second) }
            )
        )
    }
}