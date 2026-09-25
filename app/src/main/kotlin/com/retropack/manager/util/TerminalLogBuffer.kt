package com.retropack.manager.util

/**
 * Bounded, thread-safe terminal log buffer (last [maxLines] lines).
 *
 * Regression background: the original implementation appended unbounded to a
 * StringBuilder while the UI expected a 2000-line rolling window; the stale
 * unit test encoding that expectation no longer compiled (and app unit tests
 * were never executed in CI, so the rot was invisible). This class restores
 * the intended rolling behavior and is directly unit-testable.
 */
class TerminalLogBuffer(private val maxLines: Int = DEFAULT_MAX_LINES) {

    private val lines = ArrayDeque<String>(maxLines.coerceAtLeast(1))

    @Synchronized
    fun append(line: String) {
        if (lines.size >= maxLines) {
            lines.removeFirst()
        }
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): String = lines.joinToString(SEPARATOR)

    @Synchronized
    fun clear() {
        lines.clear()
    }

    @Synchronized
    fun size(): Int = lines.size

    companion object {
        const val DEFAULT_MAX_LINES = 2000
        private const val SEPARATOR = "\n"
    }
}
