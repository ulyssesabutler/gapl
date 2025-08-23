package com.uabutler.resolver.scope.interfaces

import com.uabutler.ast.node.interfaces.AliasInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.InterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfaceDefinitionNode
import com.uabutler.ast.node.interfaces.RecordInterfacePortNode
import com.uabutler.cst.node.CSTPersistent
import com.uabutler.cst.node.interfaces.CSTAliasInterfaceDefinition
import com.uabutler.cst.node.interfaces.CSTInterfaceDefinition
import com.uabutler.cst.node.interfaces.CSTRecordInterfaceDefinition
import com.uabutler.resolver.scope.ProgramScope
import com.uabutler.resolver.scope.Scope
import com.uabutler.resolver.scope.Scope.Companion.toIdentifier
import com.uabutler.resolver.scope.util.GenericParameterDefinitionScope

class InterfaceDefinitionScope(
    override val parentScope: ProgramScope,
    val interfaceDefinition: CSTInterfaceDefinition,
): Scope {

    private val genericParameters = interfaceDefinition.parameterDefinitions.associateBy { it.declaredIdentifier }

    private val localSymbolTable = genericParameters

    override fun resolveLocal(name: String): CSTPersistent? {
        return localSymbolTable[name]
    }

    override fun symbols(): List<String> {
        return buildList {
            interfaceDefinition.parameterDefinitions.mapTo(this) { it.declaredIdentifier }
        }
    }

    fun ast(): InterfaceDefinitionNode {
        return when (interfaceDefinition) {
            is CSTAliasInterfaceDefinition -> {
                val identifier = interfaceDefinition.declaredIdentifier.toIdentifier()
                val parameters = GenericParameterDefinitionScope(this, interfaceDefinition.parameterDefinitions).ast()
                val aliasedInterface = InterfaceExpressionScope(this, interfaceDefinition.aliasedInterface).ast()

                AliasInterfaceDefinitionNode(
                    identifier = identifier,
                    genericInterfaces = parameters.interfaceDefinitions,
                    genericParameters = parameters.parameterDefinitions,
                    aliasedInterface = aliasedInterface,
                )
            }

            is CSTRecordInterfaceDefinition -> {
                val identifier = interfaceDefinition.declaredIdentifier.toIdentifier()
                val parameters = GenericParameterDefinitionScope(this, interfaceDefinition.parameterDefinitions).ast()
                val ports = interfaceDefinition.ports.map { port ->
                    RecordInterfacePortNode(
                        identifier = port.declaredIdentifier.toIdentifier(),
                        type = InterfaceExpressionScope(this, port.interfaceType).ast(),
                    )
                }

                RecordInterfaceDefinitionNode(
                    identifier = identifier,
                    genericInterfaces = parameters.interfaceDefinitions,
                    genericParameters = parameters.parameterDefinitions,
                    inherits = emptyList(), // TODO: We don't really support this yet
                    ports = ports,
                )
            }
        }
    }
}