package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.PassThroughNode

object PassThroughRemover: Transformer {
    override fun transform(original: List<Module>): List<Module> {
        return original.map { transformModule(it) }
    }

    private fun transformModule(module: Module): Module {
        val passThroughNodes = module.getBodyNodes().filterIsInstance<PassThroughNode>()

        passThroughNodes.forEach {
            disconnectPassThroughNode(it)
            module.removeNode(it)
        }

        return module
    }

    private fun disconnectPassThroughNode(node: Node) {
        val module = node.parentModule

        val passThroughWirePairs = node.inputWires().zip(node.outputWires())

        /* Original Source -> PassThrough Input -> PassThrough Output ---> Original Sink 1
         *                                                             \-> Original Sink 2
         *
         * PassThrough wire pairs represent the pair (PassThrough Input, PassThrough Output)
         * For each connection from the PassThrough Output to a given Original Sink, we
         *   1. Disconnect that Original Sink from the PassThrough Output
         *   2. Connect that Original Sink to the Original Source
         * Once all the Original Sinks are now connected to the Original Sources, we can also
         * remove the connection of the PassThrough Input to the Original Source.
         */
        passThroughWirePairs.forEach { (input, output) ->
            val originalSource = module.getConnectionForInputWire(input).source

            val originalSinkConnection = module.getConnectionsForOutputWire(output)

            originalSinkConnection.forEach { connection ->
                module.disconnect(connection.sink)
                module.connect(connection.sink, originalSource)
            }

            module.disconnect(input)
        }
    }
}