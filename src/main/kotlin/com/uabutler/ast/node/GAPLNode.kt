package com.uabutler.ast.node

sealed interface GAPLNode

interface TemporaryNode: GAPLNode
interface PersistentNode: GAPLNode