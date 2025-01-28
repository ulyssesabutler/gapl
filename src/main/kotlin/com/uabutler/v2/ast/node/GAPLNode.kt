package com.uabutler.v2.ast.node

sealed interface GAPLNode

interface TemporaryNode: GAPLNode
interface PersistentNode: GAPLNode