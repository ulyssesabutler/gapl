package com.uabutler.v2.ast.visitor

import com.uabutler.parsers.generated.GAPLParser
import com.uabutler.v2.ast.node.functions.circuits.*

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
            is GAPLParser.IdentifierCircuitExpressionContext -> visitIdentifierCircuitExpression(ctx)
            is GAPLParser.AnonymousNodeCircuitExpressionContext -> visitAnonymousNodeCircuitExpression(ctx)
            is GAPLParser.DeclaredInterfaceCircuitExpressionContext -> visitDeclaredInterfaceCircuitExpression(ctx)
            is GAPLParser.ReferenceCircuitExpressionContext -> visitReferenceCircuitExpression(ctx)
            is GAPLParser.ParanCircuitExpressionContext -> visitParanCircuitExpression(ctx)
            is GAPLParser.RecordInterfaceConstructorCircuitExpressionContext -> visitRecordInterfaceConstructorCircuitExpression(ctx)
            else -> throw Exception("Unrecognized circuit expression")
        }
    }

    override fun visitIdentifierCircuitExpression(ctx: GAPLParser.IdentifierCircuitExpressionContext): IdentifierCircuitExpressionNode {
        return IdentifierCircuitExpressionNode(TokenVisitor.visitId(ctx.Id()))
    }

    override fun visitAnonymousNodeCircuitExpression(ctx: GAPLParser.AnonymousNodeCircuitExpressionContext): AnonymousNodeCircuitExpressionNode {
        return AnonymousNodeCircuitExpressionNode(InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()))
    }

    override fun visitDeclaredInterfaceCircuitExpression(ctx: GAPLParser.DeclaredInterfaceCircuitExpressionContext): DeclaredNodeCircuitExpressionNode {
         return DeclaredNodeCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            type = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()),
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