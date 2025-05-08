package com.uabutler.gaplir.node.input

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.util.StringGenerator.genToStringFromValues
import com.uabutler.gaplir.InterfaceStructure
import com.uabutler.gaplir.RecordInterfaceStructure
import com.uabutler.gaplir.VectorInterfaceStructure
import com.uabutler.gaplir.WireInterfaceStructure
import com.uabutler.gaplir.node.output.NodeOutputRecordInterface
import com.uabutler.gaplir.node.output.NodeOutputVectorInterface
import com.uabutler.gaplir.node.output.NodeOutputWireInterface

sealed class NodeInputInterface {
    abstract val parent: NodeInputInterfaceParent
    abstract val structure: InterfaceStructure

    companion object {
        fun fromStructure(parent: NodeInputInterfaceParent, interfaceStructure: InterfaceStructure): NodeInputInterface {
            return when (interfaceStructure) {
                is WireInterfaceStructure -> NodeInputWireInterface(parent)
                is RecordInterfaceStructure -> NodeInputRecordInterface(parent, interfaceStructure)
                is VectorInterfaceStructure -> NodeInputVectorInterface(parent, interfaceStructure)
            }
        }
    }
}

// TODO: Custom toStrings instead of data classes
class NodeInputWireInterface(
    override val parent: NodeInputInterfaceParent,
    var input: NodeOutputWireInterface? = null,
): NodeInputInterface() {
    override val structure: InterfaceStructure = WireInterfaceStructure

    override fun toString() = genToStringFromValues(
        instanceName = this.javaClass.simpleName,
        hashCode = this.hashCode(),
        values = mapOf("input" to input.hashCode().toString(16)),
    )
}

class NodeInputRecordInterface(
    override val parent: NodeInputInterfaceParent,
    override val structure: RecordInterfaceStructure,
): NodeInputInterface() {
    var input: NodeOutputRecordInterface? = null
    val ports: Map<String, NodeInputInterface> = structure.ports.mapValues {
        val parent = NodeInputInterfaceParentRecordInterface(this, it.key)
        fromStructure(parent, it.value)
    }

    override fun toString() = genToStringFromValues(
        instanceName = this.javaClass.simpleName,
        hashCode = this.hashCode(),
        values = mapOf(
            "ports" to ports.toString(),
            "input" to input.hashCode().toString(16)
        ),
    )
}

sealed class VectorProjection
data class VectorSlice(val startIndex: Int, val endIndex: Int): VectorProjection()
data object WholeVector: VectorProjection()
data class VectorConnection(
    val sourceVector: NodeOutputVectorInterface,
    val sourceSlice: VectorProjection,
    val destSlice: VectorProjection
) {
    override fun toString() = genToStringFromValues(
        instanceName = this.javaClass.simpleName,
        hashCode = this.hashCode(),
        values = mapOf(
            "sourceVector" to sourceVector.hashCode().toString(16),
            "sourceSlice" to sourceSlice.toString(),
            "destSlice" to destSlice.toString(),
        ),
    )
}

data class NodeInputVectorInterface(
    override val parent: NodeInputInterfaceParent,
    override val structure: VectorInterfaceStructure,
): NodeInputInterface() {
    val connections: MutableList<VectorConnection> = mutableListOf()
    val vector: List<NodeInputInterface> = List(structure.size) {
        val parent = NodeInputInterfaceParentVectorInterface(this, it)
        fromStructure(parent, structure.vectoredInterface)
    }

    override fun toString() = genToStringFromProperties(
        instance = this,
        NodeInputVectorInterface::vector,
        NodeInputVectorInterface::connections,
    )
}