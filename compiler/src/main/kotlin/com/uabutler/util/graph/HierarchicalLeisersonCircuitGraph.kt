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

        val retimedInputDelay: Int?,
        val retimedOutputDelay: Int?,
        val retimedCombinationalDelay: Int?,

        val unretimedRegisterDelay: Int,
        val retimedRegisterDelay: Int,

        val originalIncomingEdges: List<Edge<N, E>>,
        val originalOutgoingEdges: List<Edge<N, E>>,

        val contractedIncomingEdges: List<Edge<N, E>>,
        val contractedOutgoingEdges: List<Edge<N, E>>,

        val originalNode: Node<N>,

        val contractedInputNode: Node<N>,
        val contractedOutputNode: Node<N>,

        val contractedInputDelayNode: Node<N>?,
        val contractedOutputDelayNode: Node<N>?,

        val contractedInputDelayEdge: Edge<N, E>?,
        val contractedOutputDelayEdge: Edge<N, E>?,
        val contractedRegisterDelayEdge: Edge<N, E>?,

        val contractedCombinationalDelayNode: Node<N>?,

        val contractedCombinationalDelayInputEdge: Edge<N, E>?,
        val contractedCombinationalDelayOutputEdge: Edge<N, E>?,
    ) {
        fun additionalRegisterDelay() = retimedRegisterDelay - unretimedRegisterDelay

        fun contractedGraphNodes() = listOfNotNull(
            contractedInputNode,
            contractedOutputNode,
            contractedInputDelayNode,
            contractedOutputDelayNode,
            contractedCombinationalDelayNode,
        )

        fun contractedGraphEdges() = listOfNotNull(
            contractedInputDelayEdge,
            contractedOutputDelayEdge,
            contractedRegisterDelayEdge,
            contractedCombinationalDelayInputEdge,
            contractedCombinationalDelayOutputEdge,
        )
    }

    private val contractCircuitGraphsByNode = contractCircuitGraphs.associateBy { it.moduleInvocationNode }

}