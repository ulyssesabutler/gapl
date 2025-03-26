package parsing

import com.uabutler.Parser
import com.uabutler.ast.node.IdentifierNode
import com.uabutler.ast.node.IntegerLiteralNode
import com.uabutler.ast.node.staticexpressions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StaticExpressionParseTest {

    private fun parseStaticExpression(input: String): StaticExpressionNode {
        return Parser.fromString(input).staticExpression()
    }

    private fun testStaticExpression(input: String, expected: StaticExpressionNode) {
        assertEquals(expected, parseStaticExpression(input))
    }

    @Test
    fun `parse an integer`() {
        testStaticExpression(
            input = "5",
            expected = IntegerLiteralStaticExpressionNode(IntegerLiteralNode(5))
        )
    }

    @Test
    fun `parse an identifier`() {
        testStaticExpression(
            input = "a",
            expected = IdentifierStaticExpressionNode(IdentifierNode("a"))
        )
    }

    @Test
    fun `parse true`() {
        testStaticExpression(
            input = "true",
            expected = TrueStaticExpressionNode()
        )
    }

    @Test
    fun `parse false`() {
        testStaticExpression(
            input = "false",
            expected = FalseStaticExpressionNode()
        )
    }

    @Test
    fun `parse addition`() {
        testStaticExpression(
            input = "1 + 2",
            expected = AdditionStaticExpressionNode(
                lhs = IntegerLiteralStaticExpressionNode(IntegerLiteralNode(1)),
                rhs = IntegerLiteralStaticExpressionNode(IntegerLiteralNode(2)),
            )
        )
    }

    @Test
    fun `parse subtraction`() {
        testStaticExpression(
            input = "2 - 1",
            expected = SubtractionStaticExpressionNode(
                lhs = IntegerLiteralStaticExpressionNode(IntegerLiteralNode(2)),
                rhs = IntegerLiteralStaticExpressionNode(IntegerLiteralNode(1)),
            )
        )
    }
}