package parsing

import com.uabutler.Parser
import com.uabutler.ast.*
import com.uabutler.ast.interfaces.*
import com.uabutler.ast.staticexpressions.IntegerLiteralStaticExpressionNode
import com.uabutler.visitor.InterfaceVisitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterfaceTest {

    private fun parseInterfaceDefinition(input: String): InterfaceDefinitionNode {
        return InterfaceVisitor
            .visitInterfaceDefinition(
                Parser.fromString(input)
                    .interfaceDefinition()
            )
    }

    private fun testInterfaceDefinition(input: String, expected: InterfaceDefinitionNode) {
        assertEquals(expected, parseInterfaceDefinition(input))
    }

    @Test
    fun `parse a simple alias interface`() {
        testInterfaceDefinition(
            input = "interface test() wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse a simple record interface`() {
        testInterfaceDefinition(
            input = "interface test() { }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = emptyList(),
                ports = emptyList(),
            )
        )
    }

    @Test
    fun `parse interface with generic interface`() {
        testInterfaceDefinition(
            input = "interface test<T>() wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = listOf(GenericInterfaceDefinitionNode(IdentifierNode("T"))),
                genericParameters = emptyList(),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse interface with generic interface list`() {
        testInterfaceDefinition(
            input = "interface test<T, U>() wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = listOf(GenericInterfaceDefinitionNode(IdentifierNode("T")), GenericInterfaceDefinitionNode(IdentifierNode("U"))),
                genericParameters = emptyList(),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse interface with generic interface list and trailing comma`() {
        testInterfaceDefinition(
            input = "interface test<T, U,>() wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = listOf(GenericInterfaceDefinitionNode(IdentifierNode("T")), GenericInterfaceDefinitionNode(IdentifierNode("U"))),
                genericParameters = emptyList(),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse interface with generic parameter`() {
        testInterfaceDefinition(
            input = "interface test(parameter size: integer) wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = listOf(
                    GenericParameterDefinitionNode(IdentifierNode("size"), IdentifierNode("integer"))
                ),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse interface with generic parameter list`() {
        testInterfaceDefinition(
            input = "interface test(parameter size: integer, parameter width: integer) wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = listOf(
                    GenericParameterDefinitionNode(IdentifierNode("size"), IdentifierNode("integer")),
                    GenericParameterDefinitionNode(IdentifierNode("width"), IdentifierNode("integer")),
                ),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse interface with generic parameter list and trailing comma`() {
        testInterfaceDefinition(
            input = "interface test(parameter size: integer, parameter width: integer,) wire",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = listOf(
                    GenericParameterDefinitionNode(IdentifierNode("size"), IdentifierNode("integer")),
                    GenericParameterDefinitionNode(IdentifierNode("width"), IdentifierNode("integer")),
                ),
                aliasedInterface = WireInterfaceExpressionNode,
            )
        )
    }

    @Test
    fun `parse a alias interface with simple defined interface expression`() {
        testInterfaceDefinition(
            input = "interface test() another()",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                aliasedInterface = DefinedInterfaceExpressionNode(
                    interfaceIdentifier = IdentifierNode("another"),
                    genericInterfaces = emptyList(),
                    genericParameters = emptyList(),
                ),
            )
        )
    }

    @Test
    fun `parse a alias interface with simple vector interface expression`() {
        testInterfaceDefinition(
            input = "interface test() wire[10]",
            expected = AliasInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                aliasedInterface = VectorInterfaceExpressionNode(
                    vectoredInterface = WireInterfaceExpressionNode,
                    boundsSpecifier = VectorBoundsNode(IntegerLiteralStaticExpressionNode(IntegerLiteralNode(10)))
                ),
            )
        )
    }

    @Test
    fun `parse a record interface with a defined inherit`() {
        testInterfaceDefinition(
            input = "interface test(): parent() { }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = listOf(
                    DefinedInterfaceExpressionNode(
                        interfaceIdentifier = IdentifierNode("parent"),
                        genericInterfaces = emptyList(),
                        genericParameters = emptyList(),
                    ),
                ),
                ports = emptyList(),
            )
        )
    }

    @Test
    fun `parse a record interface with a defined inherit list`() {
        testInterfaceDefinition(
            input = "interface test(): mom(), dad() { }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = listOf(
                    DefinedInterfaceExpressionNode(
                        interfaceIdentifier = IdentifierNode("mom"),
                        genericInterfaces = emptyList(),
                        genericParameters = emptyList(),
                    ),
                    DefinedInterfaceExpressionNode(
                        interfaceIdentifier = IdentifierNode("dad"),
                        genericInterfaces = emptyList(),
                        genericParameters = emptyList(),
                    ),
                ),
                ports = emptyList(),
            )
        )
    }

    @Test
    fun `parse a record interface with a defined inherit list and trailing comma`() {
        testInterfaceDefinition(
            input = "interface test(): mom(), dad(), { }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = listOf(
                    DefinedInterfaceExpressionNode(
                        interfaceIdentifier = IdentifierNode("mom"),
                        genericInterfaces = emptyList(),
                        genericParameters = emptyList(),
                    ),
                    DefinedInterfaceExpressionNode(
                        interfaceIdentifier = IdentifierNode("dad"),
                        genericInterfaces = emptyList(),
                        genericParameters = emptyList(),
                    ),
                ),
                ports = emptyList(),
            )
        )
    }

    @Test
    fun `parse a record interface with a simple port`() {
        testInterfaceDefinition(
            input = "interface test() { a: wire; }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = emptyList(),
                ports = listOf(
                    RecordInterfacePortNode(
                        identifier = IdentifierNode("a"),
                        type = WireInterfaceExpressionNode,
                    ),
                ),
            )
        )
    }

    @Test
    fun `parse a record interface with a simple port list`() {
        testInterfaceDefinition(
            input = "interface test() { a: wire; b: wire; }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = emptyList(),
                ports = listOf(
                    RecordInterfacePortNode(
                        identifier = IdentifierNode("a"),
                        type = WireInterfaceExpressionNode,
                    ),
                    RecordInterfacePortNode(
                        identifier = IdentifierNode("b"),
                        type = WireInterfaceExpressionNode,
                    ),
                ),
            )
        )
    }

    @Test
    fun `parse a record interface with a defined port`() {
        testInterfaceDefinition(
            input = "interface test() { a: another(); }",
            expected = RecordInterfaceDefinitionNode(
                identifier = IdentifierNode("test"),
                genericInterfaces = emptyList(),
                genericParameters = emptyList(),
                inherits = emptyList(),
                ports = listOf(
                    RecordInterfacePortNode(
                        identifier = IdentifierNode("a"),
                        type = DefinedInterfaceExpressionNode(
                            interfaceIdentifier = IdentifierNode("another"),
                            genericInterfaces = emptyList(),
                            genericParameters = emptyList(),
                        ),
                    ),
                ),
            )
        )
    }

}