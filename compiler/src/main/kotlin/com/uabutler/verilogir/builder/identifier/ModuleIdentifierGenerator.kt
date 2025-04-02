package com.uabutler.verilogir.builder.identifier

import com.uabutler.gaplir.util.ModuleInvocation

object ModuleIdentifierGenerator {
    fun genIdentifierFromInvocation(invocation: ModuleInvocation): String {
        return invocation.gaplFunctionName
    }
}