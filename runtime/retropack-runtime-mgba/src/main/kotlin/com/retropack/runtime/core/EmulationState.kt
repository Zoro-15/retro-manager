package com.retropack.runtime.core

/**
 * Formal state machine representing the lifecycle of an emulation session.
 *
 * State Machine Graph:
 * - UNINITIALIZED -> INITIALIZED | ERROR
 * - INITIALIZED   -> RUNNING | STOPPED | ERROR
 * - RUNNING       -> PAUSED | STOPPED | ERROR
 * - PAUSED        -> RUNNING | STOPPED | ERROR
 * - STOPPED       -> INITIALIZED | UNINITIALIZED | ERROR
 * - ERROR         -> STOPPED | UNINITIALIZED
 */
enum class EmulationState {
    /** The native core environment has not yet been initialized. */
    UNINITIALIZED,

    /** Core initialized, ROM loaded and verified, ready for execution. */
    INITIALIZED,

    /** Active emulation frame loop running at display refresh rate. */
    RUNNING,

    /** Emulation loop suspended (e.g. Activity paused, menu opened). Emulated state preserved. */
    PAUSED,

    /** Emulation ceased, ROM unloaded, hardware state discarded or written to save. */
    STOPPED,

    /** Fatal execution or initialization fault encountered. */
    ERROR;

    /**
     * Returns true if emulation is actively loaded (either currently executing or paused).
     */
    val isEmulating: Boolean
        get() = this == RUNNING || this == PAUSED

    /**
     * Returns true if the core is initialized and ready to run or load states.
     */
    val isReady: Boolean
        get() = this == INITIALIZED || this == RUNNING || this == PAUSED

    /**
     * Returns true if the core is currently inactive or stopped.
     */
    val isHalted: Boolean
        get() = this == UNINITIALIZED || this == STOPPED || this == ERROR

    /**
     * Validates whether a state transition from this state to [next] is legal.
     */
    fun canTransitionTo(next: EmulationState): Boolean {
        if (this == next) return true
        return when (this) {
            UNINITIALIZED -> next == INITIALIZED || next == ERROR
            INITIALIZED   -> next == RUNNING || next == STOPPED || next == ERROR
            RUNNING       -> next == PAUSED || next == STOPPED || next == ERROR
            PAUSED        -> next == RUNNING || next == STOPPED || next == ERROR
            STOPPED       -> next == INITIALIZED || next == UNINITIALIZED || next == ERROR
            ERROR         -> next == STOPPED || next == UNINITIALIZED
        }
    }

    /**
     * Enforces that transitioning from this state to [next] is valid, throwing [IllegalStateException] otherwise.
     */
    fun checkTransition(next: EmulationState) {
        check(canTransitionTo(next)) {
            "Illegal emulation state transition: cannot transition from $this to $next"
        }
    }
}
