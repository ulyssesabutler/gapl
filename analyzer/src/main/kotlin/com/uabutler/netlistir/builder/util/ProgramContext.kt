package com.uabutler.netlistir.builder.util

import com.uabutler.ast.node.FunctionInstantiationGenericParameterValueNode
import com.uabutler.ast.node.InstantiationNode
import com.uabutler.ast.node.ProgramNode
import com.uabutler.ast.node.functions.circuits.MemberAccessOperationNode
import com.uabutler.ast.node.functions.circuits.SingleArrayAccessOperationNode
import com.uabutler.ast.node.interfaces.*
import com.uabutler.ast.node.staticexpressions.StaticExpressionNode
import com.uabutler.diagnostics.BuilderDiagnosticKind
import com.uabutler.netlistir.netlist.Module
import com.uabutler.util.PredefinedFunctionNames
import com.uabutler.util.Protocol
import java.math.BigInteger

class ProgramContext(program: ProgramNode) {

    private val interfaceDefinitions = program.interfaces
        .map { AbstractInterfaceDefinition.fromNode(it) }
        .associateBy { it.signature.identifier }

    private val functionProtocols = program.functions.associate { it.identifier.value to it.protocol }

    /** A function's layer-3 protocol. Primitives are @pure, except the explicit forks, which only make sense in @ready_valid. */
    fun protocolOf(functionName: String): Protocol = functionProtocols[functionName]
        ?: when (PredefinedFunctionNames.from(functionName)) {
            PredefinedFunctionNames.FORK_EAGER, PredefinedFunctionNames.FORK_LAZY -> Protocol.READY_VALID
            else -> Protocol.PURE
        }

    /* TODO: We should eventually use this for validation
    private val interfaceSignatures = program.interfaces
        .map { InterfaceSignature.fromNode(it) }
        .associateBy { it.identifier }
    private val moduleSignatures = program.functions
        .map { ModuleSignature.fromNode(it) }
        .associateBy { it.identifier }
     */

    private data class DefinitionInContext(
        val definition: InterfaceDefinitionNode,
        val interfaceValues: Map<String, InterfaceStructure>,
        val parameterValues: Map<String, ParameterValue<*>>,
    )

    private fun definitionInContext(
        node: InterfaceExpressionNode,
        definedInterfaceIdentifier: String,
        genericInterfaceValues: List<InterfaceStructure>,
        genericParameterValues: List<ParameterValue<*>>,
    ): DefinitionInContext {
        val interfaceDefinition = try {
            interfaceDefinitions[definedInterfaceIdentifier]!!.node
        } catch (_: NullPointerException) {
            throw BuilderDiagnosticException(BuilderDiagnosticKind.CannotFindInterface(definedInterfaceIdentifier), node.span)
        }

        // Match the provided values with the local identifier
        val interfaceValues =
            GenericValueMatcher.getInterfaceValues(interfaceDefinition.genericInterfaces, genericInterfaceValues)
        val parameterValues =
            GenericValueMatcher.getParameterValues(interfaceDefinition.genericParameters, genericParameterValues)

        return DefinitionInContext(interfaceDefinition, interfaceValues, parameterValues)
    }

