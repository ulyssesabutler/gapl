package com.uabutler.v2.gaplir.node

import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.builder.util.AnonymousIdentifierGenerator
import com.uabutler.v2.gaplir.node.input.NodeInputInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputInterface
import com.uabutler.v2.gaplir.util.ModuleInvocation

sealed class Node(
    // Default to an anonymous node
    open val name: String = AnonymousIdentifierGenerator.genIdentifier(),

    inputInterfaceStructures: List<InterfaceStructure>,
    outputInterfaceStructures: List<InterfaceStructure>,
) {
    val inputs: List<NodeInputInterface> = NodeInputInterface.fromStructures(this, inputInterfaceStructures)
    val outputs: List<NodeOutputInterface> = NodeOutputInterface.fromStructures(this, outputInterfaceStructures)
}

class PassThroughNode(
    name: String = AnonymousIdentifierGenerator.genIdentifier(),
    interfaceStructures: List<InterfaceStructure>,
): Node(name, interfaceStructures, interfaceStructures)

class ModuleInvocationNode(
    invocationName: String = AnonymousIdentifierGenerator.genIdentifier(),

    val moduleInvocation: ModuleInvocation,

    val functionInputInterfaces: List<Pair<String, InterfaceStructure>>,
    val functionOutputInterfaces: List<Pair<String, InterfaceStructure>>,
): Node(invocationName, functionInputInterfaces.map { it.second }, functionOutputInterfaces.map { it.second })

// TODO: Create nodes for built-in functions, register, priority queues, etc.