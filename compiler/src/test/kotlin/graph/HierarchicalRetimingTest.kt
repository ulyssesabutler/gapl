package graph

import com.uabutler.util.Logger
import graph.TestUtil.createHierarchicalGraph
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class HierarchicalRetimingTest {

    @BeforeEach
    fun `setup logger`() {
        Logger.setLevel(Logger.Level.WARN)
    }

    @Test
    fun `successfully create hierarchical graph`() {
        val hierarchicalGraph = createHierarchicalGraph(
            name = "hierarchical graph",
            edgeList = listOf(
                EdgeSketch("i", "c", 1),
                EdgeSketch("c", "o", 1),
            ),
            contractedOverride = mapOf(
                "c" to ContractSketch(
                    inputOutputDelay = 1 to 1,
                    combinationalDelay = 1,
                    addedRegisters = 0,
                )
            )
        )

        hierarchicalGraph.print()
    }
}