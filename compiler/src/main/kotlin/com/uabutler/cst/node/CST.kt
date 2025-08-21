package com.uabutler.cst.node

sealed interface CST

interface CSTTemporary: CST
interface CSTPersistent: CST
object CSTEmpty: CST