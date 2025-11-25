package com.uabutler.util

import java.util.Locale

object Timer {

    data class Duration(val startTime: Long, val endTime: Long)

    class Instance {
        var currentStartTime: Long? = null
        val times: MutableList<Duration> = mutableListOf()

        fun start() {
            if (currentStartTime != null) throw IllegalStateException("Timer is already running")
            currentStartTime = System.currentTimeMillis()
        }

        fun stop() {
            val endTime = System.currentTimeMillis()
            if (currentStartTime == null) throw IllegalStateException("Timer is not running")
            times.add(Duration(currentStartTime!!, endTime))
            currentStartTime = null
        }
    }

    val instances = mutableMapOf<String, Instance>()

    fun create(name: String) = instances.putIfAbsent(name, Instance())
    fun start(name: String) = instances.getOrPut(name) { Instance() }.start()
    fun stop(name: String) = instances[name]!!.stop()
    fun getTimes(name: String): String = instances[name]!!.times.sumOf { it.endTime - it.startTime }.let { "%.2fs".format(Locale.US, it / 1000.0) }

}