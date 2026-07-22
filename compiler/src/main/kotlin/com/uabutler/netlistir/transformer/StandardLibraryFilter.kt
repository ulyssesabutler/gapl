package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.util.StandardLibraryFunctions

object StandardLibraryFilter: Transformer {

    override fun transform(original: List<Module>): List<Module> {
        val standardLibraryFunctions = StandardLibraryFunctions.entries.map { it.identifier }.toSet()

        val standardLibraryRootModuleNames = InvocationGraph(original).rootModules()
            .map { it.identifier() }
            .filter { it in standardLibraryFunctions }
            .toSet()

        return original.filter { it.identifier() !in standardLibraryRootModuleNames }
    }

}