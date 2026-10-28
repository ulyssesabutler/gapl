package com.uabutler.diagnostics

import com.uabutler.util.Protocol

sealed interface BuilderDiagnosticKind : DiagnosticKind {

    data class UnableToFindStaticExpressionValue(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Unable to find value for $identifierText"
    }

    data class StaticExpressionParameterNotInteger(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Parameter '$identifierText' is a function, not an integer, and cannot be used in a static expression"
    }

    data class WireCountMismatch(
        val gaplModuleName: String,
        val previousOutputNodeName: String,
        val previousCount: Int,
        val currentInputNodeName: String,
        val currentCount: Int,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "Bit width mismatch in $gaplModuleName of $previousOutputNodeName (width of $previousCount) to $currentInputNodeName (width of $currentCount)"
    }

    data class ExpectedModuleInstantiation(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() =
            "Generic parameter '$identifierText' is an integer, not a function, and cannot be used as a circuit node"
    }

    data class UnableToFindCircuitNode(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Unable to find node with identifier $identifierText"
    }

    data class CannotFindInterface(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Cannot find interface: $identifierText"
    }

    data class CannotFindInterfaceValue(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Cannot find interface value: $identifierText"
    }

    data class UnableToLocateFunction(val functionName: String) : BuilderDiagnosticKind {
        override val message get() = "Unable to locate function: $functionName"
    }

    data class GenericInterfaceArityMismatch(
        val functionName: String,
        val expected: Int,
        val actual: Int,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "Unable to match generic interface values for $functionName: expected $expected generic interface(s), got $actual"
    }

    data class GenericParameterArityMismatch(
        val functionName: String,
        val expected: Int,
        val actual: Int,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "Unable to match generic parameter values for $functionName: expected $expected generic parameter(s), got $actual"
    }

    data class UnableToFindFunctionReference(val identifierText: String) : BuilderDiagnosticKind {
        override val message get() = "Unable to find function reference '$identifierText'"
    }

    data class UndrivenOutputPort(val portName: String, val functionName: String) : BuilderDiagnosticKind {
        override val message get() =
            "Output '$portName' of function '$functionName' is never driven by anything inside the function"
    }

    data class UndrivenNodeInput(
        val nodeName: String,
        val nodeType: String,
        val inputName: String?,
        val functionName: String,
    ) : BuilderDiagnosticKind {
        override val message get() = buildString {
            append("Input ")
            if (inputName != null) append("'$inputName' ")
            append("of $nodeType '$nodeName' in function '$functionName' is never connected to anything")
        }
    }

    data class MultiplyDrivenOutputPort(val portName: String, val functionName: String) : BuilderDiagnosticKind {
        override val message get() =
            "Output '$portName' of function '$functionName' is driven by more than one connection"
    }

    data class MultiplyDrivenNodeInput(
        val nodeName: String,
        val nodeType: String,
        val inputName: String?,
        val functionName: String,
    ) : BuilderDiagnosticKind {
        override val message get() = buildString {
            append("Input ")
            if (inputName != null) append("'$inputName' ")
            append("of $nodeType '$nodeName' in function '$functionName' is connected more than once")
        }
    }

    data class CombinationalLoop(val involvedNodes: List<NodeInLoop>) : BuilderDiagnosticKind {
        data class NodeInLoop(val nodeName: String, val functionName: String)

        override val message get() =
            "Combinational loop involving: ${involvedNodes.joinToString(", ") { "${it.nodeName} (in ${it.functionName})" }} " +
            "(no register breaks the cycle)"
    }

    data class RecursiveInvocation(val involvedFunctions: List<String>) : BuilderDiagnosticKind {
        override val message get() =
            "Recursive function invocation involving: ${involvedFunctions.joinToString(", ")} " +
            "(a function cannot invoke itself, directly or indirectly, without changing its generic parameters - this would produce an infinitely large circuit)"
    }

    data class InvalidViewFieldPath(val viewName: String, val fieldName: String, val reason: String) : BuilderDiagnosticKind {
        override val message get() = "Field '$fieldName' of view '$viewName' does not name bits of its base: $reason"
    }

