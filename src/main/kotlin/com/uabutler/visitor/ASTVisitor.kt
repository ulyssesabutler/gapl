package com.uabutler.visitor

import com.uabutler.ast.*
import com.uabutler.ast.interfaces.*
import com.uabutler.ast.staticexpressions.*
import com.uabutler.parsers.generated.GAPLBaseVisitor
import com.uabutler.parsers.generated.GAPLParser
import org.antlr.v4.kotlinruntime.Token
import org.antlr.v4.kotlinruntime.tree.TerminalNode

object TokenVisitor {
    fun visitId(node: TerminalNode): IdentifierNode {
        return IdentifierNode(node.text)
    }

    fun visitId(token: Token?): IdentifierNode {
        return IdentifierNode(token!!.text!!)
    }

    fun visitIntegerLiteral(token: TerminalNode): IntegerLiteralNode {
        return IntegerLiteralNode(token.text.toInt())
    }
}

class ASTVisitor: GAPLBaseVisitor<GAPLNode>() {

    override fun defaultResult() = EmptyNode

    override fun visitProgram(ctx: GAPLParser.ProgramContext): ProgramNode {
        return ProgramNode(
            ctx.interfaceDefinition()
                .map { visit(it) }
                .filterIsInstance<InterfaceDefinitionNode>()
        )
    }

    override fun visitInterfaceDefinition(ctx: GAPLParser.InterfaceDefinitionContext): InterfaceDefinitionNode {
        ctx.aliasInterfaceDefinition()?.let { return visitAliasInterfaceDefinition(it) }
        ctx.recordInterfaceDefinition()?.let { return visitRecordInterfaceDefinition(it) }
        throw Exception("Invalid interface definition")
    }

