package com.darshpandya.taskmaster.ui

/** A value that should be handled once, such as a snackbar message. */
class Event<out T>(private val content: T) {
    private var handled = false

    fun consume(): T? = if (handled) null else content.also { handled = true }

    fun peek(): T = content
}
