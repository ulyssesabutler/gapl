package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.LiteralFunction

object LiteralSimplifier: Transformer {
    class ModuleSimplifier(val module: Module) {
        data class LiteralFunctionSignature(
            val size: Int,
            val value: Int,
        )

        val literalNodes = mutableMapOf<LiteralFunctionSignature, PredefinedFunctionNode>()

        fun getLiteralNode(signature: LiteralFunctionSignature) = literalNodes.getOrPut(signature) {
            val predefinedFunction = LiteralFunction(signature.size, signature.value)
            PredefinedFunctionNode(
                identifier = "\$SIMPLIFIED$${signature.size}$${signature.value}",
                parentModule = module,
                inputWireVectorGroupsBuilder = { node ->
                    predefinedFunction.inputs.map { it.toInputWireVectorGroup(node) }
                },
                outputWireVectorGroupsBuilder = { node ->
                    predefinedFunction.outputs.map { it.toOutputWireVectorGroup(node) }
                },
                predefinedFunction = predefinedFunction,
            ).also { module.addBodyNode(it) }
        }

        fun simplify(): Module {
            val existingLiteralNodes = module.getBodyNodes().filterIsInstance<PredefinedFunctionNode>()
                .filter { it.predefinedFunction is LiteralFunction }

            existingLiteralNodes.onEach { originalLiteralNode ->
                val signature = LiteralFunctionSignature(
                    size = (originalLiteralNode.predefinedFunction as LiteralFunction).size,
                    value = originalLiteralNode.predefinedFunction.value,
                )

                val newLiteralNode = getLiteralNode(signature)
                val wiresInOriginalLiteralNode = originalLiteralNode.outputWires()
                    .zip(newLiteralNode.outputWires())
                    .associate { it.first to it.second }

                module.getConnectionsForNodeOutput(originalLiteralNode).forEach { connection ->
                    val newSource = wiresInOriginalLiteralNode[connection.source]!!
                    module.disconnect(connection.sink)
                    module.connect(connection.sink, newSource)
                }
            }.forEach { module.removeNode(it) }

            return module
        }
    }

    override fun transform(original: List<Module>): List<Module> {
        return original.map { ModuleSimplifier(it).simplify() }
    }

}