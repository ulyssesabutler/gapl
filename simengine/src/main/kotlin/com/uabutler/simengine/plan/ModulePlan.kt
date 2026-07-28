package com.uabutler.simengine.plan

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode

/**
 * Static analysis over a single [Module]: which order to evaluate its non-register body nodes in
 * for one combinational settle pass, and a dense indexing scheme for wire storage. Computed once
 * per distinct [Module] object and cached (see [PlanCache]), since the same module can be invoked
 * from multiple call sites.
 */
class ModulePlan(
    val module: Module,
    /** Dense index, one per [OutputWire] bit in the module — the real value storage in `instance`. */
    val outputWireIndex: Map<OutputWire, Int>,
    val outputWireCount: Int,
    /** Every [InputWire] in the module maps to the [outputWireIndex] slot of the wire driving it. */
    val inputWireSource: Map<InputWire, Int>,
    /** Topological order of non-register body nodes for one settle() pass. */
    val evaluationOrder: List<Node>,
    val registerNodes: List<PredefinedFunctionNode>,
)
