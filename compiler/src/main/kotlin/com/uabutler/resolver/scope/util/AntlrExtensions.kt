package com.uabutler.resolver.scope.util

import com.uabutler.ast.node.IdentifierNode
import com.uabutler.diagnostics.SourceSpan
import com.uabutler.parsers.generated.CSTParser
import org.antlr.v4.kotlinruntime.Token

fun Token.toIdentifierNode() = IdentifierNode(SourceSpan.of(this), this.text!!)

// interfaceDefinition is not a labeled-alternative rule, so both branches share one context
// type with two nullable sub-rule accessors instead of distinct generated subclasses.
val CSTParser.InterfaceDefinitionContext.declaredIdentifierToken: Token
    get() = aliasInterfaceDefinition()?.declaredIdentifer
        ?: recordInterfaceDefinition()!!.declaredIdentifer!!
