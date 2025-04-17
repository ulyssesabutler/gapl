package com.uabutler.verilogir.builder.node

import com.uabutler.gaplir.node.ModuleInvocationNode
import com.uabutler.verilogir.builder.identifier.ModuleIdentifierGenerator.genIdentifierFromInvocation
import com.uabutler.verilogir.module.statement.Invocation
import com.uabutler.verilogir.module.statement.Statement
import com.uabutler.verilogir.module.statement.invocation.InvocationPort

object ModuleInvocationConnector {
    fun connect(node: ModuleInvocationNode): List<Statement> {
        val inputModulePortNames = node.functionInputInterfaces.map { "${it.name}_output" }
        val inputOuterWirePortNames = node.inputs.map { "${it.name}_input" }

        val outputModulePortNames = node.functionOutputInterfaces.map { "${it.name}_input" }
        val outputOuterWirePortNames = node.outputs.map { "${it.name}_output" }

        val ports = (inputModulePortNames + outputModulePortNames).zip(inputOuterWirePortNames + outputOuterWirePortNames)

        val invocation = Invocation(
            invocationName = node.invokedModuleName,
            moduleName = genIdentifierFromInvocation(node.moduleInvocation),
            ports = ports.map { InvocationPort(modulePortName = it.first, variablePortName = it.second) }
        )
        return listOf(invocation)
    }
}