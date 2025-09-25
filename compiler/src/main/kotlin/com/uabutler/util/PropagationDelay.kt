package com.uabutler.util

import com.uabutler.netlistir.netlist.Node

interface PropagationDelay {

    fun forNode(node: Node): Int

}