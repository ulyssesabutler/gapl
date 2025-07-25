package com.uabutler.verilogir.builder

import com.uabutler.netlistir.netlist.WireVectorGroup
import com.uabutler.netlistir.netlist.Module as NetlistModule
import com.uabutler.util.VerilogInterface
import com.uabutler.verilogir.builder.identifier.ModuleIdentifierGenerator
import com.uabutler.verilogir.module.ModuleIO
import com.uabutler.verilogir.module.ModuleIODirection
import com.uabutler.verilogir.util.DataType
import com.uabutler.verilogir.module.Module as VerilogModule

object VerilogBuilder {
    private fun moduleIOsFromInterfaceStructure(
        name: String,
        structure: List<WireVectorGroup<*>>,
        direction: ModuleIODirection,
    ): List<ModuleIO> {
        structure.flatMap { group ->
            group.wireVectors.map { wireVector ->
                ModuleIO(
                    name = "${group.identifier}${wireVector.identifier.joinToString("$")}",
                    direction = direction,
                    type = DataType.WIRE,
                    startIndex = wireVector.wires.size - 1,
                    endIndex = 0,
                )
            }
        }

        return VerilogInterface.fromGAPLInterfaceStructure(name = name, structure = structure).map {
             ModuleIO(
                 name = it.name,
                 direction = direction,
                 type = DataType.WIRE,
                 startIndex = it.width - 1,
                 endIndex = 0,
            )
        }
    }

    fun verilogModuleFromGAPLModule(netlistModule: NetlistModule): VerilogModule {
        return VerilogModule(
            name = ModuleIdentifierGenerator.genIdentifierFromInvocation(netlistModule.invocation),
            inputs = netlistModule.getInputNodes().flatMap {
                moduleIOsFromInterfaceStructure(
                    name = it.identifier,
                    structure = it.inputWireVectorGroups,
                    direction = ModuleIODirection.INPUT,
                )
            },
            outputs = netlistModule.getOutputNodes().flatMap {
                moduleIOsFromInterfaceStructure(
                    name = it.identifier,
                    structure = it.outputWireVectorGroups,
                    direction = ModuleIODirection.OUTPUT,
                )
            },
            statements =
                OldStatementBuilder.verilogStatementsFromIONodes(netlistModule.getInputNodes(), netlistModule.getOutputNodes()) +
                        OldStatementBuilder.verilogStatementsFromGAPLNodes(netlistModule.getBodyNodes()),
        )
    }
}