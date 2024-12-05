package com.uabutler.ast

sealed class GAPLNode

abstract class TemporaryNode: GAPLNode()
abstract class PersistentNode: GAPLNode()

class EmptyNode: TemporaryNode()