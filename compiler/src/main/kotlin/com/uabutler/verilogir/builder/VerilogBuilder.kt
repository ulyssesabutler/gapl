package com.uabutler.verilogir.builder

import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.verilogir.builder.interfaceutil.VerilogInterface
import com.uabutler.verilogir.builder.identifier.ModuleIdentifierGenerator
import com.uabutler.verilogir.module.ModuleIO
import com.uabutler.verilogir.module.ModuleIODirection
import com.uabutler.verilogir.util.DataType
import com.uabutler.verilogir.module.Module as VerilogModule
import com.uabutler.gaplir.Module as GAPLModule

object VerilogBuilder {
    private fun moduleIOsFromInterfaceStructure(
        name: String,
        structure: InterfaceStructure,
        direction: ModuleIODirection,
    ): List<ModuleIO> {
        return VerilogInterface.fromGAPLInterfaceStructure(name, structure).map {
             ModuleIO(
                 name = it.name,
                 direction = direction,
                 type = DataType.WIRE,
                 startIndex = it.width - 1,
                 endIndex = 0,
            )
        }
    }

    fun verilogModuleFromGAPLModule(gaplModule: GAPLModule): VerilogModule {
        println("## Building verilog module: ${ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplModule.moduleInvocation)}")

        return VerilogModule(
            name = ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplModule.moduleInvocation),
            inputs = gaplModule.inputNodes.flatMap {
                moduleIOsFromInterfaceStructure(
                    name = it.name,
                    structure = it.inputInterfaceStructure,
                    direction = ModuleIODirection.INPUT,
                )
            },
            outputs = gaplModule.outputNodes.flatMap {
                moduleIOsFromInterfaceStructure(
                    name = it.name,
                    structure = it.outputInterfaceStructure,
                    direction = ModuleIODirection.OUTPUT,
                )
            },
            statements =
                StatementBuilder.verilogStatementsFromIONodes(gaplModule.inputNodes, gaplModule.outputNodes) +
                        StatementBuilder.verilogStatementsFromGAPLNodes(gaplModule.nodes + gaplModule.outputNodes),
        )
    }
}