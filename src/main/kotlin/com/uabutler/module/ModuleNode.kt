package com.uabutler.module

import com.uabutler.ast.interfaces.InterfaceExpressionNode

sealed class ModuleNodeInterface {
    abstract fun hasInput(): Boolean
    abstract fun getInput(): ModuleNodeInterface?
    abstract fun isCompatible(other: ModuleNodeInterface): Boolean

    fun setInput(input: ModuleNodeInterface) {
        when (input) {
            is ModuleNodeWireInterface -> (this as ModuleNodeWireInterface).setInput(input)
            is ModuleNodeVectorInterface -> (this as ModuleNodeVectorInterface).setInput(input)
            is ModuleNodeRecordInterface -> (this as ModuleNodeRecordInterface).setInput(input)
        }
    }

    companion object {
        fun fromInterfaceExpressionNode(node: InterfaceExpressionNode): ModuleNodeInterface = TODO()
    }
}

class ModuleNodeWireInterface : ModuleNodeInterface() {
    var inputNode: ModuleNodeWireInterface? = null

    override fun hasInput() = inputNode != null
    override fun getInput() = inputNode
    override fun isCompatible(other: ModuleNodeInterface): Boolean {
        return other is ModuleNodeWireInterface
    }

    fun setInput(input: ModuleNodeWireInterface) { inputNode = input }
}

sealed class ModuleNodeComplexInterface: ModuleNodeInterface()

data class ModuleNodeVectorInterface(
    val subInterfaces: List<ModuleNodeInterface>,
): ModuleNodeComplexInterface() {
    var inputNode: ModuleNodeVectorInterface? = null

    override fun hasInput() = inputNode != null || subInterfaces.all { it.hasInput() }
    override fun getInput() = inputNode
    override fun isCompatible(other: ModuleNodeInterface): Boolean {
        if (other !is ModuleNodeVectorInterface) return false
        if (other.subInterfaces.size != subInterfaces.size) return false

        return other.subInterfaces
            .zip(subInterfaces)
            .all { (subInterface, otherSubInterface) ->
                subInterface.isCompatible(otherSubInterface)
            }
    }

    fun setInput(input: ModuleNodeVectorInterface) {
        if (inputNode != null || subInterfaces.any { it.hasInput() }) throw Exception("Interface already has input")

        inputNode = input
    }

    fun setInput(index: Int, input: ModuleNodeInterface) {
        if (inputNode != null || subInterfaces[index].hasInput()) throw Exception("Interface already has input")

        subInterfaces[index].setInput(input)
    }

    fun setInput(startIndex: Int, input: ModuleNodeVectorInterface) {
        if (inputNode != null) throw Exception("Interface already has input")

        input.subInterfaces.forEachIndexed { i, it ->
            if (subInterfaces[startIndex + i].hasInput()) throw Exception("Interface already has input")

            subInterfaces[i].setInput(it)
        }
    }
}

data class ModuleNodeRecordInterface(
    val subInterfaces: Map<String, ModuleNodeInterface>,
): ModuleNodeComplexInterface() {
    var inputNode: ModuleNodeRecordInterface? = null

    override fun hasInput() = inputNode != null || subInterfaces.values.all { it.hasInput() }
    override fun getInput() = inputNode
    override fun isCompatible(other: ModuleNodeInterface): Boolean {
        if (other !is ModuleNodeRecordInterface) return false
        if (other.subInterfaces.size != subInterfaces.size) return false

        // Not comparing the keys is deliberate
        return other.subInterfaces.values
            .zip(subInterfaces.values)
            .all { (subInterface, otherSubInterface) ->
                subInterface.isCompatible(otherSubInterface)
            }
    }

    fun setInput(input: ModuleNodeRecordInterface) {
        if (inputNode != null || subInterfaces.values.any { it.hasInput() }) throw Exception("Interface already has input")

        inputNode = input
    }

    fun setInput(key: String, input: ModuleNodeRecordInterface) {
        if (inputNode != null || subInterfaces[key]!!.hasInput()) throw Exception("Interface already has input")

        subInterfaces[key]!!.setInput(input)
    }
}

data class ModuleNodeTranslatableInterface private constructor(
    val subInterfaces: Map<String, List<ModuleNodeWireInterface>>,
) {
    companion object {
        fun fromModuleNodeInterface(moduleNodeInterface: ModuleNodeInterface): ModuleNodeTranslatableInterface {
            return when (moduleNodeInterface) {
                is ModuleNodeWireInterface -> {
                    // I.e., interface { wire[1]; }
                    ModuleNodeTranslatableInterface(mapOf("_wire" to listOf(moduleNodeInterface)))
                }
                is ModuleNodeRecordInterface -> {
                    ModuleNodeTranslatableInterface(
                        moduleNodeInterface.subInterfaces
                            .map { port ->
                                fromModuleNodeInterface(port.value).subInterfaces.mapKeys { "${port.key}_${it.key}" }
                            }
                            .fold(mutableMapOf()) { acc, map ->
                                acc.putAll(map)
                                acc
                            }
                    )
                }
                is ModuleNodeVectorInterface -> {
                    when {
                        moduleNodeInterface.subInterfaces.all { it is ModuleNodeWireInterface } -> {
                            // Type erasure jumps in to ruin my life, yet again.
                            val subInterfaces = moduleNodeInterface.subInterfaces
                                .filterIsInstance<ModuleNodeWireInterface>()

                            ModuleNodeTranslatableInterface(mapOf("_vector" to subInterfaces))
                        }
                        moduleNodeInterface.subInterfaces.all { it is ModuleNodeVectorInterface } -> {
                            // Fuck type erasure
                            val subInterfaces = moduleNodeInterface.subInterfaces
                                .filterIsInstance<ModuleNodeVectorInterface>()
                                .flatMap { it.subInterfaces }

                            fromModuleNodeInterface(ModuleNodeVectorInterface(subInterfaces))
                        }
                        moduleNodeInterface.subInterfaces.all { it is ModuleNodeRecordInterface } -> {
                            val portNames = moduleNodeInterface.subInterfaces
                                .filterIsInstance<ModuleNodeRecordInterface>()
                                .first()
                                .subInterfaces
                                .keys

                            val subInterfaces = portNames.associate { key ->
                                key to ModuleNodeVectorInterface(
                                    moduleNodeInterface.subInterfaces
                                        .filterIsInstance<ModuleNodeRecordInterface>()
                                        .map { it.subInterfaces[key]!! }
                                )
                            }

                            fromModuleNodeInterface(ModuleNodeRecordInterface(subInterfaces))
                        }
                        else -> throw Exception("Invalid subtype for module node vector interface")
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "{" + subInterfaces.map { "${it.key}: wire[${it.value.size}];" }.joinToString(" ") + "}"
    }
}