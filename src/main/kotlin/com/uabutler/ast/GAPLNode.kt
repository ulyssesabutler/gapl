package com.uabutler.ast

sealed interface GAPLNode

interface TemporaryNode: GAPLNode
interface PersistentNode: GAPLNode