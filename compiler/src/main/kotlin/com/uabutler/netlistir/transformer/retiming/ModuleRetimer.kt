package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.transformer.retiming.delay.PropagationDelay
import com.uabutler.netlistir.transformer.retiming.graph.WeightedModule

object ModuleRetimer {

    fun retime(module: Module, delay: PropagationDelay): Module {
        val weightedModule = WeightedModuleFromModule.convert(module, delay)
        val retimedModule = WeightedModuleRetimer.retime(weightedModule)
        return ModuleFromWeightedModule.convert(retimedModule)
    }

}