    data class OverlappingViewFields(val viewName: String, val firstField: String, val secondField: String) : BuilderDiagnosticKind {
        override val message get() =
            "Fields '$firstField' and '$secondField' of view '$viewName' overlap - fields within one view must name disjoint bits " +
            "(write overlapping interpretations as separate views of the same base)"
    }

    data class InvalidViewConversion(val conversion: String, val reason: String) : BuilderDiagnosticKind {
        override val message get() = "Invalid use of '$conversion': $reason"
    }

    data class InvalidForkCall(val fork: String, val reason: String) : BuilderDiagnosticKind {
        override val message get() = "Invalid use of '$fork': $reason"
    }

    // ---- Layer-3 protocol checks (type-system.md §5). Protocols print as "@valid" etc. ----

    data class IllegalProtocolCall(
        val functionName: String,
        val functionProtocol: Protocol,
        val calleeName: String,
        val calleeProtocol: Protocol,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "$functionProtocol function '$functionName' cannot call $calleeProtocol function '$calleeName' - " +
            "a callee's protocol must be at or below its caller's (@pure < @valid < @ready_valid)"
    }

    data class MissingCoercion(
        val functionName: String,
        val functionProtocol: Protocol,
        val calleeName: String,
        val calleeProtocol: Protocol,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "Calling $calleeProtocol function '$calleeName' from $functionProtocol function '$functionName' creates an island, " +
            "which must be marked: write '$calleeName(...)$functionProtocol'"
    }

    data class CoercionProtocolMismatch(
        val functionName: String,
        val functionProtocol: Protocol,
        val written: Protocol,
    ) : BuilderDiagnosticKind {
        override val message get() =
            "An island in $functionProtocol function '$functionName' is marked $written - " +
            "a call-site annotation always names the caller's own protocol, $functionProtocol"
    }

    data class UnnecessaryCoercion(val functionName: String, val calleeName: String, val protocol: Protocol) : BuilderDiagnosticKind {
        override val message get() =
            "'$calleeName' is already $protocol, like '$functionName', so this call is not an island - remove the annotation"
    }

    data class ValidFunctionHasMultipleInputs(val functionName: String, val inputCount: Int) : BuilderDiagnosticKind {
        override val message get() =
            "@valid function '$functionName' has $inputCount input ports - a @valid function may have at most one, " +
            "since firing on several would be a join (bundle them into one record, or use @ready_valid)"
    }

    data class JoinInValidFunction(val functionName: String, val calleeName: String, val inputCount: Int) : BuilderDiagnosticKind {
        override val message get() =
            "'$calleeName' takes $inputCount input channels, which is a join - not allowed inside @valid function '$functionName' " +
            "(put the fork-and-join inside a single-input @pure island)"
    }

    data class ImplicitFork(val functionName: String, val channel: String, val consumerCount: Int) : BuilderDiagnosticKind {
        override val message get() =
            "Channel '$channel' feeds $consumerCount consumers in @ready_valid function '$functionName' - " +
            "forks must be explicit there: use fork_eager(...) or fork_lazy(...)"
    }

    data class ImplicitJoin(val functionName: String, val sink: String, val channelCount: Int) : BuilderDiagnosticKind {
        override val message get() =
            "In '$functionName', $sink is built from $channelCount different channels, which is a join - " +
            "only an island may join (do the combining inside a @pure function and call it as an island)"
    }

    data class NotYetSupported(val feature: String) : BuilderDiagnosticKind {
        override val message get() = "Not yet supported: $feature"
    }

    data class ProtocolFunctionArgument(val functionName: String, val protocol: Protocol) : BuilderDiagnosticKind {
        override val message get() =
            "Only @pure functions can be passed as arguments, but '$functionName' is $protocol"
    }

    data class IslandContainsRegister(val calleeName: String, val registerFunctionName: String) : BuilderDiagnosticKind {
        override val message get() =
            "'$calleeName' cannot be an island because it contains a register (in '$registerFunctionName') - " +
            "an island must be combinational; pipelining comes from retiming"
    }
}
