package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.LiteralFunction

object ConstantSimplifier: Transformer {

    private fun isCollapsibleNode(node: BodyNode): Boolean {
        if (node is PassThroughNode) return true

        if (node is PredefinedFunctionNode) {
            return node.predefinedFunction !is LiteralFunction
        }

        return false
    }

    private fun simplify(module: Module): Module {
        val literalNodes = module.getBodyNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction is LiteralFunction }

        TODO()
    }

    override fun transform(original: List<Module>): List<Module> {
        return original.map { simplify(it) }
    }
}