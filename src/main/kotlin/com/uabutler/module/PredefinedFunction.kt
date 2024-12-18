package com.uabutler.module

import com.uabutler.ast.IntegerLiteralNode
import com.uabutler.ast.VectorBoundsNode
import com.uabutler.ast.interfaces.VectorInterfaceExpressionNode
import com.uabutler.ast.interfaces.WireInterfaceExpressionNode
import com.uabutler.ast.staticexpressions.IntegerLiteralStaticExpressionNode

enum class NativeFunction(val identifier: String) {
    Add("add"),
    Subtract("subtract"),
    Multiply("multiply"),
    // TODO: All operators

    Register("register"),
}

object PredefinedFunction {
    private fun addModuleNode(nodeIdentifier: String): ModuleNode {
        val interfaceExpression = VectorInterfaceExpressionNode(
            vectoredInterface = WireInterfaceExpressionNode(),
            boundsSpecifier = VectorBoundsNode(IntegerLiteralStaticExpressionNode(IntegerLiteralNode(32))),
        )

        return ModuleNode(
            input = listOf(
                ModuleNodeInterface.fromInterfaceExpressionNode("${nodeIdentifier}_a", interfaceExpression, null),
                ModuleNodeInterface.fromInterfaceExpressionNode("${nodeIdentifier}_b", interfaceExpression, null),
            ),
            output = listOf(
                ModuleNodeInterface.fromInterfaceExpressionNode("${nodeIdentifier}_c", interfaceExpression, null),
            ),
            mode = ModuleNodeInternalMode.NativeFunction,
            nativeFunction = NativeFunction.Add,
        )
    }

    private fun registerModuleNode(nodeIdentifier: String): ModuleNode {
        val interfaceExpression = VectorInterfaceExpressionNode(
            vectoredInterface = WireInterfaceExpressionNode(),
            boundsSpecifier = VectorBoundsNode(IntegerLiteralStaticExpressionNode(IntegerLiteralNode(32))),
        )

        return ModuleNode(
            input = listOf(
                ModuleNodeInterface.fromInterfaceExpressionNode("${nodeIdentifier}_in", interfaceExpression, null),
            ),
            output = listOf(
                ModuleNodeInterface.fromInterfaceExpressionNode("${nodeIdentifier}_out", interfaceExpression, null),
            ),
            mode = ModuleNodeInternalMode.NativeFunction,
            nativeFunction = NativeFunction.Register,
        )
    }

    private fun getNativeFunction(identifier: String): NativeFunction? {
        return NativeFunction.entries.firstOrNull { it.identifier == identifier }
    }

    fun isNativeFunction(nodeIdentifier: String) = getNativeFunction(nodeIdentifier) != null

    fun moduleNodeFromPredefinedFunction(nodeIdentifier: String, functionIdentifier: String): ModuleNode {
        return when (getNativeFunction(functionIdentifier)) {
            NativeFunction.Add -> addModuleNode(nodeIdentifier)
            NativeFunction.Register -> registerModuleNode(nodeIdentifier)
            else -> throw Exception("Unknown predefined function: $functionIdentifier")
        }
    }
}
