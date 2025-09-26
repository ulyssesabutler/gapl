package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.RegisterFunction

class Retimer(val delay: PropagationDelay): Transformer {

    companion object {
        fun retimeModuleFilter(module: Module): Boolean {
            return module.getNodes().any {
                it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction
            }
        }
    }

    override fun transform(original: List<Module>): List<Module> {
        val moduleRetimability = original.groupBy { retimeModuleFilter(it) }

        val modulesToRetime = moduleRetimability[true] ?: emptyList()
        val modulesToSkip = moduleRetimability[false] ?: emptyList()

        val retimedModules = modulesToRetime
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay) }
            .map { it.retimed() }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }

        return retimedModules + modulesToSkip
    }

}