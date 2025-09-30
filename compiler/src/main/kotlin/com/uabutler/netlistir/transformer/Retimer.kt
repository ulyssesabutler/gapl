package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.graph.LeisersonCircuitGraph
import com.uabutler.util.graph.toDot
import java.io.File

class Retimer(val delay: PropagationDelay, val preretimeFilePrefix: String?, val postretimeFilePrefix: String?): Transformer {

    companion object {
        fun retimeModuleFilter(module: Module): Boolean {
            return module.getNodes().any {
                it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction
            }
        }
    }

    fun exportDotFile(
        graph: LeisersonCircuitGraph<Module, Node, Collection<NetlistLeisersonCircuitConverter.NonRegisterConnection>>,
        label: String,
        fileNamePrefix: String,
    ) {
        val file = File("$fileNamePrefix-${graph.value.identifier()}.dot")

        val fileContents = graph.toDot(
            graphName = "$label: ${graph.value.identifier()}",
            nodeName = { "${it.value.typeString()}:${it.value.name()}" }
        )

        file.writeText(fileContents)
    }

    override fun transform(original: List<Module>): List<Module> {
        val moduleRetimability = original.groupBy { retimeModuleFilter(it) }

        val modulesToRetime = moduleRetimability[true] ?: emptyList()
        val modulesToSkip = moduleRetimability[false] ?: emptyList()

        val retimedModules = modulesToRetime
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay) }
            .onEach { if (preretimeFilePrefix != null) exportDotFile(it, "Pre-Retiming", preretimeFilePrefix) }
            .map { it.retimed() }
            .onEach { if (postretimeFilePrefix != null) exportDotFile(it, "Post-Retiming", postretimeFilePrefix) }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }

        return retimedModules + modulesToSkip
    }

}