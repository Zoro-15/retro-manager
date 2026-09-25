package com.retropack.domain.rom

/**
 * Thrown when a ROM fails structural or header validation checks.
 */
class InvalidRomException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Thrown when a binary patch fails signature, structure, or checksum validation checks.
 */
class InvalidPatchException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
