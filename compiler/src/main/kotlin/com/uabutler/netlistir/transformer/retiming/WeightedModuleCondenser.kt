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
                if (connection.connectionGroups.size != 1) throw Exception("Compiler Bug: Invariant violation, Connection group already has more than 1 item")
            }

            val groupGroups = weightGroup.groupBy { it.connectionGroups.first().group }.values

            val groups = groupGroups.map { groupGroup ->
                WeightedModule.InputGroupConnection(
                    group = groupGroup.first().connectionGroups.first().group,
                    connections = groupGroup.flatMap { it.connectionGroups.first().connections }
                )
            }

            WeightedModule.WeightedConnection(
                source = source,
                sink = sink,
                weight = weight,
                connectionGroups = groups
            )
        }

        return WeightedModule(
            module = weightedModule.module,
            nodes = weightedModule.nodes,
            connections = groupedConnections,
        )
    }

}