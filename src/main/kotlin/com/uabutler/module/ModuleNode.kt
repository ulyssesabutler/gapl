package com.uabutler.module

import com.uabutler.ast.PersistentNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.interfaces.*
import com.uabutler.references.FunctionDeclaration
import com.uabutler.references.InterfaceDeclaration

data class ModuleNode(
    val input: List<ModuleNodeInterface>,
    val output: List<ModuleNodeInterface>,
    val astNode: PersistentNode? = null,
    // TODO: Internal connections, is it a function, or a pass through? Maybe an operation?
) {
    // TODO: For now, we're just assuming every function and interface definition is concrete

    fun hasInput() = input.all { it.hasInput() }

    companion object {
        fun fromInterfaceExpressionNode(
            identifier: String,
            expressionNode: InterfaceExpressionNode,
            astNode: PersistentNode? = null,
        ): ModuleNode {
            return if (expressionNode is DefinedInterfaceExpressionNode) {
                fromInstantiationNode(identifier, expressionNode, astNode)
            } else {
                val newNode = ModuleNode(
                    input = listOf(ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_input", expressionNode, null)),
                    output = listOf(ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_output", expressionNode, null)),
                    astNode = astNode,
                )
                newNode.input.forEach { it.parentNode = newNode }
                newNode.output.forEach { it.parentNode = newNode }
                newNode
            }
        }

        fun fromInstantiationNode(
            identifier: String,
            expressionNode: DefinedInterfaceExpressionNode,
            astNode: PersistentNode? = null,
        ): ModuleNode {
            val definition = expressionNode.getScope()!!.getDeclaration(expressionNode.interfaceIdentifier.value)
            when (definition) {
                is FunctionDeclaration -> {
                    val function = fromFunctionDefinitionNode(identifier, definition.astNode)
                    return ModuleNode(
                        input = function.input,
                        output = function.output,
                        astNode = astNode,
                    )
                }
                is InterfaceDeclaration -> {
                    val newNode = ModuleNode(
                        input = listOf(ModuleNodeInterface.fromInterfaceDefinitionNode("${identifier}_input", definition.astNode, null)),
                        output = listOf(ModuleNodeInterface.fromInterfaceDefinitionNode("${identifier}_output", definition.astNode, null)),
                        astNode = astNode,
                    )
                    newNode.input.forEach { it.parentNode = newNode }
                    newNode.output.forEach { it.parentNode = newNode }
                    return newNode
                }
                else -> throw Exception("Unexpected definition of instantiation")
            }
        }

        fun fromFunctionDefinitionNode(
            identifier: String,
            functionDefinitionNode: FunctionDefinitionNode,
            astNode: PersistentNode? = null,
        ): ModuleNode {
            val newNode = ModuleNode(
                input = functionDefinitionNode.inputFunctionIO.map { ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_input", it.interfaceType, null) },
                output = functionDefinitionNode.outputFunctionIO.map { ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_output", it.interfaceType, null) },
                astNode = astNode,
            )

            newNode.input.forEach { it.parentNode = newNode }
            newNode.output.forEach { it.parentNode = newNode }

            return newNode
        }
    }
}