    private fun definitionInContext(
        node: DefinedInterfaceExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ) = definitionInContext(
        node = node,
        definedInterfaceIdentifier = node.interfaceIdentifier.value,
        genericInterfaceValues = node.genericInterfaces.map {
            buildInterfaceWithContext(
                node = it.value,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        },
        genericParameterValues = node.genericParameters.map { ParameterValue.fromNode(it, this, interfaceValuesContext, parameterValuesContext) },
    )

    private fun buildDefinedInterface(
        node: InterfaceExpressionNode,
        definedInterfaceIdentifier: String,

        genericInterfaceValues: List<InterfaceStructure>,
        genericParameterValues: List<ParameterValue<*>>,
    ): InterfaceStructure {
        val (interfaceDefinition, interfaceValues, parameterValues) =
            definitionInContext(node, definedInterfaceIdentifier, genericInterfaceValues, genericParameterValues)

        return when (interfaceDefinition) {
            is AliasInterfaceDefinitionNode -> buildInterfaceWithContext(
                node = interfaceDefinition.aliasedInterface,
                interfaceValuesContext = interfaceValues,
                parameterValuesContext = parameterValues,
            )
            is RecordInterfaceDefinitionNode -> {
                // TODO: Inherits
                val ports = interfaceDefinition.ports.map {
                    it.identifier.value to it.type
                }
                    .associate { it }
                    .mapValues {
                        buildInterfaceWithContext(
                            node = it.value,
                            interfaceValuesContext = interfaceValues,
                            parameterValuesContext = parameterValues,
                        )
                    }

                RecordInterfaceStructure(ports)
            }
            is ViewInterfaceDefinitionNode -> buildViewLayout(interfaceDefinition, interfaceValues, parameterValues).structure
        }
        // TODO: Validation, types and number
    }

    private fun buildViewLayout(
        view: ViewInterfaceDefinitionNode,
        interfaceValues: Map<String, InterfaceStructure>,
        parameterValues: Map<String, ParameterValue<*>>,
    ): ViewLayout {
        val viewName = view.identifier.value
        val base = buildInterfaceWithContext(view.base, interfaceValues, parameterValues)

        val fields = linkedMapOf<String, InterfaceStructure>()
        val baseBits = mutableListOf<Int>()
        val claimedBy = mutableMapOf<Int, String>()

        view.fields.forEach { field ->
            val fieldName = field.identifier.value
            fun invalid(reason: String): Nothing =
                throw BuilderDiagnosticException(BuilderDiagnosticKind.InvalidViewFieldPath(viewName, fieldName, reason), field.span)
            fun evaluate(expression: StaticExpressionNode) =
                StaticExpressionEvaluator.evaluateStaticExpressionWithContext(expression, parameterValues).intValueExact()

            if (fieldName in fields) invalid("the view already has a field named '$fieldName'")

            // Walk the path in source order to find the field's type, collecting the same
            // (members, indices, range) decomposition a circuit reference's projection uses.
            var current = base
            val members = mutableListOf<String>()
            val indices = mutableListOf<Int>()

            field.singleAccesses.forEach { access ->
                when (access) {
                    is MemberAccessOperationNode -> {
                        val member = access.memberIdentifier.value
                        val record = current as? RecordInterfaceStructure
                            ?: invalid("'.$member' is applied to something that is not a record")
                        current = record.ports[member] ?: invalid("there is no member '$member'")
                        members += member
                    }
                    is SingleArrayAccessOperationNode -> {
                        val vector = current as? VectorInterfaceStructure
                            ?: invalid("an index is applied to something that is not a vector")
                        val index = evaluate(access.index)
                        if (index !in 0 until vector.size) invalid("index $index is out of range for a vector of size ${vector.size}")
                        current = vector.vectoredInterface
                        indices += index
                    }
                }
            }

            val range = field.multipleAccess?.let { slice ->
                val vector = current as? VectorInterfaceStructure
                    ?: invalid("a slice is applied to something that is not a vector")
                val start = evaluate(slice.startIndex)
                val end = evaluate(slice.endIndex)
                if (start < 0 || end >= vector.size || start > end) {
                    invalid("slice [$start:$end] is out of range for a vector of size ${vector.size}")
                }
                current = VectorInterfaceStructure(vector.vectoredInterface, end - start + 1)
                start..end
            }

            val bits = ViewLayout.selectedBits(base, members, indices, range)
            bits.forEach { bit ->
                claimedBy.put(bit, fieldName)?.let { earlier ->
                    throw BuilderDiagnosticException(BuilderDiagnosticKind.OverlappingViewFields(viewName, earlier, fieldName), field.span)
                }
            }

            fields[fieldName] = current
            baseBits += bits
        }

        return ViewLayout(base, RecordInterfaceStructure(fields), baseBits)
    }

    /** The layout of the view an interface expression names, following aliases, or null if it isn't one. */
    private fun viewLayoutOf(
        node: InterfaceExpressionNode,
        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): ViewLayout? {
        if (node !is DefinedInterfaceExpressionNode) return null
        val (definition, interfaceValues, parameterValues) = definitionInContext(node, interfaceValuesContext, parameterValuesContext)
        return when (definition) {
            is ViewInterfaceDefinitionNode -> buildViewLayout(definition, interfaceValues, parameterValues)
            is AliasInterfaceDefinitionNode -> viewLayoutOf(definition.aliasedInterface, interfaceValues, parameterValues)
            is RecordInterfaceDefinitionNode -> null
        }
    }

    fun buildInterfaceWithContext(
        node: InterfaceExpressionNode,

        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): InterfaceStructure {
        return when (node) {
            is WireInterfaceExpressionNode -> WireInterfaceStructure
            is VectorInterfaceExpressionNode -> VectorInterfaceStructure(
                vectoredInterface = buildInterfaceWithContext(
                    node = node.vectoredInterface,
                    interfaceValuesContext = interfaceValuesContext,
                    parameterValuesContext = parameterValuesContext,
                ),
                size = StaticExpressionEvaluator.evaluateStaticExpressionWithContext(
                    staticExpression = node.boundsSpecifier.boundSpecifier,
                    context = parameterValuesContext,
                ).intValueExact(),
            )
            is IdentifierInterfaceExpressionNode -> {
                try {
                    interfaceValuesContext[node.interfaceIdentifier.value]!!
                } catch (_: NullPointerException) {
                    throw BuilderDiagnosticException(BuilderDiagnosticKind.CannotFindInterfaceValue(node.interfaceIdentifier.value), node.span)
                }
            }
            is DefinedInterfaceExpressionNode -> buildDefinedInterface(
                node = node,
                definedInterfaceIdentifier = node.interfaceIdentifier.value,
                genericInterfaceValues = node.genericInterfaces.map {
                    buildInterfaceWithContext(
                        node = it.value,
                        interfaceValuesContext = interfaceValuesContext,
                        parameterValuesContext = parameterValuesContext,
                    )
                },
                genericParameterValues = node.genericParameters.map { ParameterValue.fromNode(it, this, interfaceValuesContext, parameterValuesContext) },
            )
            is ErrorInterfaceExpressionNode -> throw IllegalStateException(
                "Reached NodeBuilder with an error node (${node.message}) that should have been caught by semantic analysis - this is a compiler bug"
            )
        }
    }

    fun buildModuleInvocationDataWithContext(
        node: InstantiationNode,

        interfaceValuesContext: Map<String, InterfaceStructure>,
        parameterValuesContext: Map<String, ParameterValue<*>>,
    ): Module.Invocation {

        // This supports positional arguments
        val interfaces = node.genericInterfaces.map {
            buildInterfaceWithContext(
                node = it.value,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        }

        // This supports positional arguments
        val parameterValues = node.genericParameters.map {
            ParameterValue.fromNode(
                node = it,
                programContext = this,
                interfaceValuesContext = interfaceValuesContext,
                parameterValuesContext = parameterValuesContext,
            )
        }

        val functionName = node.definitionIdentifier.value
        fun invalid(reason: String): Nothing =
            throw BuilderDiagnosticException(BuilderDiagnosticKind.InvalidViewConversion(functionName, reason), node.span)

        // Function-typed parameters are @pure (type-system.md §5), so only @pure functions may be passed.
        node.genericParameters.filterIsInstance<FunctionInstantiationGenericParameterValueNode>().forEach { argument ->
            val argumentName = argument.instantiation.definitionIdentifier.value
            val argumentProtocol = protocolOf(argumentName)
            if (argumentProtocol != Protocol.PURE) {
                throw BuilderDiagnosticException(
                    BuilderDiagnosticKind.ProtocolFunctionArgument(argumentName, argumentProtocol),
                    argument.span,
                )
            }
        }

        when (PredefinedFunctionNames.from(functionName)) {
            PredefinedFunctionNames.PROJECT, PredefinedFunctionNames.OVERLAY -> {
                if (interfaces.size != 2 || parameterValues.isNotEmpty()) {
                    invalid("expected exactly two interfaces, (base, view), and no parameters")
                }
                val layout = viewLayoutOf(node.genericInterfaces[1].value, interfaceValuesContext, parameterValuesContext)
                    ?: invalid("its second interface must name a view")
                // Structural, not nominal: generic parameters have lost their names by now, and nominal
                // interface checking is deferred (type-system.md §10 item 18).
                if (layout.base != interfaces[0]) invalid("the view's base does not match the first interface")

                return Module.Invocation(
                    gaplFunctionName = functionName,
                    interfaces = interfaces,
                    parameters = ViewLayout.encode(layout.baseBits),
                )
            }
            PredefinedFunctionNames.ZEROS -> {
                if (interfaces.size != 1 || parameterValues.isNotEmpty()) {
                    invalid("expected exactly one interface and no parameters")
                }
            }
            PredefinedFunctionNames.FORK_EAGER, PredefinedFunctionNames.FORK_LAZY -> {
                val count = (parameterValues.singleOrNull() as? IntegerParameterValue)?.value
                if (interfaces.size != 1 || count == null || count < BigInteger.ONE) {
                    throw BuilderDiagnosticException(
                        BuilderDiagnosticKind.InvalidForkCall(functionName, "expected one interface and a positive branch count, e.g. $functionName(T, 2)"),
                        node.span,
                    )
                }
            }
            else -> {}
        }

        return Module.Invocation(
            gaplFunctionName = functionName,
            interfaces = interfaces,
            parameters = parameterValues,
        )
    }
}