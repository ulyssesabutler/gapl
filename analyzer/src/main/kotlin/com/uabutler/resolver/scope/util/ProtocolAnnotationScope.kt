package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.ProtocolAnnotationNode
import com.uabutler.diagnostics.ResolverDiagnosticKind
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.SemanticTokenKind
import com.uabutler.util.Protocol

/** Resolves `@name` to a protocol, or reports it and returns null if the name isn't one. */
class ProtocolAnnotationScope(
    parentScope: Scope,
    val annotation: CSTParser.ProtocolAnnotationContext,
): Scope by parentScope {

    fun ast(): ProtocolAnnotationNode? {
        val span = SourceSpan.of(annotation)
        val name = annotation.protocol!!.text!!
        val protocol = Protocol.from(name) ?: run {
            diagnostics.reportError(ResolverDiagnosticKind.UnknownProtocol(name), span)
            return null
        }
        semanticTokens.record(span, SemanticTokenKind.KEYWORD)
        return ProtocolAnnotationNode(span, protocol)
    }
}
