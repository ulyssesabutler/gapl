package com.uabutler.diagnostics

sealed interface ResolverDiagnosticKind : DiagnosticKind {

    data class UnresolvedSymbol(val name: String) : ResolverDiagnosticKind {
        override val message get() = "Unresolved symbol '$name'"
    }

    data class Redeclaration(val name: String) : ResolverDiagnosticKind {
        override val message get() = "Redeclaration of '$name'"
    }

    data class CannotUseAsGenericParameterValue(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "'$identifierText' cannot be used as a generic parameter value"
    }

    data class UnexpectedStaticExpressionParameters(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "Unexpected parameters for '$identifierText' in static expression"
    }

    data object UnsupportedLogicalOperatorsInStaticExpression : ResolverDiagnosticKind {
        override val message get() = "Logical && / || are not supported in static expressions yet"
    }

    data object UnexpectedAccessorInStaticExpression : ResolverDiagnosticKind {
        override val message get() = "Unexpected accessor expression in static expression"
    }

    data object UnexpectedWireInStaticExpression : ResolverDiagnosticKind {
        override val message get() = "Unexpected use of 'wire' in static expression"
    }

    data object UnsupportedTransformer : ResolverDiagnosticKind {
        override val message get() = "Transformers are not supported yet"
    }

    data class InvalidDeclarationType(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "'$identifierText' cannot be declared with this type"
    }

    data object ExpectedCircuitExpressionGotValueExpression : ResolverDiagnosticKind {
        override val message get() =
            "This looks like a value expression (an integer, boolean, or arithmetic/comparison expression) - circuit expressions must reference a wire, an interface, or a function call instead"
    }

    data object UnexpectedAccessorOnCircuitExpression : ResolverDiagnosticKind {
        override val message get() =
            "Cannot use an accessor ('.', '[i]', or '[a:b]') here - accessors can only be applied to a circuit node reference or an interface expression"
    }

    data object UnexpectedAccessorOnAnonymousInterface : ResolverDiagnosticKind {
        override val message get() = "Unexpected accessor on an interface expression"
    }

    data object UnsupportedSliceAccess : ResolverDiagnosticKind {
        override val message get() = "Unexpected access operation on a slice - not yet supported"
    }

    data object UnsupportedProtocolAccessor : ResolverDiagnosticKind {
        override val message get() = "Protocol accessors are not supported yet"
    }

    data class UnexpectedCircuitNodeParameters(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "Unexpected parameters for '$identifierText' in circuit node expression"
    }

    data object UnexpectedAccessorInInterfaceExpression : ResolverDiagnosticKind {
        override val message get() = "Unexpected accessor in interface expression"
    }

    data class NotAnInterfaceTypedGenericParameter(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "'$identifierText' is not an interface-typed generic parameter"
    }

    data class UnexpectedGenericInterfaceParameters(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "Unexpected parameters for generic interface '$identifierText'"
    }

    data class CannotUseInInterfaceExpression(val identifierText: String) : ResolverDiagnosticKind {
        override val message get() = "Cannot use '$identifierText' in an interface expression"
    }

    data object ExpectedInterfaceExpression : ResolverDiagnosticKind {
        override val message get() = "Expected an interface expression"
    }
}
