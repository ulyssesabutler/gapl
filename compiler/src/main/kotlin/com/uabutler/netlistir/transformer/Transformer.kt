package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.MutableModule

interface Transformer {

    fun transform(original: List<MutableModule>): List<MutableModule>

}