package com.uabutler.netlistir.transformer.retiming.graph

import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node

class WeightedModule(
    val module: Module,
    val nodes: List<WeightedNode>,
    val connections: List<WeightedConnection>,
) {

    data class WeightedNode(
        val node: Node,
        val weight: Int,
    )

    data class InputGroupConnection(
        val group: InputWireVectorGroup,
        val connections: List<Module.Connection>,
    )

    data class WeightedConnection(
        val source: WeightedNode,
        val sink: WeightedNode,
        val weight: Int,
        val connectionGroups: List<InputGroupConnection>,
    )

}