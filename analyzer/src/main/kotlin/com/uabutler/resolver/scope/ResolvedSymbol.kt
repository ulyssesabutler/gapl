package com.uabutler.resolver.scope

import com.uabutler.parsers.generated.CSTParser

sealed interface ResolvedSymbol {
    // ctx is null for predefined builtin functions, which have no declaration site in user source.
    data class Function(val ctx: CSTParser.FunctionDefinitionContext?): ResolvedSymbol
    data class Interface(val ctx: CSTParser.InterfaceDefinitionContext): ResolvedSymbol
    data class Parameter(val ctx: CSTParser.ParameterDefinitionContext): ResolvedSymbol
    data class CircuitNode(val ctx: CSTParser.DeclaredCircuitExpressionContext): ResolvedSymbol
    data class FunctionIO(val ctx: CSTParser.FunctionIOContext): ResolvedSymbol
}
