package com.uabutler.v2.gaplir.node.input

import com.uabutler.util.StringGenerator.genToStringFromProperties
import com.uabutler.util.StringGenerator.genToStringFromValues
import com.uabutler.v2.gaplir.InterfaceStructure
import com.uabutler.v2.gaplir.RecordInterfaceStructure
import com.uabutler.v2.gaplir.VectorInterfaceStructure
import com.uabutler.v2.gaplir.WireInterfaceStructure
import com.uabutler.v2.gaplir.node.Node
import com.uabutler.v2.gaplir.node.output.NodeOutputRecordInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputVectorInterface
import com.uabutler.v2.gaplir.node.output.NodeOutputWireInterface

sealed class NodeInputInterface {
    abstract val structure: InterfaceStructure

    companion object {
        fun fromStructure(interfaceStructure: InterfaceStructure): NodeInputInterface {
            return when (interfaceStructure) {
                is WireInterfaceStructure -> NodeInputWireInterface()
                is RecordInterfaceStructure -> NodeInputRecordInterface(interfaceStructure)
                is VectorInterfaceStructure -> NodeInputVectorInterface(interfaceStructure)
            }
        }
    }
}

// TODO: Custom toStrings instead of data classes
class NodeInputWireInterface(
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
    override val structure: RecordInterfaceStructure,
): NodeInputInterface() {
    var input: NodeOutputRecordInterface? = null
    val ports: Map<String, NodeInputInterface> = structure.ports.mapValues { fromStructure(it.value) }

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
    override val structure: VectorInterfaceStructure,
): NodeInputInterface() {
    val connections: MutableList<VectorConnection> = mutableListOf()
    val vector: List<NodeInputInterface> = List(structure.size) { fromStructure(structure.vectoredInterface) }

    override fun toString() = genToStringFromProperties(
        instance = this,
        NodeInputVectorInterface::vector,
        NodeInputVectorInterface::connections,
    )
}