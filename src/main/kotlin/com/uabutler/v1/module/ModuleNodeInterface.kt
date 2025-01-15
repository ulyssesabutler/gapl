package com.uabutler.v1.module

import com.uabutler.v1.util.StaticExpressionEvaluator
import com.uabutler.v1.ast.interfaces.*

fun ModuleNodeInterface?.objectString() = this?.let { it::class.simpleName + "@" + System.identityHashCode(it) } ?: "null"

sealed class ModuleNodeInterface {
    abstract var identifier: String

    abstract fun hasInput(): Boolean
    abstract fun getInput(): Collection<ModuleNodeInterface>
    abstract fun getInputString(): String
    override fun toString(): String {
        return this.objectString() + "(identifier=${identifier}, input=${getInputString()})"
    }
    abstract fun isCompatible(other: ModuleNodeInterface): Boolean

    abstract var parentNode: ModuleNode?

    fun duplicate(identifier: String? = null): ModuleNodeInterface {
        return when (this) {
            is ModuleNodeWireInterface -> copy(identifier = identifier ?: this.identifier)
            is ModuleNodeVectorInterface -> copy(identifier = identifier ?: this.identifier)
            is ModuleNodeRecordInterface -> copy(identifier = identifier ?: this.identifier)
        }
    }

    fun setInput(input: ModuleNodeInterface) {
        when (input) {
            is ModuleNodeWireInterface -> (this as ModuleNodeWireInterface).setInput(input)
            is ModuleNodeVectorInterface -> (this as ModuleNodeVectorInterface).setInput(input)
            is ModuleNodeRecordInterface -> (this as ModuleNodeRecordInterface).setInput(input)
        }
    }

    companion object {
        fun fromInterfaceExpressionNode(identifier: String, node: InterfaceExpressionNode, parent: ModuleNode?): ModuleNodeInterface {
            when (node) {
                is WireInterfaceExpressionNode -> {
                    val newNode = ModuleNodeWireInterface(identifier)
                    newNode.parentNode = parent
                    return newNode
                }
                is DefinedInterfaceExpressionNode -> {
                    val declaration = node.getScope()!!.getDeclaration(node.interfaceIdentifier.value)!!.astNode as InterfaceDefinitionNode
                    return fromInterfaceDefinitionNode(identifier, declaration, parent)
                }
                is VectorInterfaceExpressionNode -> {
                    return ModuleNodeVectorInterface(
                        identifier = identifier,
                        subInterfaces = List(StaticExpressionEvaluator.evaluateConcrete(node.boundsSpecifier.boundSpecifier)) {
                            fromInterfaceExpressionNode(identifier, node.vectoredInterface, parent)
                        },
                    )
                }
                is IdentifierInterfaceExpressionNode -> TODO("Currently assuming concrete syntax")
            }
        }

        fun fromInterfaceDefinitionNode(identifier: String, node: InterfaceDefinitionNode, parent: ModuleNode?): ModuleNodeInterface {
            return when (node) {
                is AliasInterfaceDefinitionNode -> {
                    fromInterfaceExpressionNode(identifier, node.aliasedInterface, parent)
                }

                is RecordInterfaceDefinitionNode -> {
                    ModuleNodeRecordInterface(
                        identifier = identifier,
                        subInterfaces = node.ports
                            .associateBy { it.identifier }
                            .mapKeys { it.key.value }
                            .mapValues { entry -> fromInterfaceExpressionNode("${identifier}_${entry.key}", entry.value.type, parent) }
                    )
                }
            }
        }
    }
}

data class ModuleNodeWireInterface(
    override var identifier: String,
): ModuleNodeInterface() {
    override var parentNode: ModuleNode? = null
    var inputNode: ModuleNodeWireInterface? = null

    override fun hasInput() = inputNode != null
    override fun getInput() = inputNode?.let { listOf(it) } ?: emptyList()
    override fun isCompatible(other: ModuleNodeInterface): Boolean {
        return other is ModuleNodeWireInterface
    }

    override fun toString(): String {
        return this.objectString() + "(identifier=${identifier}, input=${getInputString()})"
    }

    fun setInput(input: ModuleNodeWireInterface) { inputNode = input }

    override fun getInputString(): String {
        return inputNode.objectString()
    }
}

sealed class ModuleNodeComplexInterface: ModuleNodeInterface()

