package com.uabutler.v2.verilogir.module

import com.uabutler.v2.verilogir.util.DataType

data class Module(
    val inputs: List<ModuleIO>,
    val outputs: List<ModuleIO>,
)

data class ModuleIO(
    val name: String,
    val type: DataType,
    val startIndex: Int,
    val endIndex: Int,
)