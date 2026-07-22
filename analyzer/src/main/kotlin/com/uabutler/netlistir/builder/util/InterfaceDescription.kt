package com.uabutler.netlistir.builder.util

import com.uabutler.util.InterfaceType

data class InterfaceDescription(
    val name: String,
    val interfaceStructure: InterfaceStructure,
    val interfaceType: InterfaceType,
)
