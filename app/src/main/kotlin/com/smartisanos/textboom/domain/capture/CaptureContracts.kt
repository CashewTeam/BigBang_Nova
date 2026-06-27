package com.smartisanos.textboom.domain.capture

data class CaptureTextBlockContract(
    val text: String,
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val confidence: Double,
)

data class CaptureResultContract(
    val source: String,
    val blocks: List<CaptureTextBlockContract>,
    val durationMs: Int,
    val debugMessage: String,
)

data class TextSessionInputContract(
    val captureResult: CaptureResultContract,
    val rawLines: List<String>,
    val originalText: String,
    val source: String,
    val sessionId: String,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val revision: Int,
)
