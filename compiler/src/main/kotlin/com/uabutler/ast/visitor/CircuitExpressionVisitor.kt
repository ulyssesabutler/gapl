package com.uabutler.ast.visitor

import com.uabutler.ast.node.GAPLNode
import com.uabutler.ast.node.functions.circuits.*
import com.uabutler.parsers.generated.GAPLParser

object CircuitExpressionVisitor: GAPLVisitor() {

    override fun visitCircuitExpression(ctx: GAPLParser.CircuitExpressionContext): CircuitExpressionNode {
        return visitCircuitConnectorExpression(ctx.circuitConnectorExpression())
    }

    override fun visitCircuitConnectorExpression(ctx: GAPLParser.CircuitConnectorExpressionContext): CircuitConnectionExpressionNode {
        return CircuitConnectionExpressionNode(
            connectedExpression = ctx.circuitGroupExpression().map { visitCircuitGroupExpression(it) }
        )
    }

    override fun visitCircuitGroupExpression(ctx: GAPLParser.CircuitGroupExpressionContext): CircuitGroupExpressionNode {
        return CircuitGroupExpressionNode(
            expressions = ctx.circuitNodeExpression().map { visitCircuitNodeExpression(it) }
        )
    }

    fun visitCircuitNodeExpression(ctx: GAPLParser.CircuitNodeExpressionContext): CircuitNodeExpressionNode {
        return when (ctx) {
            is GAPLParser.DeclaredInterfaceCircuitExpressionContext -> visitDeclaredInterfaceCircuitExpression(ctx)
            is GAPLParser.DeclaredFunctionCircuitExpressionContext -> visitDeclaredFunctionCircuitExpression(ctx)
            is GAPLParser.DeclaredGenericFunctionCircuitExpressionContext -> visitDeclaredGenericFunctionCircuitExpression(ctx)
            is GAPLParser.AnonymousFunctionCircuitExpressionContext -> visitAnonymousFunctionCircuitExpression(ctx)
            is GAPLParser.AnonymousGenericFunctionCircuitExpressionContext -> visitAnonymousGenericFunctionCircuitExpression(ctx)
            is GAPLParser.ReferenceCircuitExpressionContext -> visitReferenceCircuitExpression(ctx)
            is GAPLParser.ParanCircuitExpressionContext -> visitParanCircuitExpression(ctx)
            is GAPLParser.RecordInterfaceConstructorCircuitExpressionContext -> visitRecordInterfaceConstructorCircuitExpression(ctx)
            else -> throw Exception("Unrecognized circuit expression")
        }
    }

    override fun visitDeclaredInterfaceCircuitExpression(ctx: GAPLParser.DeclaredInterfaceCircuitExpressionContext): DeclaredInterfaceCircuitExpressionNode {
         return DeclaredInterfaceCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.nodeIdentifier),
            type = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()),
        )
    }

    override fun visitDeclaredFunctionCircuitExpression(ctx: GAPLParser.DeclaredFunctionCircuitExpressionContext): DeclaredFunctionCircuitExpressionNode {
        return DeclaredFunctionCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.nodeIdentifier),
            instantiation = UtilityVisitor.visitInstantiation(ctx.instantiation()),
        )
    }

    override fun visitDeclaredGenericFunctionCircuitExpression(ctx: GAPLParser.DeclaredGenericFunctionCircuitExpressionContext): DeclaredGenericFunctionCircuitExpressionNode {
        return DeclaredGenericFunctionCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.nodeIdentifier),
            functionIdentifier = TokenVisitor.visitId(ctx.functionIdentifier),
        )
    }

    override fun visitAnonymousFunctionCircuitExpression(ctx: GAPLParser.AnonymousFunctionCircuitExpressionContext): AnonymousFunctionCircuitExpressionNode {
        return AnonymousFunctionCircuitExpressionNode(
            instantiation = UtilityVisitor.visitInstantiation(ctx.instantiation()),
        )
    }

    override fun visitAnonymousGenericFunctionCircuitExpression(ctx: GAPLParser.AnonymousGenericFunctionCircuitExpressionContext): AnonymousGenericFunctionCircuitExpressionNode {
        return AnonymousGenericFunctionCircuitExpressionNode(
            functionIdentifier = TokenVisitor.visitId(ctx.functionIdentifier),
        )
    }

    override fun visitReferenceCircuitExpression(ctx: GAPLParser.ReferenceCircuitExpressionContext): ReferenceCircuitExpressionNode {
        return ReferenceCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            singleAccesses = ctx.singleAccessOperation().map { visitSingleAccessOperation(it) },
            multipleAccess = ctx.multipleArrayAccessOperation()?.let { visitMultipleArrayAccessOperation(it) },
        )
    }

    override fun visitParanCircuitExpression(ctx: GAPLParser.ParanCircuitExpressionContext): CircuitExpressionNodeCircuitExpression {
        return CircuitExpressionNodeCircuitExpression(visitCircuitExpression(ctx.circuitExpression()))
    }

    override fun visitRecordInterfaceConstructorCircuitExpression(ctx: GAPLParser.RecordInterfaceConstructorCircuitExpressionContext): RecordInterfaceConstructorExpressionNode {
        return RecordInterfaceConstructorExpressionNode(
            statements = ctx.circuitStatement().map { CircuitStatementVisitor.visitCircuitStatement(it) }
        )
    }

    fun visitSingleAccessOperation(ctx: GAPLParser.SingleAccessOperationContext): SingleAccessOperationNode {
        return when (ctx) {
            is GAPLParser.MemberAccessOperationContext -> visitMemberAccessOperation(ctx)
            is GAPLParser.SingleArrayAccessOperationContext -> visitSingleArrayAccessOperation(ctx)
            else -> throw Exception("Unrecognized access operation")
        }
    }

    override fun visitMemberAccessOperation(ctx: GAPLParser.MemberAccessOperationContext): MemberAccessOperationNode {
        return MemberAccessOperationNode(TokenVisitor.visitId(ctx.Id()))
    }

    override fun visitSingleArrayAccessOperation(ctx: GAPLParser.SingleArrayAccessOperationContext): SingleArrayAccessOperationNode {
        return SingleArrayAccessOperationNode(StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression()))
    }

    override fun visitMultipleArrayAccessOperation(ctx: GAPLParser.MultipleArrayAccessOperationContext): MultipleAccessOperationNode {
        return MultipleAccessOperationNode(
            startIndex = StaticExpressionVisitor.visitStaticExpression(ctx.startIndex!!),
            endIndex = StaticExpressionVisitor.visitStaticExpression(ctx.endIndex!!),
        )
    }

}