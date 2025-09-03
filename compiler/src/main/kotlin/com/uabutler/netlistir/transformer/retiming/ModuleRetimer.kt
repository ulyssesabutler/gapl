package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.netlist.Module

object ModuleRetimer {

    fun retime(module: Module): Module {
        val weightedModule = WeightedModuleFromModule.convert(module)
        val retimedModule = WeightedModuleRetimer.retime(weightedModule)
        return ModuleFromWeightedModule.convert(retimedModule)
    }

}