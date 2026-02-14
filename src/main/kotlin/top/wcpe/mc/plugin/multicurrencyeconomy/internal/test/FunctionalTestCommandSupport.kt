package top.wcpe.mc.plugin.multicurrencyeconomy.internal.test

import org.bukkit.command.CommandSender
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.async.AsyncExecutor

/**
 * 功能测试命令输出器。
 *
 * 约定：
 * 1. 输出采用 JSON 风格字符串，便于管理员复制解析。
 * 2. 所有字段与文案均使用中文，满足运维本地化需求。
 * 3. 提供单测试集与多测试集汇总两种输出模式。
 */
object FunctionalTestCommandSupport {

    private const val PREFIX = "[MCE-测试] "

    fun runSuite(
        sender: CommandSender,
        suiteName: String,
        runner: () -> FunctionalTestSuiteResult
    ) {
        sender.sendMessage("$PREFIX{\"测试集\":\"${escape(suiteName)}\",\"状态\":\"运行中\"}")
        AsyncExecutor.runAsync {
            val report = runCatching(runner).getOrElse { ex ->
                FunctionalTestSuiteResult(
                    suiteName = suiteName,
                    caseResults = listOf(
                        FunctionalTestCaseResult(
                            suiteName = suiteName,
                            caseName = "测试集执行",
                            success = false,
                            reason = "测试集执行异常：${ex.message ?: "无异常信息"}",
                            elapsedMs = 0L
                        )
                    ),
                    startedAtEpochMs = System.currentTimeMillis(),
                    finishedAtEpochMs = System.currentTimeMillis()
                )
            }
            sendSuiteReport(sender, report)
        }
    }

    fun runSuites(
        sender: CommandSender,
        suitesName: String,
        runner: () -> List<FunctionalTestSuiteResult>
    ) {
        sender.sendMessage("$PREFIX{\"测试集\":\"${escape(suitesName)}\",\"状态\":\"运行中\"}")
        AsyncExecutor.runAsync {
            val reports = runCatching(runner).getOrElse { ex ->
                listOf(
                    FunctionalTestSuiteResult(
                        suiteName = suitesName,
                        caseResults = listOf(
                            FunctionalTestCaseResult(
                                suiteName = suitesName,
                                caseName = "测试集执行",
                                success = false,
                                reason = "测试集执行异常：${ex.message ?: "无异常信息"}",
                                elapsedMs = 0L
                            )
                        ),
                        startedAtEpochMs = System.currentTimeMillis(),
                        finishedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
            reports.forEach { sendSuiteReport(sender, it) }

            val totalCases = reports.sumOf { it.caseResults.size }
            val totalPassed = reports.sumOf { it.passedCount }
            val totalFailed = reports.sumOf { it.failedCount }
            val totalDuration = reports.sumOf { it.durationMs }
            val status = if (totalFailed == 0) "成功" else "失败"
            sender.sendMessage(
                "$PREFIX" +
                    "{\"测试集\":\"${escape(suitesName)}\",\"状态\":\"$status\"," +
                    "\"总用例\":$totalCases,\"通过\":$totalPassed,\"失败\":$totalFailed,\"总耗时毫秒\":$totalDuration}"
            )
        }
    }

    private fun sendSuiteReport(sender: CommandSender, report: FunctionalTestSuiteResult) {
        report.caseResults.forEach { case ->
            val status = if (case.success) "成功" else "失败"
            sender.sendMessage(
                "$PREFIX" +
                    "{\"测试集\":\"${escape(case.suiteName)}\",\"用例\":\"${escape(case.caseName)}\",\"状态\":\"$status\"," +
                    "\"原因\":\"${escape(case.reason)}\",\"耗时毫秒\":${case.elapsedMs}}"
            )
        }

        val status = if (report.success) "成功" else "失败"
        sender.sendMessage(
            "$PREFIX" +
                "{\"测试集\":\"${escape(report.suiteName)}\",\"状态\":\"$status\"," +
                "\"总用例\":${report.caseResults.size},\"通过\":${report.passedCount}," +
                "\"失败\":${report.failedCount},\"总耗时毫秒\":${report.durationMs}}"
        )
    }

    private fun escape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "'")
    }
}
