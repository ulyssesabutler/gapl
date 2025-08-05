package com.uabutler.ast.visitor

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
            is GAPLParser.CircuitNodeCreationExpressionContext -> visitCircuitNodeCreationExpression(ctx)
            else -> throw Exception("Unrecognized circuit expression")
        }
    }

    override fun visitCircuitNodeCreationExpression(ctx: GAPLParser.CircuitNodeCreationExpressionContext): CircuitNodeCreationExpressionNode {
        return CircuitNodeCreationExpressionNode(
            identifier = ctx.nodeIdentifier?.let { TokenVisitor.visitId(it) },
            interior = visitCircuitNodeInterior(ctx.circuitNodeInterior()),
        )
    }

    override fun visitCircuitNodeReferenceExpression(ctx: GAPLParser.CircuitNodeReferenceExpressionContext): CircuitNodeReferenceExpressionNode {
        return CircuitNodeReferenceExpressionNode(
            identifier = TokenVisitor.visitId(ctx.nodeIdentifier),
            singleAccesses = ctx.singleAccessOperation().map { visitSingleAccessOperation(it) },
            multipleAccess = ctx.multipleArrayAccessOperation()?.let { visitMultipleArrayAccessOperation(it) }
        )
    }

    override fun visitCircuitNodeLiteralExpression(ctx: GAPLParser.CircuitNodeLiteralExpressionContext): CircuitNodeLiteralExpressionNode {
        return CircuitNodeLiteralExpressionNode(
            literal = StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression())
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

    fun visitCircuitNodeInterior(ctx: GAPLParser.CircuitNodeInteriorContext): CircuitNodeInteriorNode {
        return when (ctx) {
            is GAPLParser.CircuitNodeFunctionInteriorContext -> visitCircuitNodeFunctionInterior(ctx)
            is GAPLParser.CircuitNodeInterfaceInteriorContext -> visitCircuitNodeInterfaceInterior(ctx)
            is GAPLParser.CircuitNodeInterfaceTransformerInteriorContext -> visitCircuitNodeInterfaceTransformerInterior(ctx)
            else -> throw Exception("Unrecognized circuit node interior")
        }
    }

    override fun visitCircuitNodeFunctionInterior(ctx: GAPLParser.CircuitNodeFunctionInteriorContext): CircuitNodeFunctionInteriorNode {
        return CircuitNodeFunctionInteriorNode(
            function = FunctionVisitor.visitFunctionExpression(ctx.functionExpression())
        )
    }

    override fun visitCircuitNodeInterfaceInterior(ctx: GAPLParser.CircuitNodeInterfaceInteriorContext): CircuitNodeInterfaceInteriorNode {
        return CircuitNodeInterfaceInteriorNode(
            interfaceExpression = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression())
        )
    }

    override fun visitCircuitNodeInterfaceTransformerInterior(ctx: GAPLParser.CircuitNodeInterfaceTransformerInteriorContext): CircuitNodeInterfaceTransformerInteriorNode {
        return CircuitNodeInterfaceTransformerInteriorNode(
            interfaceExpression = InterfaceVisitor.visitInterfaceExpression(ctx.interfaceExpression()),
            interfaceTransformer = visitCircuitNodeInterfaceTransformer(ctx.circuitNodeInterfaceTransformer()),
            mode = UtilityVisitor.visitTransformerMode(ctx.transfomerMode()),
        )
    }

    fun visitCircuitNodeInterfaceTransformer(ctx: GAPLParser.CircuitNodeInterfaceTransformerContext): CircuitNodeInterfaceTransformerNode {
        return when (ctx) {
            is GAPLParser.CircuitNodeInterfaceRecordTransformerContext -> visitCircuitNodeInterfaceRecordTransformer(ctx)
            is GAPLParser.CircuitNodeInterfaceListTransformerContext -> visitCircuitNodeInterfaceListTransformer(ctx)
            else -> throw Exception("Unrecognized interface transformer")
        }
    }

    override fun visitCircuitNodeInterfaceRecordTransformer(ctx: GAPLParser.CircuitNodeInterfaceRecordTransformerContext): CircuitNodeInterfaceRecordTransformerNode {
        return CircuitNodeInterfaceRecordTransformerNode(
            expressions = ctx.circuitNodeRecordTransformerExpression().map { visitCircuitNodeRecordTransformerExpression(it) }
        )
    }

    override fun visitCircuitNodeInterfaceListTransformer(ctx: GAPLParser.CircuitNodeInterfaceListTransformerContext): CircuitNodeInterfaceListTransformerNode {
        return CircuitNodeInterfaceListTransformerNode(
            expressions = ctx.circuitNodeListTransformerExpression().map { visitCircuitNodeListTransformerExpression(it) }
        )
    }

    override fun visitCircuitNodeRecordTransformerExpression(ctx: GAPLParser.CircuitNodeRecordTransformerExpressionContext): CircuitNodeRecordTransformerExpressionNode {
        return CircuitNodeRecordTransformerExpressionNode(
            port = TokenVisitor.visitId(ctx.portIdentifier),
            expression = visitCircuitExpression(ctx.circuitExpression()),
        )
    }

    override fun visitCircuitNodeListTransformerExpression(ctx: GAPLParser.CircuitNodeListTransformerExpressionContext): CircuitNodeListTransformerExpressionNode {
        return CircuitNodeListTransformerExpressionNode(
            index = StaticExpressionVisitor.visitStaticExpression(ctx.staticExpression()),
            expression = visitCircuitExpression(ctx.circuitExpression()),
        )
    }

}