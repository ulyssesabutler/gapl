package com.uabutler.verilog

import com.uabutler.module.*

object VerilogWriter {
    private fun inputsFromModule(module: Module) = buildList {
        module.inputNodes.forEach { node ->
            add("// Input wires for \"${node.key}\"")
            node.value.output.forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("input $identifier") else add("input [$size:0] $identifier")
                }
            }
        }
    }

    private fun outputsFromModule(module: Module) = buildList {
        module.outputNodes.forEach { node ->
            add("// Output wires for \"${node.key}\"")
            node.value.input.forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("output $identifier") else add("output [$size:0] $identifier")
                }
            }
        }
    }

    private fun getModuleNodeWireDeclarations(module: Module) = buildList {
        (module.bodyNodes + module.anonymousNodes).forEach { node ->
            add("// Wires for node \"${node.key}\"")
            (node.value.input + node.value.output).forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("wire $identifier;") else add("wire [$size:0] $identifier;")
                }
                add("")
            }
        }
    }

    private fun getInterNodeInputConnections(module: ModuleNode) = buildList {
        module.input.forEach { nodeInterface ->
            add("// Assigning input of \"${nodeInterface.identifier}\" (inter-node connection)")

            val input = nodeInterface.getInput().first()
            val inputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input).subInterfaces.toList()
            val outputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.toList()
            outputWires.forEachIndexed { index, pair ->
                val inputIdentifier = inputWires[index].first
                val outputIdentifier = pair.first

                add("assign $outputIdentifier = $inputIdentifier;")
            }
            add("")
        }
    }

    private fun getInterNodeConnections(module: Module) = buildList {
        (module.outputNodes + module.bodyNodes + module.anonymousNodes).forEach { node ->
            addAll(getInterNodeInputConnections(node.value))
        }
    }

    private fun getIntraNodePassThrough(node: ModuleNode) = buildList {
        node.output.forEachIndexed { nodeInterfaceIndex, nodeInterface ->
            add("// Assigning input of \"${nodeInterface.identifier}\" (intra-node connection, pass-through)")

            val input = node.input[nodeInterfaceIndex]
            val inputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input).subInterfaces.toList()
            val outputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.toList()
            outputWires.forEachIndexed { index, pair ->
                val inputIdentifier = inputWires[index].first
                val outputIdentifier = pair.first

                add("assign $outputIdentifier = $inputIdentifier;")
            }
            add("")
        }
    }

    private fun getIntraNodeRegister(node: ModuleNode) = buildList {
        node.output.forEachIndexed { nodeInterfaceIndex, nodeInterface ->
            add("// Creating register for \"${nodeInterface.identifier}\" (intra-node connection, register)")

            val input = node.input[nodeInterfaceIndex]
            val inputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(input.identifier, input).subInterfaces.toList()
            val outputWires = ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.toList()
            outputWires.forEachIndexed { index, pair ->
                val inputIdentifier = inputWires[index].first
                val outputIdentifier = pair.first
                val outputRegisterIdentifier = "${outputIdentifier}_reg"
                val size = pair.second.size

                add("reg [$size:0] $outputRegisterIdentifier;")
                add("assign $outputIdentifier = $outputRegisterIdentifier;")
                add("always @(posedge clk) begin")
                add("  $outputRegisterIdentifier <= $inputIdentifier;")
                add("end")

            }
            add("")
        }
    }

    private fun getIntraNodeConnections(module: Module) = buildList {
        (module.bodyNodes + module.anonymousNodes).forEach { node ->
            when (node.value.mode) {
                ModuleNodeInternalMode.NativeFunction -> {
                    when (node.value.nativeFunction!!) {
                        NativeFunction.Register -> addAll(getIntraNodeRegister(node.value))
                        else -> throw Exception("Unknown native function, ${node.value.nativeFunction}")
                    }
                }
                else -> addAll(getIntraNodePassThrough(node.value))
            }
        }
    }

    fun verilogStringFromModule(module: Module) = buildString {
        appendLine("module ${module.identifier}")
        appendLine("(")

        inputsFromModule(module).forEach { node -> appendLine("  $node,") }
        appendLine()
        outputsFromModule(module).dropLast(1).forEach { node -> appendLine("  $node,") }
        appendLine("  ${outputsFromModule(module).last()}")

        appendLine(");")

        getModuleNodeWireDeclarations(module).forEach { declaration -> appendLine("  $declaration") }

        getInterNodeConnections(module).forEach { connection -> appendLine("  $connection") }

        getIntraNodeConnections(module).forEach { connection -> appendLine("  $connection") }

        appendLine("endmodule")
    }
}