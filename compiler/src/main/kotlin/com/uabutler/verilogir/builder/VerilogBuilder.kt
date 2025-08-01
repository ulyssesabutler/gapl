package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.WireVectorGroup
import com.uabutler.netlistir.netlist.Module as NetlistModule
import com.uabutler.verilogir.builder.creator.util.Identifier
import com.uabutler.verilogir.module.ModuleIO
import com.uabutler.verilogir.module.ModuleIODirection
import com.uabutler.verilogir.util.DataType
import com.uabutler.verilogir.module.Module as VerilogModule

object VerilogBuilder {
    private fun moduleIOsFromInterfaceStructure(
        structure: List<WireVectorGroup<*>>,
        direction: ModuleIODirection,
    ): List<ModuleIO> {
        return structure.flatMap { group ->
            group.wireVectors.map { wireVector ->
                ModuleIO(
                    name = Identifier.wire(wireVector),
                    direction = direction,
                    type = DataType.WIRE,
                    startIndex = wireVector.wires.size - 1,
                    endIndex = 0,
                )
            }
        }
    }

    fun verilogModuleFromGAPLModule(netlistModule: NetlistModule): VerilogModule {
        try {
            return VerilogModule(
                name = Identifier.module(netlistModule.invocation),
                inputs = netlistModule.getInputNodes().flatMap {
                    moduleIOsFromInterfaceStructure(
                        structure = it.outputWireVectorGroups,
                        direction = ModuleIODirection.INPUT,
                    )
                },
                outputs = netlistModule.getOutputNodes().flatMap {
                    moduleIOsFromInterfaceStructure(
                        structure = it.inputWireVectorGroups,
                        direction = ModuleIODirection.OUTPUT,
                    )
                },
                statements = StatementBuilder.verilogStatementsFromNodes(netlistModule.getNodes()),
            )
        } catch (e: Exception) {
            throw Exception("Failed to create verilog module ${Identifier.module(netlistModule.invocation)} for netlist module ${netlistModule.invocation.gaplFunctionName}", e)
        }
    }
}