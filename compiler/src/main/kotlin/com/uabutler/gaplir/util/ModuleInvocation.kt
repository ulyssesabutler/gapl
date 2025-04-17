package com.uabutler.gaplir.util

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.builder.util.ParameterValue

// TODO: Is there any reason to keep this seperate from the ModuleInstantiationData?
data class ModuleInvocation(
    val gaplFunctionName: String,
    val interfaces: List<InterfaceStructure>,
    val parameters: List<ParameterValue<*>>,
)