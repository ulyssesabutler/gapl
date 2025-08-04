package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module

// Module names are useful for debugging, but their length could present a problem for the synthesizer.
object Renamer: Transformer {
    fun renameNodesInModule(original: Module): Module {
        var counter = 0
        fun genNodeName() = "node${counter++}"

        original.getBodyNodes().forEach { it.rename(genNodeName()) }

        return original
    }

    override fun transform(original: List<Module>): List<Module> {
        return original.map { renameNodesInModule(it) }
    }
}