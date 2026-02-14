package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.Test
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.CurrencyRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CurrencyService] 集成测试（仓储层使用内存桩实现）。
 *
 * 测试目标：
 * 1. 覆盖货币生命周期：创建、查询、启用/禁用、删除。
 * 2. 验证重复标识与已删除标识不可重复创建。
 * 3. 验证边界精度（超范围）与主货币切换逻辑。
 */
class CurrencyServiceIntegrationTest {

    /** 以内存 Map 模拟货币表。 */
    private lateinit var store: ConcurrentHashMap<Int, CurrencyEntity>

    /** 自增 ID 模拟器。 */
    private lateinit var idSequence: AtomicInteger

    /**
     * 为每个用例重建隔离环境，并将仓储方法替换为内存桩。
     */
    @BeforeTest
    fun setUp() {
        store = ConcurrentHashMap()
        idSequence = AtomicInteger(1)

        mockkObject(DatabaseManager, CurrencyRepository)
        every { DatabaseManager.ready } returns true

        every { CurrencyRepository.findByIdentifierIncludeDeleted(any()) } answers {
            val identifier = firstArg<String>().lowercase()
            store.values.firstOrNull { it.identifier == identifier }?.copy()
        }
        every { CurrencyRepository.findAllActive() } answers {
            store.values
                .filter { !it.deleted }
                .map { it.copy() }
        }
        every { CurrencyRepository.insert(any()) } answers {
            val entity = firstArg<CurrencyEntity>()
            if (entity.id <= 0) {
                entity.id = idSequence.getAndIncrement()
            }
            store[entity.id] = entity.copy()
            1L
        }
        every { CurrencyRepository.update(any()) } answers {
            val entity = firstArg<CurrencyEntity>()
            if (!store.containsKey(entity.id)) {
                0L
            } else {
                store[entity.id] = entity.copy()
                1L
            }
        }
        every { CurrencyRepository.softDelete(any()) } answers {
            val id = firstArg<Int>()
            val existing = store[id]
            if (existing == null) {
                0L
            } else {
                existing.deleted = true
                store[id] = existing
                1L
            }
        }
        every { CurrencyRepository.clearAllPrimary() } answers {
            store.values.forEach { entity ->
                entity.primary = false
                store[entity.id] = entity
            }
            Unit
        }

        CurrencyService.refreshCache()
    }

    /**
     * 清理 Mock，避免对其他测试产生副作用。
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `货币生命周期应支持完整增删改查`() {
        val created = CurrencyService.createCurrency(
            identifier = "integration_coin",
            name = "集成测试货币",
            precision = 2,
            symbol = "$",
            defaultMaxBalance = 500L,
            consoleLog = false
        )
        assertNotNull(created, "创建货币失败，返回值不应为 null")

        val byIdentifier = CurrencyService.getByIdentifier("integration_coin")
        assertNotNull(byIdentifier, "按标识查询应命中已创建货币")
        assertEquals(created.id, byIdentifier.id, "按标识查询返回的货币 ID 与创建结果不一致")

        val byId = CurrencyService.getById(created.id)
        assertNotNull(byId, "按 ID 查询应命中已创建货币")
        assertEquals("integration_coin", byId.identifier, "按 ID 查询返回的标识不正确")

        assertTrue(CurrencyService.disableCurrency("integration_coin"), "禁用货币应成功")
        assertFalse(CurrencyService.getByIdentifier("integration_coin")!!.enabled, "禁用后 enabled 应为 false")

        assertTrue(CurrencyService.enableCurrency("integration_coin"), "重新启用货币应成功")
        assertTrue(CurrencyService.getByIdentifier("integration_coin")!!.enabled, "启用后 enabled 应为 true")

        assertTrue(CurrencyService.deleteCurrency("integration_coin"), "删除货币应成功")
        assertNull(CurrencyService.getByIdentifier("integration_coin"), "删除后不应再从活动货币中查到该货币")
    }

    @Test
    fun `重复标识与已删除标识都应被拒绝创建`() {
        val first = CurrencyService.createCurrency(
            identifier = "integration_dup",
            name = "重复测试货币一",
            precision = 2,
            symbol = "",
            defaultMaxBalance = -1L,
            consoleLog = false
        )
        assertNotNull(first, "首次创建应成功")
        assertNull(
            CurrencyService.createCurrency(
                identifier = "integration_dup",
                name = "重复测试货币二",
                precision = 2,
                symbol = "",
                defaultMaxBalance = -1L,
                consoleLog = false
            ),
            "重复标识不应允许再次创建"
        )

        assertTrue(CurrencyService.deleteCurrency("integration_dup"), "删除货币应成功")
        assertNull(
            CurrencyService.createCurrency(
                identifier = "integration_dup",
                name = "重复测试货币三",
                precision = 2,
                symbol = "",
                defaultMaxBalance = -1L,
                consoleLog = false
            ),
            "已删除的历史标识仍不应允许复用"
        )
    }

    @Test
    fun `精度边界与主货币切换应符合预期`() {
        val a = CurrencyService.createCurrency(
            identifier = "integration_primary_a",
            name = "主货币A",
            precision = 2,
            symbol = "",
            defaultMaxBalance = -1L,
            consoleLog = false
        )
        val b = CurrencyService.createCurrency(
            identifier = "integration_primary_b",
            name = "主货币B",
            precision = 99,
            symbol = "",
            defaultMaxBalance = -1L,
            consoleLog = false
        )

        assertNotNull(a, "货币 A 创建失败")
        assertNotNull(b, "货币 B 创建失败")
        assertEquals(8, b.precision, "超范围精度应被限制为 8")

        assertTrue(CurrencyService.setPrimary("integration_primary_a"), "设置 A 为主货币应成功")
        assertEquals("integration_primary_a", CurrencyService.getPrimary()?.identifier, "当前主货币应为 A")

        assertTrue(CurrencyService.setPrimary("integration_primary_b"), "设置 B 为主货币应成功")
        assertEquals("integration_primary_b", CurrencyService.getPrimary()?.identifier, "当前主货币应切换为 B")

        val primaryCount = CurrencyService.getAllCurrencies().count { it.primary }
        assertEquals(1, primaryCount, "任意时刻应只有一个主货币")
    }

    /**
     * 复制实体，避免测试中引用同一实例导致状态串改。
     */
    private fun CurrencyEntity.copy(): CurrencyEntity {
        return CurrencyEntity().also { target ->
            target.id = this.id
            target.identifier = this.identifier
            target.name = this.name
            target.symbol = this.symbol
            target.precision = this.precision
            target.defaultMaxBalance = this.defaultMaxBalance
            target.primary = this.primary
            target.enabled = this.enabled
            target.deleted = this.deleted
            target.consoleLog = this.consoleLog
            target.createdAt = this.createdAt
            target.updatedAt = this.updatedAt
        }
    }
}
