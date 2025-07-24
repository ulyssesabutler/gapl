package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.netlistir.util.PredefinedFunction

sealed class Node(
    val identifier: String,
    val parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) {
    val inputWireVectorGroups: List<InputWireVectorGroup> = inputWireVectorGroupsBuilder(this)
    val outputWireVectorGroups: List<OutputWireVectorGroup> = outputWireVectorGroupsBuilder(this)

    fun inputWires() = inputWireVectorGroups.flatMap { it.wireVectors.flatMap { it.wires } }
    fun outputWires() = outputWireVectorGroups.flatMap { it.wireVectors.flatMap { it.wires } }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "inputWireVectorGroups" to inputWireVectorGroups,
                "outputWireVectorGroups" to outputWireVectorGroups
            ),
            identityProps = mapOf(
                "parentModule" to parentModule
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        return ObjectUtils.equalsBuilder<Node>(
            self = this,
            other = other,
            { o -> identifier == o.identifier },
            { o -> parentModule == o.parentModule },
            { o -> inputWireVectorGroups == o.inputWireVectorGroups },
            { o -> outputWireVectorGroups == o.outputWireVectorGroups },
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            identifier,
            parentModule.invocation,
            inputWireVectorGroups,
            outputWireVectorGroups,
        )
    }
}

sealed class IONode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class InputNode(
    identifier: String,
    parentModule: Module,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : IONode(identifier, parentModule, { emptyList() }, outputWireVectorGroupsBuilder)

class OutputNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
) : IONode(identifier, parentModule, inputWireVectorGroupsBuilder, { emptyList() })

sealed class BodyNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class ModuleInvocationNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    val invocation: Module.Invocation,
) : BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PredefinedFunctionNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    val predefinedFunction: PredefinedFunction
) : BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PassThroughNode(
    identifier: String,
    parentModule: Module,
    // TODO: We need some way to represent the interface that will be the same on both sides
    //   For now, the user is just going to pinky promise they're the same
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
): BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)