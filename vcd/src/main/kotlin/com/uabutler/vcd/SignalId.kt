package com.uabutler.vcd

/**
 * Opaque handle to a declared signal, returned by [VcdWriter.declareSignal] and passed back into
 * [VcdWriter.dumpInitialValues]/[VcdWriter.writeValue]. Must stay a plain public type (not
 * `internal`) — callers in other Gradle subprojects (e.g. a future simengine integration) need to
 * hold and pass these across module boundaries, and `internal` is invisible across that boundary.
 */
data class SignalId(val index: Int)
