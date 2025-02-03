package com.uabutler.v2.verilogir.builder.identifier

import com.uabutler.v2.gaplir.util.ModuleInvocation

object ModuleIdentifierGenerator {
    fun genIdentifierFromInvocation(invocation: ModuleInvocation): String {
        return invocation.gaplFunctionName
    }
}