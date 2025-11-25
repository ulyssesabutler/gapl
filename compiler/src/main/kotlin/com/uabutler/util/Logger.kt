package com.uabutler.util

object Logger {

    // Default level
    private var level: Level = Level.DEBUG

    data class Layer(val name: String, val startTime: Long)

    private val stack: MutableList<Layer> = mutableListOf()

    enum class Level(val number: Int) {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3),
    }

    private fun print(message: String) {
        val string = buildString {
            append(" ".repeat(stack.size * 2))
            append(message)
        }

        println(string)
    }

    private fun writeLog(message: String, level: Level) {
        if (level.number >= this.level.number) {
            print(message)
        }
    }

    fun setLevel(level: Level) { this.level = level }
    fun debug(message: String) = writeLog(message, Level.DEBUG)
    fun info(message: String) = writeLog(message, Level.INFO)
    fun warn(message: String) = writeLog(message, Level.WARN)
    fun error(message: String) = writeLog(message, Level.ERROR)

    fun start(name: String) {
        debug("STARTING $name")
        stack.add(Layer(name, System.currentTimeMillis()))
    }

    fun finish() {
        val current = stack.removeLast()
        debug("FINISHED ${current.name} in ${System.currentTimeMillis() - current.startTime}ms")
    }

}