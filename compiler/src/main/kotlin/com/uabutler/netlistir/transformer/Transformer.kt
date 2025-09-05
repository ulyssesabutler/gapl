package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module

interface Transformer {

    fun transform(original: List<Module>): List<Module>

}