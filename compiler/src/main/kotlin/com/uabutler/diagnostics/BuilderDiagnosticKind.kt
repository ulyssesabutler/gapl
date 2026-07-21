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
            "Mismatch in $gaplModuleName of $previousOutputNodeName ($previousCount) to $currentInputNodeName ($currentCount)"
    }

    data object ExpectedModuleInstantiation : BuilderDiagnosticKind {
        override val message get() = "Expected module instantiation"
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
}
