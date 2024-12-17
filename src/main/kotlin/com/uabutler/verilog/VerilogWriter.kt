package com.uabutler.verilog

import com.uabutler.module.Module
import com.uabutler.module.ModuleNode
import com.uabutler.module.ModuleNodeTranslatableInterface

object VerilogWriter {
    private fun inputsFromModule(module: Module) = buildList {
        module.inputNodes.forEach { node ->
            node.value.output.forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(node.key, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("input $identifier") else add("input [0:$size] $identifier")
                }
            }
        }
    }

    private fun outputsFromModule(module: Module) = buildList {
        module.outputNodes.forEach { node ->
            node.value.input.forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(node.key, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("output $identifier") else add("output [0:$size] $identifier")
                }
            }
        }
    }

    private fun getModuleNodeWireDeclarations(module: Module) = buildList {
        (module.bodyNodes + module.anonymousNodes).forEach { node ->
            (node.value.input + node.value.output).forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("wire $identifier;") else add("wire [0:$size] $identifier;")
                }
            }
        }
    }

    private fun getNodeInputConnections(module: ModuleNode) = buildList {
        module.input.forEach { nodeInterface ->
            val input = nodeInterface.getInput().first()
            val inputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input).subInterfaces.toList()
            val outputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.toList()
            outputWires.forEachIndexed { index, pair ->
                val inputIdentifier = inputWires[index].first
                val outputIdentifier = pair.first

                add("assign $outputIdentifier = $inputIdentifier;")
            }
        }
    }

    private fun getNodeConnections(module: Module) = buildList {
        module.outputNodes.forEach { node ->
            addAll(getNodeInputConnections(node.value))
        }
        module.bodyNodes.forEach { node ->
            addAll(getNodeInputConnections(node.value))
        }
        module.anonymousNodes.forEach { node ->
            addAll(getNodeInputConnections(node.value))
        }
    }

    fun verilogStringFromModule(module: Module) = buildString {
        appendLine("module ${module.identifier}")
        appendLine("(")

        inputsFromModule(module).forEach { node -> appendLine("  $node,") }
        outputsFromModule(module).dropLast(1).forEach { node -> appendLine("  $node,") }
        appendLine("  ${outputsFromModule(module).last()}")

        appendLine(");")

        getModuleNodeWireDeclarations(module).forEach { declaration -> appendLine("  $declaration") }
        getNodeConnections(module).forEach { connection -> appendLine("  $connection") }

        appendLine("endmodule")
    }
}