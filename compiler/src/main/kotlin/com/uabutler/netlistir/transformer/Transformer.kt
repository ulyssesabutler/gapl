package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module

interface Transformer {
    fun transform(original: List<Module>): List<Module>

    companion object {
        fun allTransformations(original: List<Module>): List<Module> {
            val transformers = listOf(
                PassThroughRemover,
            )

            return transformers.fold(original) { acc, transformer -> transformer.transform(acc) }
        }
    }
}