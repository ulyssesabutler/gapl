package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.transformer.retiming.ModuleRetimer
import com.uabutler.netlistir.transformer.retiming.delay.PropagationDelay
import com.uabutler.netlistir.util.RegisterFunction

class Retimer(val delay: PropagationDelay): Transformer {

    override fun transform(original: List<Module>): List<Module> {
        return original
            .filter { it.getNodes().any { it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction } }
            .map { ModuleRetimer.retime(it, delay) }
    }

}