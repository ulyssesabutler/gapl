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
        val contractedEdge: Edge<N, E>,
        val contractedInputNode: Node<N>,
        val contractedOutputNode: Node<N>,
        val contractedIncomingEdges: List<Edge<N, E>>,
        val contractedOutgoingEdges: List<Edge<N, E>>,
        val originalNode: Node<N>,
        val originalIncomingEdges: List<Edge<N, E>>,
        val originalOutgoingEdges: List<Edge<N, E>>,
    ) {
        fun additionalRegisterDelay() = retimedRegisterDelay - unretimedRegisterDelay
    }

    private val contractCircuitGraphsByNode = contractCircuitGraphs.associateBy { it.moduleInvocationNode }

}