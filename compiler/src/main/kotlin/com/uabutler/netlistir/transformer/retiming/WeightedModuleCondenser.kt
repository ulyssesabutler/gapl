package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.transformer.retiming.graph.WeightedModule

object WeightedModuleCondenser {

    fun condenseWeightedModule(weightedModule: WeightedModule): WeightedModule {
        val weightGroups = weightedModule.connections.groupBy { it.source }.values.flatMap { sourceGroups ->
            sourceGroups.groupBy { it.sink }.values.flatMap { sourceSinkGroup ->
                sourceSinkGroup.groupBy { it.weight }.values
            }
        }

        val groupedConnections = weightGroups.map { weightGroup ->
            val source = weightGroup.first().source
            val sink = weightGroup.first().sink
            val weight = weightGroup.first().weight

            // Validation
            weightGroup.forEach { connection ->
                if (source != connection.source) throw Exception("Compiler Bug: Invariant violation, Source mismatch")
                if (sink != connection.sink) throw Exception("Compiler Bug: Invariant violation, Sink mismatch")
                if (weight != connection.weight) throw Exception("Compiler Bug: Invariant violation, Weight mismatch")
            }

            val connectionGroups = weightGroup.flatMap { it.connectionGroups }.groupBy { it.group }.map { (wireVectorGroup, connectionGroups) ->
                WeightedModule.InputGroupConnection(
                    group = wireVectorGroup,
                    connections = connectionGroups.flatMap { it.connections }
                )
            }

            WeightedModule.WeightedConnection(
                source = source,
                sink = sink,
                weight = weight,
                connectionGroups = connectionGroups,
            )
        }

        return WeightedModule(
            module = weightedModule.module,
            nodes = weightedModule.nodes,
            connections = groupedConnections,
        )
    }

}