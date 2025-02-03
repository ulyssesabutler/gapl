package com.uabutler.v2.verilogir.builder

import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.verilogir.builder.`interface`.VerilogInterface
import com.uabutler.v2.verilogir.builder.util.ModuleIdentifierGenerator
import com.uabutler.v2.verilogir.module.ModuleIO
import com.uabutler.v2.verilogir.module.ModuleIODirection
import com.uabutler.v2.verilogir.util.DataType
import com.uabutler.v2.verilogir.module.Module as VerilogModule
import com.uabutler.v2.gaplir.Module as GAPLModule

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

    fun verilogModuleFromGAPLModule(gaplModule: GAPLModule) = VerilogModule(
        name = ModuleIdentifierGenerator.genIdentifierFromInvocation(gaplModule.moduleInvocation),
        inputs = gaplModule.inputStructure.flatMap { moduleIOsFromInterfaceStructure(it.key, it.value, ModuleIODirection.INPUT) },
        outputs = gaplModule.outputStructure.flatMap { moduleIOsFromInterfaceStructure(it.key, it.value, ModuleIODirection.OUTPUT) },
        statements = listOf(), // TODO
    )
}