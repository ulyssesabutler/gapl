package com.uabutler.visitor

import com.uabutler.ast.functions.circuits.*
import com.uabutler.parsers.generated.GAPLParser

object CircuitExpressionVisitor: GAPLVisitor() {

    override fun visitCircuitExpression(ctx: GAPLParser.CircuitExpressionContext): CircuitExpressionNode {
        return visitCircuitConnectorExpression(ctx.circuitConnectorExpression())
    }

    override fun visitCircuitConnectorExpression(ctx: GAPLParser.CircuitConnectorExpressionContext): CircuitConnectionExpressionNode {
        val current = CircuitConnectionExpressionNode(
            connectedExpression = ctx.circuitGroupExpression().map { visitCircuitGroupExpression(it) }
        )
        current.connectedExpression.forEach { it.parent = current }
        return current
    }

    override fun visitCircuitGroupExpression(ctx: GAPLParser.CircuitGroupExpressionContext): CircuitGroupExpressionNode {
        val current = CircuitGroupExpressionNode(
            expressions = ctx.circuitNodeExpression().map { visitCircuitNodeExpression(it) }
        )
        current.expressions.forEach { it.parent = current }
        return current
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
        val current = IdentifierCircuitExpressionNode(TokenVisitor.visitId(ctx.Id()))
        current.identifier.parent = current
        return current
    }

    override fun visitAnonymousNodeCircuitExpression(ctx: GAPLParser.AnonymousNodeCircuitExpressionContext): AnonymousNodeCircuitExpressionNode {
        val current = AnonymousNodeCircuitExpressionNode(InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()))
        current.type.parent = current
        return current
    }

    override fun visitDeclaredInterfaceCircuitExpression(ctx: GAPLParser.DeclaredInterfaceCircuitExpressionContext): DeclaredNodeCircuitExpressionNode {
        val current = DeclaredNodeCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            type = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()),
        )
        current.identifier.parent = current
        current.type.parent = current
        return current
    }

    override fun visitReferenceCircuitExpression(ctx: GAPLParser.ReferenceCircuitExpressionContext): ReferenceCircuitExpressionNode {
        val current = ReferenceCircuitExpressionNode(
            identifier = TokenVisitor.visitId(ctx.Id()),
            singleAccesses = ctx.singleAccessOperation().map { visitSingleAccessOperation(it) },
            multipleAccess = ctx.multipleArrayAccessOperation()?.let { visitMultipleArrayAccessOperation(it) },
        )
        current.identifier.parent = current
        current.singleAccesses.forEach { it.parent = current }
        current.multipleAccess?.parent = current
        return current
    }

    override fun visitParanCircuitExpression(ctx: GAPLParser.ParanCircuitExpressionContext): CircuitExpressionNodeCircuitExpression {
        val current = CircuitExpressionNodeCircuitExpression(visitCircuitExpression(ctx.circuitExpression()))
        current.expression.parent = current
        return current
    }

    override fun visitRecordInterfaceConstructorCircuitExpression(ctx: GAPLParser.RecordInterfaceConstructorCircuitExpressionContext): RecordInterfaceConstructorExpressionNode {
        val current = RecordInterfaceConstructorExpressionNode(
            statements = ctx.circuitStatement().map { CircuitStatementVisitor.visitCircuitStatement(it) }
        )
        current.statements.forEach { it.parent = current }
        return current
    }

    fun visitSingleAccessOperation(ctx: GAPLParser.SingleAccessOperationContext): SingleAccessOperationNode {
        return when (ctx) {
            is GAPLParser.MemberAccessOperationContext -> visitMemberAccessOperation(ctx)
            is GAPLParser.SingleArrayAccessOperationContext -> visitSingleArrayAccessOperation(ctx)
            else -> throw Exception("Unrecognized access operation")
        }
    }

    override fun visitMemberAccessOperation(ctx: GAPLParser.MemberAccessOperationContext): MemberAccessOperationNode {
        val current = MemberAccessOperationNode(TokenVisitor.visitId(ctx.Id()))
        current.memberIdentifier.parent = current
        return current
    }

    override fun visitSingleArrayAccessOperation(ctx: GAPLParser.SingleArrayAccessOperationContext): SingleArrayAccessOperationNode {
        val current = SingleArrayAccessOperationNode(StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression()))
        current.index.parent = current
        return current
    }

    override fun visitMultipleArrayAccessOperation(ctx: GAPLParser.MultipleArrayAccessOperationContext): MultipleAccessOperationNode {
        val current = MultipleAccessOperationNode(
            startIndex = StaticExpressionVisitor.visitStaticExpression(ctx.startIndex!!),
            endIndex = StaticExpressionVisitor.visitStaticExpression(ctx.endIndex!!),
        )
        current.startIndex.parent = current
        current.endIndex.parent = current
        return current
    }

}