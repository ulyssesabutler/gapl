package com.uabutler.v2.gaplir.node.input

import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.RecordInterfaceStructure
import com.uabutler.v2.gaplir.VectorInterfaceStructure
import com.uabutler.v2.gaplir.WireInterfaceStructure
import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.node.output.NodeOutputRecordInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputVectorInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputWireInterface

sealed class NodeInputInterface {

    companion object {
        fun fromStructures(node: Node, inputInterfaceStructures: List<InterfaceStructure>): List<NodeInputInterface> {
            return inputInterfaceStructures.map { fromStructure(it) }
        }

        fun fromStructure(interfaceStructure: InterfaceStructure): NodeInputInterface {
            return when (interfaceStructure) {
                is WireInterfaceStructure -> NodeInputWireInterface()
                is RecordInterfaceStructure -> NodeInputRecordInterface(interfaceStructure)
                is VectorInterfaceStructure -> NodeInputVectorInterface(interfaceStructure)
            }
        }
    }
}

class NodeInputWireInterface: NodeInputInterface() {
    var input: NodeOutputWireInterface? = null
}

class NodeInputRecordInterface(
    structure: RecordInterfaceStructure,
): NodeInputInterface() {
    var input: NodeOutputRecordInterface? = null

    val ports: Map<String, NodeInputInterface> = structure.ports.mapValues { fromStructure(it.value) }
}

sealed class VectorProjection
data class VectorSlice(val startIndex: Int, val endIndex: Int): VectorProjection()
data object WholeVector: VectorProjection()
data class VectorConnection(
    val sourceVector: NodeOutputVectorInterface,
    val sourceSlice: VectorProjection,
    val destSlice: VectorProjection
)

class NodeInputVectorInterface(
    structure: VectorInterfaceStructure,
): NodeInputInterface() {
    val connections = mutableListOf<VectorConnection>()

    val vector: List<NodeInputInterface> = List(structure.size) { fromStructure(structure.vectoredInterface) }
}