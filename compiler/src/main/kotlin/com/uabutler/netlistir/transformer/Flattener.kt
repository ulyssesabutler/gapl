package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode

object Flattener: Transformer {
    private class Helper(val original: List<Module>) {
        val modules = original.associateBy { it.invocation }
        val flattenedModules = mutableMapOf<Module.Invocation, Module>()

        fun rootModules(): List<Module> {
            val couldBeRoot = original.associate { it.invocation to true }.toMutableMap()

            val invocations = original.flatMap { it.getBodyNodes() }
                .filterIsInstance<ModuleInvocationNode>()
                .map { it.invocation }
                .toSet()

            invocations.forEach { couldBeRoot[it] = false }

            return couldBeRoot.filterValues { it }.keys.map { modules[it]!! }
        }

        fun inlineModuleInvocation(node: ModuleInvocationNode) {
            val currentModule = node.parentModule
            val inliningModule = flattenModule(modules[node.invocation]!!)

            /* TODO:
             *   1. Create a new node in current module for every node in inline module
             *     a. Give each node a name equals to the origin node name prepended with something to make it unique
             *     b. As a validation, throw an exception if any nodes are module invocation nodes
             *     c. Replace input and output nodes with passthrough nodes
             *     d. Be sure to keep track of the input and output nodes, and map these to wire vector groups
             *     e. This mapping should be done based on the identifier of the node and wire group, they should line up. Throw an exception if they don't.
             *   2. Disconnect each wire vector group on the module invocation node, create a connection to input/output nodes
             */
        }

        fun flattenModule(module: Module) {
            if (module.invocation in flattenedModules) return

            module.getBodyNodes()
                .filterIsInstance<ModuleInvocationNode>()
                .forEach { inlineModuleInvocation(it) }

            flattenedModules[module.invocation] = module
        }

        fun flatten(): List<Module> {
            return rootModules().onEach { flattenModule(it) }
        }
    }

    override fun transform(original: List<Module>): List<Module> {
        return Helper(original).flatten()
    }
}