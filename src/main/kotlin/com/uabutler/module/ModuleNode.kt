package com.uabutler.module

import com.uabutler.ast.PersistentNode
import com.uabutler.ast.functions.FunctionDefinitionNode
import com.uabutler.ast.interfaces.*
import com.uabutler.references.FunctionDeclaration
import com.uabutler.references.InterfaceDeclaration

data class ModuleNode(
    val input: List<ModuleNodeInterface>,
    val output: List<ModuleNodeInterface>,

    // Ugh, this is also wrong.
    val mode: ModuleNodeInternalMode = ModuleNodeInternalMode.AstModule,
    val astNode: PersistentNode? = null,
    val nativeFunction: NativeFunction? = null,
    val functionDefinition: FunctionDefinitionNode? = null,

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
            // Pre-defined: First, look for predefined functions
            if (PredefinedFunction.isNativeFunction(expressionNode.interfaceIdentifier.value)) {
                return PredefinedFunction.moduleNodeFromPredefinedFunction(identifier, expressionNode.interfaceIdentifier.value)
            }

            // Next, look for user-defined functions or interface
            val definition = expressionNode.getScope()!!.getDeclaration(expressionNode.interfaceIdentifier.value)
            when (definition) {
                is FunctionDeclaration -> {
                    val function = fromFunctionDefinitionNode(identifier, definition.astNode)
                    return ModuleNode(
                        input = function.input,
                        output = function.output,
                        astNode = astNode,
                        mode = ModuleNodeInternalMode.DefinedFunction,
                        functionDefinition = definition.astNode
                    )
                }
                is InterfaceDeclaration -> {
                    val newNode = ModuleNode(
                        input = listOf(ModuleNodeInterface.fromInterfaceDefinitionNode("${identifier}_input", definition.astNode, null)),
                        output = listOf(ModuleNodeInterface.fromInterfaceDefinitionNode("${identifier}_output", definition.astNode, null)),
                        astNode = astNode,
                        mode = ModuleNodeInternalMode.DefinedInterface,
                    )
                    newNode.input.forEach { it.parentNode = newNode }
                    newNode.output.forEach { it.parentNode = newNode }
                    return newNode
                }
                else -> throw Exception("Unexpected definition of instantiation${definition?.let { ", ${definition::class.simpleName}" }}")
            }
        }

        fun fromFunctionDefinitionNode(
            identifier: String,
            functionDefinitionNode: FunctionDefinitionNode,
            astNode: PersistentNode? = null,
        ): ModuleNode {
            val newNode = ModuleNode(
                input = functionDefinitionNode.inputFunctionIO.map { ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_${it.identifier.value}_input", it.interfaceType, null) },
                output = functionDefinitionNode.outputFunctionIO.map { ModuleNodeInterface.fromInterfaceExpressionNode("${identifier}_${it.identifier.value}_output", it.interfaceType, null) },
                astNode = astNode,
            )

            newNode.input.forEach { it.parentNode = newNode }
            newNode.output.forEach { it.parentNode = newNode }

            return newNode
        }
    }
}