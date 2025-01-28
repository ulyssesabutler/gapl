package com.uabutler.v2.gaplir.node.output

import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.RecordInterfaceStructure
import com.uabutler.v2.gaplir.VectorInterfaceStructure
import com.uabutler.v2.gaplir.WireInterfaceStructure
import com.uabutler.v2.gaplir.node.Node

sealed class NodeOutputInterface {
    abstract val parent: NodeOutputInterfaceParent

    companion object {
        fun fromStructures(node: Node, outputInterfaceStructures: List<InterfaceStructure>): List<NodeOutputInterface> {
            return outputInterfaceStructures.mapIndexed { index, interfaceStructure ->
                val parent = NodeOutputInterfaceParentNode(node, index)
                fromStructure(parent, interfaceStructure)
            }
        }

        fun fromStructure(parent: NodeOutputInterfaceParent, interfaceStructure: InterfaceStructure): NodeOutputInterface {
            return when (interfaceStructure) {
                is WireInterfaceStructure -> NodeOutputWireInterface(parent)
                is RecordInterfaceStructure -> NodeOutputRecordInterface(parent, interfaceStructure)
                is VectorInterfaceStructure -> NodeOutputVectorInterface(parent, interfaceStructure)
            }
        }
    }
}

class NodeOutputWireInterface(
    override val parent: NodeOutputInterfaceParent,
): NodeOutputInterface()

class NodeOutputRecordInterface(
    override val parent: NodeOutputInterfaceParent,
    structure: RecordInterfaceStructure,
): NodeOutputInterface() {
    val ports: Map<String, NodeOutputInterface> = structure.ports.mapValues {
        val parent = NodeOutputInterfaceParentRecordInterface(this, it.key)
        fromStructure(parent, it.value)
    }
}

class NodeOutputVectorInterface(
    override val parent: NodeOutputInterfaceParent,
    structure: VectorInterfaceStructure,
): NodeOutputInterface() {
    val vector: List<NodeOutputInterface> = List(structure.size) {
        val parent = NodeOutputInterfaceParentVectorInterface(this, it)
        fromStructure(parent, structure.vectoredInterface)
    }
}
