package com.uabutler.netlistir.transformer.retiming.delay

import com.uabutler.netlistir.netlist.Node

interface PropagationDelay {

    fun forNode(node: Node): Int

}