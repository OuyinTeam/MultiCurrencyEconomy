package top.wcpe.mc.plugin.multicurrencyeconomy.internal.test

data class FunctionalTestCaseResult(
    val suiteName: String,
    val caseName: String,
    val success: Boolean,
    val reason: String,
    val elapsedMs: Long
)

data class FunctionalTestSuiteResult(
    val suiteName: String,
    val caseResults: List<FunctionalTestCaseResult>,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long
) {
    val passedCount: Int
        get() = caseResults.count { it.success }

    val failedCount: Int
        get() = caseResults.size - passedCount

    val success: Boolean
        get() = failedCount == 0

    val durationMs: Long
        get() = (finishedAtEpochMs - startedAtEpochMs).coerceAtLeast(0L)
}
