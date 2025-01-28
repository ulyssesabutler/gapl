package com.uabutler.v2.gaplir.builder.signature.util

import com.uabutler.v2.ast.node.GenericParameterDefinitionNode

sealed interface SignatureGenericParameterType

// TODO: Basic types
data object Integer: SignatureGenericParameterType

// TODO: Functions