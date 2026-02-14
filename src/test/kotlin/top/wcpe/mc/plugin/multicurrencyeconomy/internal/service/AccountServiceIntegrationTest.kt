package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import org.bukkit.Bukkit
import org.junit.Test
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.EconomyResult
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.AccountEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.AccountRepository
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [AccountService] 集成测试（依赖内存桩仓储）。
 *
 * 覆盖范围：
 * 1. 玩家在不同货币下的账户隔离性。
 * 2. 乐观锁并发更新下的余额不变量（不超发、不负值）。
 * 3. 非法输入、未知货币、上限校验等异常路径的稳定性。
 */
class AccountServiceIntegrationTest {

    /** 模拟账户表中的单条记录状态。 */
    private data class AccountState(
        var playerUuid: String,
        var balance: BigDecimal,
        var version: Long
    )

    /** 并发执行统计结果。 */
    private data class ConcurrentStats(
        val successCount: Int,
        val failureCount: Int,
        val exceptionCount: Int
    )

    private val accountStore = ConcurrentHashMap<Pair<String, Int>, AccountState>()
    private val currencyStore = ConcurrentHashMap<String, CurrencyEntity>()
    private val currencyById = ConcurrentHashMap<Int, CurrencyEntity>()

    /**
     * 初始化测试桩：
     * - 货币查询改为内存 Map。
     * - 账户读写改为内存 CAS（基于 version）。
     * - 审计日志写入置空实现，避免无关依赖干扰。
     */
    @BeforeTest
    fun setUp() {
        mockkObject(DatabaseManager, CurrencyService, AccountRepository, AuditService)
        mockkStatic(Bukkit::class)

        every { DatabaseManager.ready } returns true
        every { Bukkit.getPlayerExact(any()) } returns null

        every { CurrencyService.getByIdentifier(any()) } answers {
            val identifier = firstArg<String>().lowercase()
            currencyStore[identifier]
        }

        every { AccountRepository.getOrCreate(any(), any(), any()) } answers {
            val playerName = firstArg<String>()
            val playerUuid = secondArg<String>()
            val currencyId = thirdArg<Int>()
            val key = playerName to currencyId
            val state = accountStore.computeIfAbsent(key) {
                AccountState(playerUuid = playerUuid, balance = BigDecimal.ZERO, version = 1L)
            }
            synchronized(state) {
                if (playerUuid.isNotEmpty()) {
                    state.playerUuid = playerUuid
                }
                state.toEntity(playerName, currencyId)
            }
        }

        every { AccountRepository.update(any()) } answers {
            val entity = firstArg<AccountEntity>()
            val key = entity.playerName to entity.currencyId
            val state = accountStore[key] ?: return@answers 0L
            synchronized(state) {
                if (entity.version != state.version) {
                    return@answers 0L
                }
                state.balance = entity.balance
                state.playerUuid = entity.playerUuid
                state.version += 1L
                return@answers 1L
            }
        }

        every { AccountRepository.findByPlayerAndCurrency(any(), any()) } answers {
            val playerName = firstArg<String>()
            val currencyId = secondArg<Int>()
            val state = accountStore[playerName to currencyId] ?: return@answers null
            synchronized(state) {
                state.toEntity(playerName, currencyId)
            }
        }

        every {
            AuditService.writeLog(
                any(), any(), any(), any<ChangeType>(),
                any(), any(), any(), any(), any()
            )
        } just runs
    }

    /**
     * 用例后清理缓存和 Mock，保证测试相互隔离。
     */
    @AfterTest
    fun tearDown() {
        AccountService.clearCache()
        unmockkAll()
        accountStore.clear()
        currencyStore.clear()
        currencyById.clear()
    }

    @Test
    fun `不同货币账户应相互独立且余额互不影响`() {
        val currencyA = registerCurrency(id = 1, identifier = "acct_a", precision = 2, defaultMaxBalance = -1L)
        val currencyB = registerCurrency(id = 2, identifier = "acct_b", precision = 2, defaultMaxBalance = -1L)
        assertNotNull(currencyA, "货币 A 注册失败")
        assertNotNull(currencyB, "货币 B 注册失败")

        val playerName = "integration_player"
        val playerUuid = "integration-player-uuid"

        assertTrue(
            AccountService.setBalanceDirect(
                playerName, playerUuid, "acct_a", BigDecimal("100"),
                "集成测试:初始化货币A", "测试执行器"
            ).success,
            "初始化货币 A 余额失败"
        )
        assertTrue(
            AccountService.setBalanceDirect(
                playerName, playerUuid, "acct_b", BigDecimal("50"),
                "集成测试:初始化货币B", "测试执行器"
            ).success,
            "初始化货币 B 余额失败"
        )

        assertTrue(
            AccountService.withdrawDirect(
                playerName, playerUuid, "acct_a", BigDecimal("40"),
                "集成测试:货币A扣款", "测试执行器"
            ).success,
            "从货币 A 扣款失败"
        )

        val balanceA = AccountService.getBalanceFromDb(playerName, "acct_a")
        val balanceB = AccountService.getBalanceFromDb(playerName, "acct_b")
        assertEquals(0, balanceA.compareTo(BigDecimal("60.00")), "货币 A 余额应为 60.00")
        assertEquals(0, balanceB.compareTo(BigDecimal("50.00")), "货币 B 余额应保持 50.00")

        val insufficient = AccountService.withdrawDirect(
            playerName, playerUuid, "acct_b", BigDecimal("1000"),
            "集成测试:余额不足", "测试执行器"
        )
        assertFalse(insufficient.success, "余额不足时应返回失败")
        assertEquals(
            0,
            AccountService.getBalanceFromDb(playerName, "acct_b").compareTo(BigDecimal("50.00")),
            "余额不足失败后，货币 B 余额不应被改动"
        )
    }

