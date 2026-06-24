package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.util.Logger

// Module names are useful for debugging, but their length could present a problem for the synthesizer.
object Renamer: Transformer {
    fun renameNodesInModule(original: MutableModule): MutableModule {
        Logger.start("Renaming nodes in ${original.identifier()}", Logger.Level.TRACE)
        var counter = 0
        fun genNodeName() = "node${counter++}"

        Logger.trace { "Renaming ${original.getBodyNodes().size} nodes" }
        original.getBodyNodes().forEach { it.rename(genNodeName()) }

        return original.also { Logger.finish() }
    }

    override fun transform(original: List<MutableModule>): List<MutableModule> {
        return original.map { renameNodesInModule(it) }
    }
}