package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.LiteralFunction
import java.math.BigInteger

object LiteralSimplifier: Transformer {
    class ModuleSimplifier(val module: MutableModule) {
        data class LiteralFunctionSignature(
            val size: Int,
            val value: BigInteger,
        )

        val literalNodes = mutableMapOf<LiteralFunctionSignature, PredefinedFunctionNode>()

        fun getLiteralNode(signature: LiteralFunctionSignature) = literalNodes.getOrPut(signature) {
            val predefinedFunction = LiteralFunction(signature.size, signature.value)
            PredefinedFunctionNode(
                identifier = "SIMPLIFIED$${signature.size}$${signature.value}",
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

        fun simplify(): MutableModule {
            val existingLiteralNodes = module.getBodyNodes().filterIsInstance<PredefinedFunctionNode>()
                .filter { it.predefinedFunction is LiteralFunction }

            existingLiteralNodes.onEach { originalLiteralNode ->
                val literalFunction = originalLiteralNode.predefinedFunction as LiteralFunction
                val signature = LiteralFunctionSignature(
                    size = literalFunction.size,
                    value = literalFunction.value,
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
        return original
            .map { it.toMutableModule() }
            .map { ModuleSimplifier(it).simplify() }
    }

}