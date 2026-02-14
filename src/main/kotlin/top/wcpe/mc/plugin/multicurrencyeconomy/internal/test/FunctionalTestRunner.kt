package top.wcpe.mc.plugin.multicurrencyeconomy.internal.test

import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.EconomyResult
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 游戏内功能测试执行器。
 *
 * 设计目标：
 * 1. 允许管理员在运行期验证核心能力（CRUD、并发、异常稳定性）。
 * 2. 每个用例返回结构化结果：成功/失败 + 原因 + 耗时。
 * 3. 测试数据自动隔离并清理，避免污染正式业务数据。
 */
object FunctionalTestRunner {

    private const val DEFAULT_OPERATOR = "功能测试执行器"
    private const val DEFAULT_REASON_PREFIX = "功能测试"

    private data class CaseOutcome(val success: Boolean, val reason: String)

    private data class ConcurrentExecutionResult(
        val successCount: Int,
        val failureCount: Int,
        val exceptionCount: Int,
        val timedOut: Boolean
    )

    /**
     * 货币 CRUD 功能测试：
     * - 创建、查询、重复创建拦截、精度边界、启用禁用、删除。
     */
    fun runCurrencyCrudSuite(): FunctionalTestSuiteResult {
        val suite = "货币增删改查"
        val start = System.currentTimeMillis()
        val results = mutableListOf<FunctionalTestCaseResult>()

        val token = uniqueToken()
        val currencyId = "eco_t_cur_$token"
        val boundaryCurrencyId = "eco_t_bnd_$token"
        var createdNumericId = -1

        try {
            results += runCase(suite, "创建货币") {
                val created = CurrencyService.createCurrency(
                    identifier = currencyId,
                    name = "ECO_TEST_$token",
                    precision = 2,
                    symbol = "T",
                    defaultMaxBalance = 1_000_000L,
                    consoleLog = false
                ) ?: return@runCase fail("创建货币失败：createCurrency 返回 null")

                createdNumericId = created.id
                if (created.identifier != currencyId) {
                    return@runCase fail("创建后标识不一致：期望=$currencyId，实际=${created.identifier}")
                }
                pass("创建成功，货币ID=$createdNumericId")
            }

            results += runCase(suite, "查询货币") {
                val byIdentifier = CurrencyService.getByIdentifier(currencyId)
                if (byIdentifier == null) {
                    return@runCase fail("按标识查询失败：返回 null")
                }
                val byId = CurrencyService.getById(createdNumericId)
                    ?: return@runCase fail("按 ID 查询失败：返回 null")
                if (byId.identifier != currencyId) {
                    return@runCase fail("按 ID 查询标识错误：${byId.identifier}")
                }
                pass("按标识和按ID查询均成功")
            }

            results += runCase(suite, "重复创建应拒绝") {
                val duplicate = CurrencyService.createCurrency(
                    identifier = currencyId,
                    name = "DUPLICATE",
                    precision = 2,
                    symbol = "D",
                    defaultMaxBalance = -1L,
                    consoleLog = false
                )
                if (duplicate != null) {
                    return@runCase fail("重复创建未被拒绝：返回了非空结果")
                }
                pass("重复创建已正确拒绝")
            }

            results += runCase(suite, "精度边界限制") {
                val boundary = CurrencyService.createCurrency(
                    identifier = boundaryCurrencyId,
                    name = "BOUNDARY",
                    precision = 99,
                    symbol = "B",
                    defaultMaxBalance = -1L,
                    consoleLog = false
                ) ?: return@runCase fail("边界精度货币创建失败：返回 null")

                if (boundary.precision != 8) {
                    return@runCase fail("精度限制失败：期望8，实际=${boundary.precision}")
                }
                pass("精度限制生效：99 已限制为 8")
            }

            results += runCase(suite, "启用禁用修改") {
                val disabled = CurrencyService.disableCurrency(currencyId)
                if (!disabled) return@runCase fail("禁用货币失败：disableCurrency 返回 false")
                if (CurrencyService.getByIdentifier(currencyId)?.enabled != false) {
                    return@runCase fail("禁用后缓存状态不正确")
                }

                val enabled = CurrencyService.enableCurrency(currencyId)
                if (!enabled) return@runCase fail("启用货币失败：enableCurrency 返回 false")
                if (CurrencyService.getByIdentifier(currencyId)?.enabled != true) {
                    return@runCase fail("启用后缓存状态不正确")
                }
                pass("启用与禁用切换均成功")
            }

            results += runCase(suite, "删除货币") {
                val deleted = CurrencyService.deleteCurrency(currencyId)
                if (!deleted) {
                    return@runCase fail("删除货币失败：deleteCurrency 返回 false")
                }
                if (CurrencyService.getByIdentifier(currencyId) != null) {
                    return@runCase fail("删除后仍可查询到该货币")
                }
                pass("删除成功，活动货币列表已不可见")
            }

            return FunctionalTestSuiteResult(
                suiteName = suite,
                caseResults = results,
                startedAtEpochMs = start,
                finishedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            cleanupCurrencies(currencyId, boundaryCurrencyId)
        }
    }

    /**
     * 账户隔离功能测试：
     * - 同一玩家在不同货币下余额独立。
     * - 单货币扣款不会影响其他货币。
     * - 余额不足时应失败且无副作用。
     */
    fun runAccountIsolationSuite(): FunctionalTestSuiteResult {
        val suite = "账户隔离性"
        val start = System.currentTimeMillis()
        val results = mutableListOf<FunctionalTestCaseResult>()

        val token = uniqueToken()
        val currencyA = "eco_t_a_$token"
        val currencyB = "eco_t_b_$token"
        val playerName = "eco_test_player_$token"
        val playerUuid = uuidFromName(playerName)

        try {
            results += runCase(suite, "准备测试货币") {
                val a = CurrencyService.createCurrency(currencyA, "ACC_A_$token", 2, "A", -1L, false)
                val b = CurrencyService.createCurrency(currencyB, "ACC_B_$token", 2, "B", -1L, false)
                if (a == null || b == null) {
                    return@runCase fail("准备货币失败：至少有一个创建失败")
                }
                pass("测试货币准备完成")
            }

            results += runCase(suite, "不同货币余额独立") {
                val setA = AccountService.setBalanceDirect(
                    playerName, playerUuid, currencyA, BigDecimal.ZERO, "$DEFAULT_REASON_PREFIX:init-a", DEFAULT_OPERATOR
                )
                val setB = AccountService.setBalanceDirect(
                    playerName, playerUuid, currencyB, BigDecimal.ZERO, "$DEFAULT_REASON_PREFIX:init-b", DEFAULT_OPERATOR
                )
                if (!setA.success || !setB.success) {
                    return@runCase fail("初始化余额失败")
                }

                val depA = AccountService.depositDirect(
                    playerName, playerUuid, currencyA, BigDecimal("120"), "$DEFAULT_REASON_PREFIX:dep-a", DEFAULT_OPERATOR
                )
                val depB = AccountService.depositDirect(
                    playerName, playerUuid, currencyB, BigDecimal("45"), "$DEFAULT_REASON_PREFIX:dep-b", DEFAULT_OPERATOR
                )
                if (!depA.success || !depB.success) {
                    return@runCase fail("入账失败：A=${depA.message}，B=${depB.message}")
                }

                val balanceA = AccountService.getBalanceFromDb(playerName, currencyA)
                val balanceB = AccountService.getBalanceFromDb(playerName, currencyB)
                if (balanceA.compareTo(BigDecimal("120.00")) != 0) {
                    return@runCase fail("货币A余额错误：$balanceA")
                }
                if (balanceB.compareTo(BigDecimal("45.00")) != 0) {
                    return@runCase fail("货币B余额错误：$balanceB")
                }
                pass("多货币余额独立性验证通过")
            }

            results += runCase(suite, "扣款不跨货币影响") {
                val withdraw = AccountService.withdrawDirect(
                    playerName, playerUuid, currencyA, BigDecimal("20"), "$DEFAULT_REASON_PREFIX:take-a", DEFAULT_OPERATOR
                )
                if (!withdraw.success) {
                    return@runCase fail("扣款失败：${withdraw.message}")
                }

                val balanceA = AccountService.getBalanceFromDb(playerName, currencyA)
                val balanceB = AccountService.getBalanceFromDb(playerName, currencyB)
                if (balanceA.compareTo(BigDecimal("100.00")) != 0) {
                    return@runCase fail("扣款后货币A余额错误：$balanceA")
                }
                if (balanceB.compareTo(BigDecimal("45.00")) != 0) {
                    return@runCase fail("货币B余额被意外改变：$balanceB")
                }
                pass("货币A扣款未影响货币B")
            }

            results += runCase(suite, "余额不足应失败且不改余额") {
                val failed = AccountService.withdrawDirect(
                    playerName, playerUuid, currencyB, BigDecimal("1000"), "$DEFAULT_REASON_PREFIX:insufficient", DEFAULT_OPERATOR
                )
                if (failed.success) {
                    return@runCase fail("余额不足时扣款却成功")
                }
                val balanceB = AccountService.getBalanceFromDb(playerName, currencyB)
                if (balanceB.compareTo(BigDecimal("45.00")) != 0) {
                    return@runCase fail("失败扣款后余额被改变：$balanceB")
                }
                pass("余额不足已正确拒绝，且无副作用")
            }

            results += runCase(suite, "账户快照查询") {
                val accounts = AccountService.getPlayerAccountsFromDb(playerName)
                    .filter { it.currencyIdentifier == currencyA || it.currencyIdentifier == currencyB }
                if (accounts.size != 2) {
                    return@runCase fail("快照数量错误：期望2，实际=${accounts.size}")
                }
                pass("快照查询已包含两种货币")
            }

            return FunctionalTestSuiteResult(
                suiteName = suite,
                caseResults = results,
                startedAtEpochMs = start,
                finishedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            cleanupCurrencies(currencyA, currencyB)
        }
    }

    /**
     * 并发安全测试：
     * - 并发入账验证 CAS 一致性。
     * - 并发扣款验证不出现负值与核心不变量。
     */
    fun runConcurrencySuite(threads: Int = 16, operations: Int = 400): FunctionalTestSuiteResult {
        val suite = "并发安全"
        val start = System.currentTimeMillis()
        val results = mutableListOf<FunctionalTestCaseResult>()

        val workerCount = normalizeThreads(threads)
        val totalOps = normalizeOperations(operations)
        val token = uniqueToken()
        val currencyId = "eco_t_con_$token"
        val playerName = "eco_test_con_player_$token"
        val playerUuid = uuidFromName(playerName)

        try {
            results += runCase(suite, "准备并发测试货币") {
                val created = CurrencyService.createCurrency(
                    identifier = currencyId,
                    name = "CON_$token",
                    precision = 2,
                    symbol = "C",
                    defaultMaxBalance = -1L,
                    consoleLog = false
                )
                if (created == null) {
                    return@runCase fail("并发测试货币创建失败")
                }
                pass("并发测试货币准备完成")
            }

            results += runCase(suite, "并发入账CAS一致性") {
                val reset = AccountService.setBalanceDirect(
                    playerName, playerUuid, currencyId, BigDecimal.ZERO, "$DEFAULT_REASON_PREFIX:reset-deposit", DEFAULT_OPERATOR
                )
                if (!reset.success) {
                    return@runCase fail("重置余额失败：${reset.message}")
                }

                val stats = executeConcurrent(workerCount, totalOps) {
                    AccountService.depositDirect(
                        playerName, playerUuid, currencyId, BigDecimal.ONE,
                        "$DEFAULT_REASON_PREFIX:concurrent-deposit", DEFAULT_OPERATOR
                    )
                }
                val finalBalance = AccountService.getBalanceFromDb(playerName, currencyId)
                val expectedBySuccess = BigDecimal.valueOf(stats.successCount.toLong())

                if (stats.timedOut) return@runCase fail("并发执行超时")
                if (stats.exceptionCount > 0) {
                    return@runCase fail("并发执行出现异常次数=${stats.exceptionCount}")
                }
                if (finalBalance.compareTo(expectedBySuccess) != 0) {
                    return@runCase fail(
                        "最终余额不一致：余额=$finalBalance，成功次数=${stats.successCount}"
                    )
                }
                pass(
                    "并发入账一致性通过（线程=$workerCount，操作=$totalOps，成功=${stats.successCount}，失败=${stats.failureCount}）"
                )
            }

            results += runCase(suite, "并发扣款不出现负值") {
                val initial = BigDecimal.valueOf((totalOps / 2).coerceAtLeast(1).toLong())
                val reset = AccountService.setBalanceDirect(
                    playerName, playerUuid, currencyId, initial, "$DEFAULT_REASON_PREFIX:reset-withdraw", DEFAULT_OPERATOR
                )
                if (!reset.success) {
                    return@runCase fail("重置余额失败：${reset.message}")
                }

                val stats = executeConcurrent(workerCount, totalOps) {
                    AccountService.withdrawDirect(
                        playerName, playerUuid, currencyId, BigDecimal.ONE,
                        "$DEFAULT_REASON_PREFIX:concurrent-withdraw", DEFAULT_OPERATOR
                    )
                }
                val finalBalance = AccountService.getBalanceFromDb(playerName, currencyId)
                val reconstructed = finalBalance.add(BigDecimal.valueOf(stats.successCount.toLong()))

                if (stats.timedOut) return@runCase fail("并发执行超时")
                if (stats.exceptionCount > 0) {
                    return@runCase fail("并发执行出现异常次数=${stats.exceptionCount}")
                }
                if (finalBalance < BigDecimal.ZERO) {
                    return@runCase fail("检测到负余额：$finalBalance")
                }
                if (reconstructed.compareTo(initial) != 0) {
                    return@runCase fail(
                        "不变量被破坏：最终余额+成功次数!=初始余额（$finalBalance + ${stats.successCount} != $initial）"
                    )
                }
                if (stats.successCount > initial.toInt()) {
                    return@runCase fail("成功扣款次数超过初始余额")
                }
                pass(
                    "并发扣款一致性通过（线程=$workerCount，操作=$totalOps，成功=${stats.successCount}，失败=${stats.failureCount}）"
                )
            }

            return FunctionalTestSuiteResult(
                suiteName = suite,
                caseResults = results,
                startedAtEpochMs = start,
                finishedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            cleanupCurrencies(currencyId)
        }
    }

    /**
     * 稳定性测试：
     * - 非法输入应被拒绝。
     * - 上限保护与未知货币保护。
     * - 高并发混合读写下不崩溃、不出现负余额。
     */
    fun runStabilitySuite(threads: Int = 16, operations: Int = 300): FunctionalTestSuiteResult {
        val suite = "稳定性与异常输入"
        val start = System.currentTimeMillis()
        val results = mutableListOf<FunctionalTestCaseResult>()

        val workerCount = normalizeThreads(threads)
        val totalOps = normalizeOperations(operations)
        val token = uniqueToken()
        val strictCurrencyId = "eco_t_stb_$token"
        val stressCurrencyId = "eco_t_mix_$token"
        val playerName = "eco_test_stability_$token"
        val playerUuid = uuidFromName(playerName)

        try {
            results += runCase(suite, "准备稳定性测试货币") {
                val strict = CurrencyService.createCurrency(
                    identifier = strictCurrencyId,
                    name = "STRICT_$token",
                    precision = 2,
                    symbol = "S",
                    defaultMaxBalance = 10L,
                    consoleLog = false
                )
                val stress = CurrencyService.createCurrency(
                    identifier = stressCurrencyId,
                    name = "STRESS_$token",
                    precision = 2,
                    symbol = "M",
                    defaultMaxBalance = -1L,
                    consoleLog = false
                )
                if (strict == null || stress == null) {
                    return@runCase fail("准备货币失败")
                }
                pass("稳定性测试货币准备完成")
            }

            results += runCase(suite, "非法输入应拒绝") {
                val negativeDeposit = AccountService.depositDirect(
                    playerName, playerUuid, strictCurrencyId, BigDecimal("-1"),
                    "$DEFAULT_REASON_PREFIX:negative-deposit", DEFAULT_OPERATOR
                )
                val zeroWithdraw = AccountService.withdrawDirect(
                    playerName, playerUuid, strictCurrencyId, BigDecimal.ZERO,
                    "$DEFAULT_REASON_PREFIX:zero-withdraw", DEFAULT_OPERATOR
                )
                val negativeSet = AccountService.setBalanceDirect(
                    playerName, playerUuid, strictCurrencyId, BigDecimal("-5"),
                    "$DEFAULT_REASON_PREFIX:negative-set", DEFAULT_OPERATOR
                )
                if (negativeDeposit.success || zeroWithdraw.success || negativeSet.success) {
                    return@runCase fail("非法金额被意外接受")
                }
                pass("负数与零金额输入已正确拒绝")
            }

            results += runCase(suite, "余额上限保护") {
                val reset = AccountService.setBalanceDirect(
                    playerName, playerUuid, strictCurrencyId, BigDecimal.ZERO,
                    "$DEFAULT_REASON_PREFIX:strict-reset", DEFAULT_OPERATOR
                )
                if (!reset.success) return@runCase fail("重置严格货币余额失败")

                val overLimit = AccountService.depositDirect(
                    playerName, playerUuid, strictCurrencyId, BigDecimal("11"),
                    "$DEFAULT_REASON_PREFIX:strict-over-limit", DEFAULT_OPERATOR
                )
                if (overLimit.success) {
                    return@runCase fail("超过上限的入账被意外放行")
                }
                val balance = AccountService.getBalanceFromDb(playerName, strictCurrencyId)
                if (balance.compareTo(BigDecimal.ZERO) != 0) {
                    return@runCase fail("上限拒绝后余额被改变：$balance")
                }
                pass("默认余额上限保护生效")
            }

            results += runCase(suite, "未知货币应拒绝") {
                val unknown = AccountService.depositDirect(
                    playerName, playerUuid, "does-not-exist-$token", BigDecimal.ONE,
                    "$DEFAULT_REASON_PREFIX:unknown-currency", DEFAULT_OPERATOR
                )
                if (unknown.success) {
                    return@runCase fail("未知货币请求被意外放行")
                }
                pass("未知货币操作已正确拒绝")
            }

            results += runCase(suite, "高并发混合操作稳定性") {
                val init = AccountService.setBalanceDirect(
                    playerName, playerUuid, stressCurrencyId, BigDecimal("500"),
                    "$DEFAULT_REASON_PREFIX:stress-init", DEFAULT_OPERATOR
                )
                if (!init.success) return@runCase fail("初始化压力测试余额失败")

                val stats = executeConcurrent(workerCount, totalOps) { index ->
                    val amount = BigDecimal.valueOf((index % 3 + 1).toLong())
                    if (index % 2 == 0) {
                        AccountService.depositDirect(
                            playerName, playerUuid, stressCurrencyId, amount,
                            "$DEFAULT_REASON_PREFIX:mix-deposit", DEFAULT_OPERATOR
                        )
                    } else {
                        AccountService.withdrawDirect(
                            playerName, playerUuid, stressCurrencyId, amount,
                            "$DEFAULT_REASON_PREFIX:mix-withdraw", DEFAULT_OPERATOR
                        )
                    }
                }
                val finalBalance = AccountService.getBalanceFromDb(playerName, stressCurrencyId)
                if (stats.timedOut) return@runCase fail("并发执行超时")
                if (stats.exceptionCount > 0) {
                    return@runCase fail("压力测试中出现异常次数=${stats.exceptionCount}")
                }
                if (finalBalance < BigDecimal.ZERO) {
                    return@runCase fail("压力测试后出现负余额：$finalBalance")
                }
                pass(
                    "压力测试稳定（线程=$workerCount，操作=$totalOps，成功=${stats.successCount}，失败=${stats.failureCount}，最终余额=$finalBalance）"
                )
            }

            return FunctionalTestSuiteResult(
                suiteName = suite,
                caseResults = results,
                startedAtEpochMs = start,
                finishedAtEpochMs = System.currentTimeMillis()
            )
        } finally {
            cleanupCurrencies(strictCurrencyId, stressCurrencyId)
        }
    }

    /**
     * 执行全部测试集。
     */
    fun runAllSuites(threads: Int = 16, operations: Int = 400): List<FunctionalTestSuiteResult> {
        return listOf(
            runCurrencyCrudSuite(),
            runAccountIsolationSuite(),
            runConcurrencySuite(threads, operations),
            runStabilitySuite(threads, operations)
        )
    }

    /**
     * 通用并发执行器。
     * 通过固定线程池并发执行指定操作，并汇总成功/失败/异常统计。
     */
    private fun executeConcurrent(
        threads: Int,
        operations: Int,
        operation: (Int) -> EconomyResult
    ): ConcurrentExecutionResult {
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)
        val operationIndex = AtomicInteger(0)

        val pool = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threads)

        repeat(threads) {
            pool.execute {
                try {
                    startLatch.await()
                    while (true) {
                        val index = operationIndex.getAndIncrement()
                        if (index >= operations) break
                        try {
                            val result = operation(index)
                            if (result.success) {
                                successCount.incrementAndGet()
                            } else {
                                failureCount.incrementAndGet()
                            }
                        } catch (_: Throwable) {
                            exceptionCount.incrementAndGet()
                        }
                    }
                } finally {
                    finishLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = finishLatch.await(120, TimeUnit.SECONDS)
        pool.shutdownNow()

        return ConcurrentExecutionResult(
            successCount = successCount.get(),
            failureCount = failureCount.get(),
            exceptionCount = exceptionCount.get(),
            timedOut = !finished
        )
    }

    /**
     * 运行单个测试用例并捕获异常，统一转换为结构化结果。
     */
    private fun runCase(
        suite: String,
        caseName: String,
        block: () -> CaseOutcome
    ): FunctionalTestCaseResult {
        val start = System.nanoTime()
        return try {
            val outcome = block()
            FunctionalTestCaseResult(
                suiteName = suite,
                caseName = caseName,
                success = outcome.success,
                reason = outcome.reason,
                elapsedMs = nanosToMillis(start)
            )
        } catch (ex: Throwable) {
            FunctionalTestCaseResult(
                suiteName = suite,
                caseName = caseName,
                success = false,
                reason = "出现未捕获异常：${ex.message ?: "无异常信息"}",
                elapsedMs = nanosToMillis(start)
            )
        }
    }

    private fun pass(reason: String): CaseOutcome = CaseOutcome(true, reason)

    private fun fail(reason: String): CaseOutcome = CaseOutcome(false, reason)

    /**
     * 清理测试中创建的货币，避免污染运行环境。
     */
    private fun cleanupCurrencies(vararg identifiers: String) {
        identifiers
            .filter { it.isNotBlank() }
            .forEach { identifier ->
                runCatching {
                    if (CurrencyService.getByIdentifier(identifier) != null) {
                        CurrencyService.deleteCurrency(identifier)
                    }
                }
            }
    }

    private fun normalizeThreads(value: Int): Int = value.coerceIn(2, 64)

    private fun normalizeOperations(value: Int): Int = value.coerceIn(20, 20_000)

    private fun nanosToMillis(startNano: Long): Long {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano)
    }

    /** 生成短随机后缀，确保每次测试数据命名唯一。 */
    private fun uniqueToken(): String {
        return UUID.randomUUID().toString()
            .replace("-", "")
            .take(8)
            .lowercase()
    }

    /** 基于玩家名生成稳定 UUID，避免测试中依赖 Bukkit 玩家对象。 */
    private fun uuidFromName(playerName: String): String {
        return UUID.nameUUIDFromBytes(playerName.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
