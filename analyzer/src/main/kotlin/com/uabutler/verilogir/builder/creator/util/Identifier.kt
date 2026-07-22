package com.uabutler.verilogir.builder.creator.util

import com.uabutler.netlistir.builder.util.FunctionInstantiationParameterValue
import com.uabutler.netlistir.builder.util.IntegerParameterValue
import com.uabutler.netlistir.builder.util.InterfaceStructure
import com.uabutler.netlistir.builder.util.ParameterValue
import com.uabutler.netlistir.builder.util.RecordInterfaceStructure
import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.IONode
import com.uabutler.netlistir.netlist.InputWireVector
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.OutputWireVector
import com.uabutler.netlistir.netlist.WireVector

object Identifier {
    fun ioWire(primaryIdentifier: String, wire: WireVector<*>): String {
        return buildList {
            add(primaryIdentifier)
            addAll(wire.identifier)
        }.joinToString("$")
    }

    fun bodyWire(wire: WireVector<*>): String {
        val type = when (wire) {
            is InputWireVector -> "input"
            is OutputWireVector -> "output"
        }

        return buildList {
            add(wire.parentGroup.parentNode.name())
            add(wire.parentGroup.identifier)
            addAll(wire.identifier)
            add(type)
        }.joinToString("$")
    }

    fun wire(wire: WireVector<*>): String {
        val node = wire.parentGroup.parentNode

        return when (node) {
            is IONode -> ioWire(wire.parentGroup.parentNode.name(), wire)
            else -> bodyWire(wire)
        }
    }

    fun module(invocation: Module.Invocation): String {
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
                append(module(parameterValue.value))
                append("$")
            }
        }
    }
}