package com.uabutler.v2.gaplir.node.output

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.util.StringGenerator.genToStringFromValues
import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.RecordInterfaceStructure
import com.uabutler.v2.gaplir.VectorInterfaceStructure
import com.uabutler.v2.gaplir.WireInterfaceStructure
import com.uabutler.v2.gaplir.node.Node

sealed class NodeOutputInterface {
    abstract val parent: NodeOutputInterfaceParent
    abstract val structure: InterfaceStructure

    companion object {
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
): NodeOutputInterface() {
    override val structure: InterfaceStructure = WireInterfaceStructure
    override fun toString() = "wire@${this.hashCode().toString(16)}"
}

data class NodeOutputRecordInterface(
    override val parent: NodeOutputInterfaceParent,
    override val structure: RecordInterfaceStructure,
): NodeOutputInterface() {
    val ports: Map<String, NodeOutputInterface> = structure.ports.mapValues {
        val parent = NodeOutputInterfaceParentRecordInterface(this, it.key)
        fromStructure(parent, it.value)
    }

    override fun toString() = genToStringFromProperties(
        instance = this,
        NodeOutputRecordInterface::ports,
    )
}

data class NodeOutputVectorInterface(
    override val parent: NodeOutputInterfaceParent,
    override val structure: VectorInterfaceStructure,
): NodeOutputInterface() {
    val vector: List<NodeOutputInterface> = List(structure.size) {
        val parent = NodeOutputInterfaceParentVectorInterface(this, it)
        fromStructure(parent, structure.vectoredInterface)
    }

    override fun toString() = genToStringFromValues(
        instanceName = this::class.simpleName!!,
        hashCode = this.hashCode(),
        values = mapOf(
            "size" to vector.size.toString(),
            "structure" to vector.first().toString(),
        )
    )
}