    override fun visitAliasInterfaceDefinition(ctx: GAPLParser.AliasInterfaceDefinitionContext): AliasInterfaceDefinitionNode {
        return AliasInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            aliasedInterface = visitInterfaceExpression(ctx.interfaceExpression()),
        )
    }

    override fun visitRecordInterfaceDefinition(ctx: GAPLParser.RecordInterfaceDefinitionContext): RecordInterfaceDefinitionNode {
        return RecordInterfaceDefinitionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = visitGenericInterfaceDefinitionList(ctx.genericInterfaceDefinitionList()).interfaces,
            genericParameters = visitGenericParameterDefinitionList(ctx.genericParameterDefinitionList()).parameters,
            inherits = ctx.inheritList()?.let { visitInheritList(it).inherits } ?: emptyList(),
            ports = visitPortDefinitionList(ctx.portDefinitionList()).ports,
        )
    }

    override fun visitGenericInterfaceDefinitionList(ctx: GAPLParser.GenericInterfaceDefinitionListContext): GenericInterfaceDefinitionListNode {
        return GenericInterfaceDefinitionListNode(
            ctx.Id().map { GenericInterfaceDefinitionNode(TokenVisitor.visitId(it)) }
        )
    }

    override fun visitGenericParameterDefinitionList(ctx: GAPLParser.GenericParameterDefinitionListContext): GenericParameterDefinitionListNode {
        return GenericParameterDefinitionListNode(
            ctx.genericParameterDefinition().map { visitGenericParameterDefinition(it) }
        )
    }

    override fun visitGenericParameterDefinition(ctx: GAPLParser.GenericParameterDefinitionContext): GenericParameterDefinitionNode {
        return GenericParameterDefinitionNode(
            TokenVisitor.visitId(ctx.identifier),
            TokenVisitor.visitId(ctx.typeIdentifier),
        )
    }

    override fun visitInheritList(ctx: GAPLParser.InheritListContext): RecordInterfaceInheritListNode {
        return RecordInterfaceInheritListNode(
            ctx.interfaceExpression()
                .onEach { assert(it is GAPLParser.DefinedInterfaceExpressionContext) } // TODO: Raise syntax error
                .filterIsInstance<GAPLParser.DefinedInterfaceExpressionContext>()
                .map { visitDefinedInterfaceExpression(it) }
        )
    }

    override fun visitPortDefinitionList(ctx: GAPLParser.PortDefinitionListContext): RecordInterfacePortListNode {
        return RecordInterfacePortListNode(
            ctx.portDefinition().map { visitPortDefinition(it) }
        )
    }

    override fun visitPortDefinition(ctx: GAPLParser.PortDefinitionContext): RecordInterfacePortNode {
        return RecordInterfacePortNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            type = visitInterfaceExpression(ctx.interfaceExpression()),
        )
    }

    fun visitInterfaceExpression(ctx: GAPLParser.InterfaceExpressionContext): InterfaceExpressionNode {
        return when (ctx) {
            is GAPLParser.WireInterfaceExpressionContext -> visitWireInterfaceExpression(ctx)
            is GAPLParser.DefinedInterfaceExpressionContext -> visitDefinedInterfaceExpression(ctx)
            is GAPLParser.VectorInterfaceExpressionContext -> visitVectorInterfaceExpression(ctx)
            is GAPLParser.IdentifierInterfaceExpressionContext -> visitIdentifierInterfaceExpression(ctx)
            else -> throw Exception("Unknown interface")
        }
    }

    override fun visitWireInterfaceExpression(ctx: GAPLParser.WireInterfaceExpressionContext): WireInterfaceExpressionNode {
        return WireInterfaceExpressionNode
    }

    override fun visitDefinedInterfaceExpression(ctx: GAPLParser.DefinedInterfaceExpressionContext): DefinedInterfaceExpressionNode {
        val instantiation = visitInstantiation(ctx.instantiation())
        return DefinedInterfaceExpressionNode(
            interfaceIdentifier = instantiation.definitionIdentifier,
            genericInterfaces = instantiation.genericInterfaces,
            genericParameters = instantiation.genericParameters,
        )
    }

    override fun visitVectorInterfaceExpression(ctx: GAPLParser.VectorInterfaceExpressionContext): VectorInterfaceExpressionNode {
        return VectorInterfaceExpressionNode(
            vectoredInterface = visitInterfaceExpression(ctx.interfaceExpression()),
            boundsSpecifier = VectorBoundsNode(visitStaticExpression(ctx.staticExpression())),
        )
    }

    override fun visitIdentifierInterfaceExpression(ctx: GAPLParser.IdentifierInterfaceExpressionContext): IdentifierInterfaceExpressionNode {
        return IdentifierInterfaceExpressionNode(TokenVisitor.visitId(ctx.Id()))
    }

    override fun visitInstantiation(ctx: GAPLParser.InstantiationContext): InstantiationNode {
        return InstantiationNode(
            definitionIdentifier = TokenVisitor.visitId(ctx.Id()),
            genericInterfaces = ctx.genericInterfaceValueList()?.let { visitGenericInterfaceValueList(it).interfaces } ?: emptyList(),
            genericParameters = visitGenericParameterValueList(ctx.genericParameterValueList()).parameters,
        )
    }

    override fun visitGenericInterfaceValueList(ctx: GAPLParser.GenericInterfaceValueListContext): GenericInterfaceValueListNode {
        return GenericInterfaceValueListNode(
            ctx.interfaceExpression().map { GenericInterfaceValueNode(visitInterfaceExpression(it)) }
        )
    }

    override fun visitGenericParameterValueList(ctx: GAPLParser.GenericParameterValueListContext): GenericParameterValueListNode {
        return GenericParameterValueListNode(
            ctx.staticExpression().map { GenericParameterValueNode(visitStaticExpression(it)) }
        )
    }

    fun visitStaticExpression(ctx: GAPLParser.StaticExpressionContext): StaticExpressionNode {
        return when (ctx) {
            is GAPLParser.TrueStaticExpressionContext -> TrueStaticExpressionNode
            is GAPLParser.FalseStaticExpressionContext -> FalseStaticExpressionNode
            is GAPLParser.IntLiteralStaticExpressionContext -> IntegerLiteralStaticExpressionNode(TokenVisitor.visitIntegerLiteral(ctx.IntLiteral()))
            is GAPLParser.IdStaticExpressionContext -> IdentifierStaticExpressionNode(TokenVisitor.visitId(ctx.Id()))
            is GAPLParser.ParanStaticExpressionContext -> visitStaticExpression(ctx.staticExpression())
            is GAPLParser.AddStaticExpressionContext -> AdditionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.SubtractStaticExpressionContext -> SubtractionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.MultiplyStaticExpressionContext -> MultiplicationStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.DivideStaticExpressionContext -> DivisionStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.EqualsStaticExpressionContext -> EqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.NotEqualsStaticExpressionContext -> NotEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.LessThanStaticExpressionContext -> LessThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.GreaterThanStaticExpressionContext -> GreaterThanStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.LessThanEqualsStaticExpressionContext -> LessThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            is GAPLParser.GreaterThanEqualsStaticExpressionContext -> GreaterThanEqualsStaticExpressionNode(visitStaticExpression(ctx.lhs!!), visitStaticExpression(ctx.rhs!!))
            else -> throw Exception("Unknown static expression")
        }
    }
}