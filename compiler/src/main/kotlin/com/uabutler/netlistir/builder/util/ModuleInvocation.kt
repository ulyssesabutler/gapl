package com.uabutler.netlistir.builder.util

// TODO: Is there any reason to keep this seperate from the ModuleInstantiationData?
data class ModuleInvocation(
    val gaplFunctionName: String,
    val interfaces: List<InterfaceStructure>,
    val parameters: List<ParameterValue<*>>,
)