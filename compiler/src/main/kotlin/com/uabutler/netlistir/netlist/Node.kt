package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.netlistir.util.PredefinedFunction

sealed class Node(
    val identifier: String,
    val parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> Collection<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> Collection<OutputWireVectorGroup>
) {
    val inputWireVectorGroups: Map<String, InputWireVectorGroup> = inputWireVectorGroupsBuilder(this).associateBy { it.identifier }
    val outputWireVectorGroups: Map<String, OutputWireVectorGroup> = outputWireVectorGroupsBuilder(this).associateBy { it.identifier }

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
            { o -> ObjectUtils.identifierEquals(
                selfValue = parentModule,
                otherValue = o.parentModule,
                identifierAccessor = { it.identifier }
            ) },
            { o -> inputWireVectorGroups.keys == o.inputWireVectorGroups.keys },
            { o -> outputWireVectorGroups.keys == o.outputWireVectorGroups.keys }
        )
    }

    override fun hashCode(): Int {
        return ObjectUtils.hashCodeBuilder(
            identifier,
            parentModule.identifier,
            inputWireVectorGroups.keys,
            outputWireVectorGroups.keys
        )
    }
}

class ModuleInvocationNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> Collection<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> Collection<OutputWireVectorGroup>,
    // By this stage, modules will be uniquely defined by their identifier.
    // TODO: Is this the right way to do this? Should we just keep the invocation information and compare it directly?
    val invokedModuleIdentifier: String
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PredefinedFunctionNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> Collection<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> Collection<OutputWireVectorGroup>,
    val predefinedFunction: PredefinedFunction
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PassThroughNode(
    identifier: String,
    parentModule: Module,
    // TODO: We need some way to represent the interface that will be the same on both sides
    //   For now, the user is just going to pinky promise they're the same
    inputWireVectorGroupsBuilder: (Node) -> Collection<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> Collection<OutputWireVectorGroup>,
): Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)