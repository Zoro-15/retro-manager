package com.retropack.domain.model

import java.io.File

/**
 * Result record emitted by the BuildEngine upon transformation completion.
 * Contains cryptographic provenance, certificate fingerprints, and stage logs.
 */
data class BuildResult(
    val success: Boolean,
    val artifactFile: File?,
    val packageName: String,
    val versionCode: Int,
    val certificateSha256Fingerprint: String,
    val stageProvenance: List<BuildStageRecord>,
    val durationMs: Long,
    val errorMessage: String? = null
)

data class BuildStageRecord(
    val stageNumber: Int,
    val stageName: String,
    val description: String,
    val durationMs: Long,
    val passed: Boolean
)
