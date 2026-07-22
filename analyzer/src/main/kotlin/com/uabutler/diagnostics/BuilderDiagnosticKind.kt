package com.uabutler.diagnostics

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
}
