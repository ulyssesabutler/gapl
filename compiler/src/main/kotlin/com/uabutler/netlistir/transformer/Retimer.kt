package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.util.PropagationDelay
import com.uabutler.netlistir.transformer.util.NetlistLeisersonCircuitConverter
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.Logger

class Retimer(val delay: PropagationDelay): Transformer {

    companion object {
        fun retimeModuleFilter(module: Module): Boolean {
            return module.getNodes().any {
                it is PredefinedFunctionNode && it.predefinedFunction is RegisterFunction
            }
        }
    }

    override fun transform(original: List<Module>): List<Module> {
        Logger.start("Retimer Transformer")
        val moduleRetimability = original.groupBy { retimeModuleFilter(it) }

        val modulesToRetime = moduleRetimability[true] ?: emptyList()
        val modulesToSkip = moduleRetimability[false] ?: emptyList()

        val retimedModules = modulesToRetime.asSequence()
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .sumOf { it.inputWires().size }
                    "Unretimed body wire count: $registerWires"
                }
            }
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .filterIsInstance<PredefinedFunctionNode>()
                        .filter { it.predefinedFunction is RegisterFunction }
                        .sumOf { it.inputWires().size }
                    "Unretimed register wire count: $registerWires"
                }
            }
            .map { NetlistLeisersonCircuitConverter.fromModule(it, delay, true) }
            .map { it.retimed() }
            .map { NetlistLeisersonCircuitConverter.toModule(it) }
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .sumOf { it.inputWires().size }
                    "Retimed body wire count: $registerWires"
                }
            }
            .onEach {
                Logger.debug {
                    val registerWires = it.getBodyNodes()
                        .filterIsInstance<PredefinedFunctionNode>()
                        .filter { it.predefinedFunction is RegisterFunction }
                        .sumOf { it.inputWires().size }
                    "Retimed register wire count: $registerWires"
                }
            }
            .toList()

        Logger.finish()

        return retimedModules + modulesToSkip
    }

}