    @Test
    fun `乐观锁并发操作应保持余额不变量`() {
        registerCurrency(id = 3, identifier = "concur", precision = 2, defaultMaxBalance = -1L)

        val playerName = "concurrency_player"
        val playerUuid = "concurrency-uuid"

        assertTrue(
            AccountService.setBalanceDirect(
                playerName, playerUuid, "concur", BigDecimal.ZERO,
                "集成测试:并发入账前重置", "测试执行器"
            ).success,
            "并发入账前重置余额失败"
        )

        val depositStats = executeConcurrent(threads = 8, operations = 200) {
            AccountService.depositDirect(
                playerName, playerUuid, "concur", BigDecimal.ONE,
                "集成测试:并发入账", "测试执行器"
            )
        }
        assertEquals(0, depositStats.exceptionCount, "并发入账过程不应出现异常")

        val balanceAfterDeposit = AccountService.getBalanceFromDb(playerName, "concur")
        assertEquals(
            0,
            balanceAfterDeposit.compareTo(BigDecimal.valueOf(depositStats.successCount.toLong())),
            "并发入账后余额应等于成功次数"
        )

        val initialWithdrawBalance = BigDecimal("200")
        assertTrue(
            AccountService.setBalanceDirect(
                playerName, playerUuid, "concur", initialWithdrawBalance,
                "集成测试:并发扣款前重置", "测试执行器"
            ).success,
            "并发扣款前重置余额失败"
        )

        val withdrawStats = executeConcurrent(threads = 8, operations = 200) {
            AccountService.withdrawDirect(
                playerName, playerUuid, "concur", BigDecimal.ONE,
                "集成测试:并发扣款", "测试执行器"
            )
        }
        assertEquals(0, withdrawStats.exceptionCount, "并发扣款过程不应出现异常")

        val finalBalance = AccountService.getBalanceFromDb(playerName, "concur")
        assertTrue(finalBalance >= BigDecimal.ZERO, "并发扣款后余额不应为负数")
        assertTrue(
            withdrawStats.successCount <= initialWithdrawBalance.toInt(),
            "成功扣款次数不应超过初始余额"
        )
        assertEquals(
            0,
            finalBalance
                .add(BigDecimal.valueOf(withdrawStats.successCount.toLong()))
                .compareTo(initialWithdrawBalance),
            "应满足不变量：最终余额 + 成功扣款次数 = 初始余额"
        )
    }

    @Test
    fun `非法输入与异常场景应被安全拒绝`() {
        registerCurrency(id = 4, identifier = "safe", precision = 2, defaultMaxBalance = 10L)

        val playerName = "safe_player"
        val playerUuid = "safe-uuid"

        assertFalse(
            AccountService.depositDirect(
                playerName, playerUuid, "safe", BigDecimal("-1"),
                "集成测试:负数入账", "测试执行器"
            ).success,
            "负数入账应失败"
        )
        assertFalse(
            AccountService.withdrawDirect(
                playerName, playerUuid, "safe", BigDecimal.ZERO,
                "集成测试:零金额扣款", "测试执行器"
            ).success,
            "零金额扣款应失败"
        )
        assertFalse(
            AccountService.setBalanceDirect(
                playerName, playerUuid, "safe", BigDecimal("-5"),
                "集成测试:设置负余额", "测试执行器"
            ).success,
            "设置负余额应失败"
        )
        assertFalse(
            AccountService.depositDirect(
                playerName, playerUuid, "safe", BigDecimal("11"),
                "集成测试:超过上限", "测试执行器"
            ).success,
            "超过货币上限的入账应失败"
        )
        assertFalse(
            AccountService.depositDirect(
                playerName, playerUuid, "missing_currency", BigDecimal.ONE,
                "集成测试:未知货币", "测试执行器"
            ).success,
            "未知货币入账应失败"
        )
    }

    /**
     * 向内存货币表注册测试货币。
     */
    private fun registerCurrency(
        id: Int,
        identifier: String,
        precision: Int,
        defaultMaxBalance: Long
    ): CurrencyEntity {
        val entity = CurrencyEntity().apply {
            this.id = id
            this.identifier = identifier
            this.name = "CURRENCY_$identifier"
            this.symbol = "$"
            this.precision = precision
            this.defaultMaxBalance = defaultMaxBalance
            this.primary = false
            this.enabled = true
            this.deleted = false
            this.consoleLog = false
        }
        currencyStore[identifier.lowercase()] = entity
        currencyById[id] = entity
        return entity
    }

    /**
     * 并发执行工具：
     * - 使用固定线程池并发提交操作。
     * - 统计成功/失败/异常次数，供不变量断言使用。
     */
    private fun executeConcurrent(
        threads: Int,
        operations: Int,
        operation: () -> EconomyResult
    ): ConcurrentStats {
        val pool = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(threads)
        val index = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val exceptionCount = AtomicInteger(0)

        repeat(threads) {
            pool.execute {
                try {
                    startLatch.await()
                    while (true) {
                        val current = index.getAndIncrement()
                        if (current >= operations) break
                        try {
                            val result = operation()
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
        finishLatch.await(60, TimeUnit.SECONDS)
        pool.shutdownNow()

        return ConcurrentStats(
            successCount = successCount.get(),
            failureCount = failureCount.get(),
            exceptionCount = exceptionCount.get()
        )
    }

    /**
     * 将内存状态转换为仓储层实体对象。
     */
    private fun AccountState.toEntity(playerName: String, currencyId: Int): AccountEntity {
        return AccountEntity().also { entity ->
            entity.playerName = playerName
            entity.playerUuid = this.playerUuid
            entity.currencyId = currencyId
            entity.balance = this.balance
            entity.version = this.version
        }
    }
}
