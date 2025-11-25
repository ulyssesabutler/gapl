package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.util.Logger

// Module names are useful for debugging, but their length could present a problem for the synthesizer.
object Renamer: Transformer {
    fun renameNodesInModule(original: Module): Module {
        Logger.start("Renaming nodes in ${original.identifier()}")
        var counter = 0
        fun genNodeName() = "node${counter++}"

        Logger.debug { "Renaming ${original.getBodyNodes().size} nodes" }
        original.getBodyNodes().forEach { it.rename(genNodeName()) }

        return original.also { Logger.finish() }
    }

    override fun transform(original: List<Module>): List<Module> {
        return original.map { renameNodesInModule(it) }
    }
}