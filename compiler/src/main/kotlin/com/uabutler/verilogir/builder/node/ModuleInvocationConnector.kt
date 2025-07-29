package com.uabutler.verilogir.builder.node

import com.uabutler.util.VerilogInterface
import com.uabutler.verilogir.module.statement.Invocation
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.invocation.InvocationPort

object ModuleInvocationConnector {
    fun connect(node: ModuleInvocationNode): List<Statement> {
        val inputModulePortNames = node.functionInputInterfaces.flatMap {
            VerilogInterface.fromGAPLInterfaceStructure(
                name = it.name,
                structure = it.interfaceStructure,
                // TODO: Handle different interface types
            )
        }.map { it.name }

        val inputOuterWirePortNames = node.inputs.flatMap {
            VerilogInterface.fromGAPLInterfaceStructure(
                name = "${it.name}_input",
                structure = it.item.inputInterface.structure
            )
        }.map { it.name }

        val outputModulePortNames = node.functionOutputInterfaces.flatMap {
            VerilogInterface.fromGAPLInterfaceStructure(
                name = it.name,
                structure = it.interfaceStructure,
            )
        }.map { it.name }

        val outputOuterWirePortNames = node.outputs.flatMap {
            VerilogInterface.fromGAPLInterfaceStructure(
                name = "${it.name}_output",
                structure = it.item.outputInterface.structure
            )
        }.map { it.name }

        val ports = (inputModulePortNames + outputModulePortNames).zip(inputOuterWirePortNames + outputOuterWirePortNames)

        val invocation = Invocation(
            invocationName = node.invokedModuleName,
            moduleName = genIdentifierFromInvocation(node.moduleInvocation),
            ports = ports.map { InvocationPort(modulePortName = it.first, variablePortName = it.second) }
        )
        return listOf(invocation)
    }
}