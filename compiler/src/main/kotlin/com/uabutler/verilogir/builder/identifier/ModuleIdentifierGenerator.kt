package com.uabutler.verilogir.builder.identifier

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.RecordInterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure
import com.uabutler.gaplir.builder.util.FunctionInstantiationParameterValue
import com.uabutler.gaplir.builder.util.IntegerParameterValue
import com.uabutler.gaplir.builder.util.ParameterValue
import com.uabutler.gaplir.util.ModuleInvocation

object ModuleIdentifierGenerator {

    /* Disambiguator
     *
     * Verilog allows character, numbers, underscores, and dollar signs in identifiers. GAPL is a bit more restrictive,
     * GAPL does not allow dollar signs in the identifier, so we'll use that as a way to guarantee our identifiers are
     * constructed "safely."
     *
     * First, we'll use a dollar sign to separate the user written part of the identifier (for readability of the
     * generated verilog to aid with debugging the compiler). Then, in the compiler generated portion, we'll essentially
     * use _ and $ as grouping symbols, with _ being opening and $ closing.
     *
     * So, you can interpret
     *   vector_map$i_v__w$4$$i_v__w$4$$p_5$p_f_$$
     * as
     *   vector_map $ i(v((w)4)) i(v((w)4)) p(5) p(f())
     */

    private fun generateInterfaceDisambiguator(interfaceStructure: InterfaceStructure): String {

        return when (interfaceStructure) {
            is WireInterfaceStructure -> "w"

            is RecordInterfaceStructure -> buildString {
                append("r")
                append("_")
                interfaceStructure.ports.values.forEach { append("_${generateInterfaceDisambiguator(it)}$") }
                append("$")
            }

            is VectorInterfaceStructure -> buildString {
                append("v")
                append("_")
                append("_${generateInterfaceDisambiguator(interfaceStructure.vectoredInterface)}$")
                append("${interfaceStructure.size}")
                append("$")
            }
        }

    }

    private fun generateParameterDisambiguator(parameterValue: ParameterValue<*>): String {
        return when (parameterValue) {
            is IntegerParameterValue -> parameterValue.value.toString()
            is FunctionInstantiationParameterValue -> buildString {
                append("f")
                append("_")
                append(
                    genIdentifierFromInvocation(
                        ModuleInvocation(
                            gaplFunctionName = parameterValue.value.functionIdentifier,
                            interfaces = parameterValue.value.genericInterfaceValues,
                            parameters = parameterValue.value.genericParameterValues,
                        )
                    )
                )
                append("$")
            }
        }

    }

    fun genIdentifierFromInvocation(invocation: ModuleInvocation): String {
        return buildString {
            append(invocation.gaplFunctionName)
            if (invocation.interfaces.isNotEmpty() || invocation.parameters.isNotEmpty()) {
                append("$")

                invocation.interfaces.forEach {
                    append("i")
                    append("_")
                    append(generateInterfaceDisambiguator(it))
                    append("$")
                }

                invocation.parameters.forEach {
                    append("p")
                    append("_")
                    append(generateParameterDisambiguator(it))
                    append("$")
                }
            }
        }
    }
}