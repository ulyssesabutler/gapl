package parsing

import com.uabutler.Parser
import com.uabutler.ast.node.*
import com.uabutler.ast.node.functions.DefaultFunctionIOTypeNode
import com.uabutler.ast.node.functions.FunctionDefinitionNode
import com.uabutler.ast.node.functions.FunctionIONode
import com.uabutler.ast.node.interfaces.WireInterfaceExpressionNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FunctionParseTest {

    private fun parseFunctionDefinition(input: String): FunctionDefinitionNode {
        return Parser.fromString(input).functionDefinition()
    }

    private fun testFunctionDefinition(input: String, expected: FunctionDefinitionNode) {
        assertEquals(expected, parseFunctionDefinition(input))
    }

    @Test
    fun `parse a simple function`() {
        testFunctionDefinition(
            input = "function test() input: wire => output: wire { }",
            expected = FunctionDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("input"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                outputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("output"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                statements = emptyList(),
            )
        )
    }

    @Test
    fun `parse a function with a generic interface`() {
        testFunctionDefinition(
            input = "function test<T>() input: wire => output: wire { }",
            expected = FunctionDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = listOf(
                    GenericInterfaceDefinitionNode(IdentifierNode("T")),
                ),
                genericParameters = emptyList(),
                inputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("input"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                outputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("output"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                statements = emptyList(),
            )
        )
    }

    @Test
    fun `parse a function with a generic parameter`() {
        testFunctionDefinition(
            input = "function test(parameter size: integer) input: wire => output: wire { }",
            expected = FunctionDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = listOf(
                    GenericParameterDefinitionNode(
                        identifier = IdentifierNode("size"),
                        type = IdentifierGenericParameterTypeNode(IdentifierNode("integer")),
                    )
                ),
                inputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("input"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                outputFunctionIO = listOf(
                    FunctionIONode(
                        identifier = IdentifierNode("output"),
                        ioType = DefaultFunctionIOTypeNode(),
                        interfaceType = WireInterfaceExpressionNode(),
                    ),
                ),
                statements = emptyList(),
            )
        )
    }

}