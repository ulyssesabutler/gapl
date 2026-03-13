package com.uabutler.util.graph

import com.uabutler.netlistir.netlist.ModuleInvocationNode

open class HierarchicalLeisersonCircuitGraph<G, N, E>(
    value: G,
    nodes: Collection<Node<N>>,
    edges: Collection<Edge<N, E>>,
    val contractCircuitGraphs: Collection<ContractCircuitGraph<N, E>>,
): LeisersonCircuitGraph<G, N, E>(value, nodes, edges) {

    data class ContractCircuitGraph<N, E>(
        val moduleInvocationNode: ModuleInvocationNode,
        val retimedInputDelay: Int,
        val retimedOutputDelay: Int,
        val unretimedRegisterDelay: Int,
        val retimedRegisterDelay: Int,
        val inputNode: Node<N>,
        val outputNode: Node<N>,
        val edge: Edge<N, E>,
    ) {
        fun additionalRegisterDelay() = retimedRegisterDelay - unretimedRegisterDelay
    }

    private val contractCircuitGraphsByNode = contractCircuitGraphs.associateBy { it.moduleInvocationNode }

}