data class ModuleNodeVectorInterface(
    override var identifier: String,
    val subInterfaces: List<ModuleNodeInterface>,
): ModuleNodeComplexInterface() {
    override var parentNode: ModuleNode? = null
    var inputNode: ModuleNodeVectorInterface? = null

    override fun hasInput() = inputNode != null || subInterfaces.all { it.hasInput() }
    override fun getInput() = inputNode?.let { listOf(it) } ?: subInterfaces.flatMap { it.getInput() }
    override fun isCompatible(other: ModuleNodeInterface): Boolean {
        if (other !is ModuleNodeVectorInterface) return false
        if (other.subInterfaces.size != subInterfaces.size) return false

        return other.subInterfaces
            .zip(subInterfaces)
            .all { (subInterface, otherSubInterface) ->
                subInterface.isCompatible(otherSubInterface)
            }
    }

    override fun toString(): String {
        return this.objectString() + "(identifier=${identifier}, input=${getInputString()})"
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

    override fun getInputString(): String {
        return if (hasInput())
            inputNode?.objectString() ?: "[${subInterfaces.joinToString(", ") { it.getInputString() }}]"
        else
            "null"
    }
}

data class ModuleNodeRecordInterface(
    override var identifier: String,
    val subInterfaces: Map<String, ModuleNodeInterface>,
): ModuleNodeComplexInterface() {
    override var parentNode: ModuleNode? = null
    var inputNode: ModuleNodeRecordInterface? = null

    override fun hasInput() = inputNode != null || subInterfaces.values.all { it.hasInput() }
    override fun getInput() = inputNode?.let { listOf(it) } ?: subInterfaces.values.flatMap { it.getInput() }
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

    override fun toString(): String {
        return this.objectString() + "(identifier=${identifier}, input=${getInputString()})"
    }

    fun setInput(input: ModuleNodeRecordInterface) {
        if (inputNode != null || subInterfaces.values.any { it.hasInput() }) throw Exception("Interface already has input")

        inputNode = input
    }

    fun setInput(key: String, input: ModuleNodeRecordInterface) {
        if (inputNode != null || subInterfaces[key]!!.hasInput()) throw Exception("Interface already has input")

        subInterfaces[key]!!.setInput(input)
    }

    override fun getInputString(): String {
        return if (hasInput())
            inputNode?.objectString() ?: "{${subInterfaces.map { "${it.key}=${it.value.getInputString()}" }.joinToString(", ")}}"
        else
            "null"
    }
}

data class ModuleNodeTranslatableInterface(
    val identifier: String,
    val subInterfaces: Map<String, List<ModuleNodeWireInterface>>,
) {
    companion object {
        fun fromModuleNodeInterface(identifier: String, moduleNodeInterface: ModuleNodeInterface): ModuleNodeTranslatableInterface {
            return when (moduleNodeInterface) {
                is ModuleNodeWireInterface -> {
                    // I.e., interface { wire[1]; }
                    ModuleNodeTranslatableInterface(
                        identifier = identifier,
                        subInterfaces = mapOf(identifier to listOf(moduleNodeInterface)),
                    )
                }
                is ModuleNodeRecordInterface -> {
                    ModuleNodeTranslatableInterface(
                        identifier = identifier,
                        subInterfaces = moduleNodeInterface.subInterfaces
                            .map { port ->
                                fromModuleNodeInterface(identifier, port.value).subInterfaces.mapKeys { "${port.key}_${it.key}" }
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

                            ModuleNodeTranslatableInterface(
                                identifier = identifier,
                                subInterfaces = mapOf(identifier to subInterfaces),
                            )
                        }
                        moduleNodeInterface.subInterfaces.all { it is ModuleNodeVectorInterface } -> {
                            // Fuck type erasure
                            val subInterfaces = moduleNodeInterface.subInterfaces
                                .filterIsInstance<ModuleNodeVectorInterface>()
                                .flatMap { it.subInterfaces }

                            fromModuleNodeInterface(
                                identifier = identifier,
                                moduleNodeInterface = ModuleNodeVectorInterface(identifier, subInterfaces)
                            )
                        }
                        moduleNodeInterface.subInterfaces.all { it is ModuleNodeRecordInterface } -> {
                            val portNames = moduleNodeInterface.subInterfaces
                                .filterIsInstance<ModuleNodeRecordInterface>()
                                .first()
                                .subInterfaces
                                .keys

                            val subInterfaces = portNames.associateWith { key ->
                                ModuleNodeVectorInterface(
                                    identifier = identifier,
                                    subInterfaces = moduleNodeInterface.subInterfaces
                                        .filterIsInstance<ModuleNodeRecordInterface>()
                                        .map { it.subInterfaces[key]!! }
                                )
                            }

                            fromModuleNodeInterface(
                                identifier = identifier,
                                moduleNodeInterface = ModuleNodeRecordInterface(identifier, subInterfaces),
                            )
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