package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.transformer.retiming.ModuleRetimer
import com.uabutler.netlistir.transformer.retiming.delay.PropagationDelay

class Retimer(val delay: PropagationDelay): Transformer {

    override fun transform(original: List<Module>): List<Module> {
        return original.map { ModuleRetimer.retime(it, delay) }
    }

}