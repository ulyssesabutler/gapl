package com.uabutler.v1.verilog

import com.uabutler.v1.ast.functions.FunctionDefinitionNode
import com.uabutler.v1.module.*

object VerilogWriter {
    private fun inputsFromModule(module: Module) = buildList {
        module.inputNodes.forEach { node ->
            add("// Input wires for \"${node.key}\"")
            node.value.output.forEach { nodeInterface ->
                ModuleNodeTranslatableInterface.fromModuleNodeInterface(nodeInterface.identifier, nodeInterface).subInterfaces.forEach { subInterfaceInterface ->
                    val identifier = subInterfaceInterface.key
                    val size = subInterfaceInterface.value.size

                    if (size == 1) add("input $identifier") else add("input [${size - 1}:0] $identifier")
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

                    if (size == 1) add("output $identifier") else add("output [${size - 1}:0] $identifier")
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

                    if (size == 1) add("wire $identifier;") else add("wire [${size - 1}:0] $identifier;")
                }
                add("")
            }
        }
    }

    private fun getInterNodeInputConnections(moduleNode: ModuleNode) = buildList {
        moduleNode.input.forEach { nodeInterface ->
            add("// Assigning input of \"${nodeInterface.identifier}\" (inter-node connection)")
            if (nodeInterface.getInput().isNotEmpty()) {
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
                add("always @(posedge clock) begin")
                add("  if (reset) begin")
                add("    $outputRegisterIdentifier <= 0;")
                add("  end else begin")
                add("    $outputRegisterIdentifier <= $inputIdentifier;")
                add("  end")
                add("end")

            }
            add("")
        }
    }

    private fun getIntraNodeAdd(node: ModuleNode) = buildList {
        val outputIdentifier = node.output.first().identifier
        val operand0Identifier = node.input[0].identifier
        val operand1Identifier = node.input[1].identifier

        add("// Creating addition for \"${outputIdentifier}\" (intra-node connection, addition)")
        add("assign $outputIdentifier = $operand0Identifier + $operand1Identifier;")

        add("")
    }

    private fun getIntraNodeModuleInstantiation(identifier: String, moduleNode: ModuleNode, functionDefinition: FunctionDefinitionNode) = buildList<String> {
        val functionInputIdentifiers = functionDefinition.inputFunctionIO
            .map { ModuleNodeInterface.fromInterfaceExpressionNode("${it.identifier.value}_output", it.interfaceType, null) }
            .map { ModuleNodeTranslatableInterface.fromModuleNodeInterface(it.identifier, it) }
            .flatMap { it.subInterfaces.keys }
        val functionOutputIdentifiers = functionDefinition.outputFunctionIO
            .map { ModuleNodeInterface.fromInterfaceExpressionNode("${it.identifier.value}_input", it.interfaceType, null) }
            .map { ModuleNodeTranslatableInterface.fromModuleNodeInterface(it.identifier, it) }
            .flatMap { it.subInterfaces.keys }
        val nodeInputIdentifiers = moduleNode.input
            .map { ModuleNodeTranslatableInterface.fromModuleNodeInterface(it.identifier, it) }
            .flatMap { it.subInterfaces.keys }
        val nodeOutputIdentifiers = moduleNode.output
            .map { ModuleNodeTranslatableInterface.fromModuleNodeInterface(it.identifier, it) }
            .flatMap { it.subInterfaces.keys }

        val moduleIdentifier = functionDefinition.identifier.value
        val instantiationIdentifier = "${moduleIdentifier}_$identifier"

        add("// Creating instantiation of \"$moduleIdentifier\" for \"$identifier\" (intra-node connection, module)")
        add("$moduleIdentifier $instantiationIdentifier")
        add("(")

        add("  .clock(clock),")
        add("  .reset(reset),")
        add("")

        functionInputIdentifiers.forEachIndexed { index, functionInputIdentifier ->
            val nodeInputIdentifier = nodeInputIdentifiers[index]
            add("  .$functionInputIdentifier($nodeInputIdentifier),")
        }

        add("")

        functionOutputIdentifiers.dropLast(1).forEachIndexed { index, functionOutputIdentifier ->
            val nodeOutputIdentifier = nodeOutputIdentifiers[index]
            add("  .$functionOutputIdentifier($nodeOutputIdentifier),")
        }

        functionOutputIdentifiers.last().let { functionOutputIdentifier ->
            val nodeOutputIdentifier = nodeOutputIdentifiers.last()
            add("  .$functionOutputIdentifier($nodeOutputIdentifier)")
        }

        add(");")
    }

    private fun getIntraNodeConnections(module: Module) = buildList {
        (module.bodyNodes + module.anonymousNodes).forEach { node ->
            when (node.value.mode) {
                ModuleNodeInternalMode.NativeFunction -> {
                    when (node.value.nativeFunction!!) {
                        NativeFunction.Register -> addAll(getIntraNodeRegister(node.value))
                        NativeFunction.Add -> addAll(getIntraNodeAdd(node.value))
                        else -> throw Exception("Unknown native function, ${node.value.nativeFunction}")
                    }
                }
                ModuleNodeInternalMode.DefinedFunction -> {
                    val definition = node.value.functionDefinition!!
                    addAll(getIntraNodeModuleInstantiation(node.key, node.value, definition))
                }
                ModuleNodeInternalMode.DefinedInterface -> addAll(getIntraNodePassThrough(node.value))
                else -> addAll(getIntraNodePassThrough(node.value))
            }
        }
    }

    fun verilogStringFromModule(module: Module) = buildString {
        appendLine("module ${module.identifier}")
        appendLine("(")

        appendLine("  input clock,")
        appendLine("  input reset,")
        appendLine()

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