package com.uabutler.util

import com.uabutler.util.Timer.instances
import java.util.Locale

object Logger {

    // Default level
    private var level: Level = Level.DEBUG

    data class Layer(val name: String, val startTime: Long, val level: Level)

    private val stack: MutableList<Layer> = mutableListOf()

    enum class Level(val number: Int, val prefix: String) {
        TRACE(0, "[TRACE] "),
        DEBUG(1, "[DEBUG] "),
        INFO (2, "[INFO]  "),
        WARN (3, "[WARN]  "),
        ERROR(4, "[ERROR] "),
    }

    private fun printLog(message: String, level: Level) {
        val string = buildString {
            append(level.prefix)
            append("  ".repeat(stack.size))
            append(message)
        }

        println(string)
    }

    private fun writeLog(message: String, level: Level) {
        if (level.number >= this.level.number) {
            printLog(message, level)
        }
    }

    private fun <T> runIf(function: () -> T, level: Level): T? {
        return if (level.number >= this.level.number) {
            function()
        } else {
            null
        }
    }

    private fun runLogger(message: () -> String, level: Level) {
        runIf({ writeLog(message(), level) }, level)
    }

    fun setLevel(level: Level) { this.level = level }

    fun trace(message: () -> String) = runLogger(message, Level.TRACE)
    fun debug(message: () -> String) = runLogger(message, Level.DEBUG)
    fun info(message: () -> String) = runLogger(message, Level.INFO)
    fun warn(message: () -> String) = runLogger(message, Level.WARN)
    fun error(message: () -> String) = runLogger(message, Level.ERROR)

    fun <T> ifTrace(function: () -> T) = runIf(function, Level.TRACE)
    fun <T> ifDebug(function: () -> T) = runIf(function, Level.DEBUG)
    fun <T> ifInfo(function: () -> T) = runIf(function, Level.INFO)
    fun <T> ifWarn(function: () -> T) = runIf(function, Level.WARN)
    fun <T> ifError(function: () -> T) = runIf(function, Level.ERROR)

    fun <T> run(name: String, level: Level = Level.DEBUG, block: () -> T): T {
        start(name, level)

        val ret = try {
            block()
        } catch (e: Exception) {
            printLog("Unhandled Exception: ${e::class.simpleName}", Level.ERROR)
            finish()
            throw e
        }

        finish()
        return ret
    }

    fun start(name: String, level: Level = Level.DEBUG) {
        writeLog("STARTING $name", level)
        stack.add(Layer(name, System.currentTimeMillis(), level))
    }

    private fun durationString(duration: Long) = "%.2fs".format(Locale.US, duration / 1000.0)

    fun finish() {
        val current = stack.removeLast()
        writeLog("FINISHED ${current.name} in ${durationString(System.currentTimeMillis() - current.startTime)}", current.level)
    }

}