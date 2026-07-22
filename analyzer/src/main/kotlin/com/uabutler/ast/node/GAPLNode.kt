package com.uabutler.ast.node

import com.uabutler.diagnostics.SourceSpan

interface GAPLNode {
    val span: SourceSpan
}
