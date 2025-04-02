package com.uabutler.gaplir.builder.signature

import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.gaplir.builder.signature.util.SignatureFunctionIO
import com.uabutler.gaplir.builder.signature.util.SignatureGenericInterface
import com.uabutler.gaplir.builder.signature.util.SignatureGenericParameter

data class ModuleSignature(
    val identifier: String,
    val genericInterfaces: Map<String, SignatureGenericInterface>,
    val genericParameters: Map<String, SignatureGenericParameter>,
    val inputs: Map<String, SignatureFunctionIO>,
    val outputs: Map<String, SignatureFunctionIO>,
) {
    companion object {
        fun fromNode(node: FunctionDefinitionNode): ModuleSignature {
            return ModuleSignature(
                identifier = node.identifier.value,
                genericInterfaces = node.genericInterfaces.map { SignatureGenericInterface.fromNode(it) }.associateBy { it.identifier },
                genericParameters = node.genericParameters.map { SignatureGenericParameter.fromNode(it) }.associateBy { it.identifier },
                inputs = node.inputFunctionIO.map { SignatureFunctionIO.fromNode(it) }.associateBy { it.identifier },
                outputs = node.outputFunctionIO.map { SignatureFunctionIO.fromNode(it) }.associateBy { it.identifier },
            )
        }
    }
}
