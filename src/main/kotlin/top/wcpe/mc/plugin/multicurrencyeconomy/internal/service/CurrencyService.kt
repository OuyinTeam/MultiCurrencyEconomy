package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.CurrencyInfo
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.CurrencyRepository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * 货币服务 — 管理货币的创建、删除、启用/禁用、主货币切换等核心业务逻辑。
 *
 * 【缓存机制】
 *   内存中维护 [currencyCache]（identifier → CurrencyEntity），减少数据库查询。
 *   缓存在 [refreshCache] 中统一刷新，由初始化流程和管理操作触发。
 *
 * 【线程语义】
 *   - 缓存使用 ConcurrentHashMap，读取线程安全。
 *   - 写操作（create/delete/enable/disable/setPrimary）应在异步线程中调用。
 *
 * 【默认货币】
 *   首次启动时，如果数据库中无货币记录，根据 config.yml 配置自动创建默认主货币。
 *
 * 【ID 类型】货币 ID 为 INT 自增主键。
 */
object CurrencyService {

    /**
     * 货币缓存 — key 为 identifier（小写），value 为实体。
     * 仅包含未删除的货币。
     */
    private val currencyCache = ConcurrentHashMap<String, CurrencyEntity>()

    /**
     * 货币 ID → 实体的映射缓存。
     * key 为 Int 类型自增 ID。
     */
    private val currencyIdCache = ConcurrentHashMap<Int, CurrencyEntity>()

    // ======================== 初始化 ========================

    /**
     * 初始化货币服务。
     * 刷新缓存，如无货币则创建默认主货币。
     * 【调用时机】数据库初始化完成后在异步线程中调用。
     */
    fun initialize() {
        refreshCache()
        ensureDefaultCurrency()
        info("[MCE] 货币服务初始化完成，已加载 ${currencyCache.size} 种货币。")
    }

    // ======================== 查询方法 ========================

    /**
     * 根据标识符获取货币信息（不含已删除）。
     * 优先从缓存读取。
     *
     * @param identifier 货币标识符（英文小写）
     * @return 货币实体，不存在返回 null
     */
    fun getByIdentifier(identifier: String): CurrencyEntity? {
        return currencyCache[identifier.lowercase()]
    }

    /**
     * 根据货币 ID 获取货币信息（不含已删除）。
     *
     * @param id 货币 INT 自增 ID
     * @return 货币实体，不存在返回 null
     */
    fun getById(id: Int): CurrencyEntity? {
        return currencyIdCache[id]
    }

    /**
     * 获取主货币。
     *
     * @return 主货币实体，不存在返回 null
     */
    fun getPrimary(): CurrencyEntity? {
        return currencyCache.values.find { it.primary }
    }

    /**
     * 获取所有已启用且未删除的货币列表。
     *
     * @return 启用的货币实体列表
     */
    fun getActiveCurrencies(): List<CurrencyEntity> {
        return currencyCache.values.filter { it.enabled }.toList()
    }

    /**
     * 获取所有未删除的货币列表（含禁用的）。
     *
     * @return 所有未删除的货币实体列表
     */
    fun getAllCurrencies(): List<CurrencyEntity> {
        return currencyCache.values.toList()
    }

    /**
     * 获取所有已启用货币的标识符列表（用于 Tab 补全）。
     *
     * @return 启用的货币标识符列表
     */
    fun getActiveCurrencyIdentifiers(): List<String> {
        return currencyCache.values.filter { it.enabled }.map { it.identifier }
    }

    /**
     * 将实体转换为 API 层的 CurrencyInfo DTO。
     *
     * @param entity 货币实体
     * @return CurrencyInfo DTO
     */
    fun toInfo(entity: CurrencyEntity): CurrencyInfo {
        return CurrencyInfo(
            id = entity.id,
            identifier = entity.identifier,
            displayName = entity.name,
            symbol = entity.symbol,
            precision = entity.precision,
            defaultMaxBalance = entity.defaultMaxBalance,
            primary = entity.primary,
            enabled = entity.enabled
        )
    }

    // ======================== 管理方法 ========================

    /**
     * 创建新货币。
     * 使用自增主键，无需手动生成 UUID。
     *
     * @param identifier      货币标识符（英文小写）
     * @param name            显示名称
     * @param precision       精度（小数位数，0-8）
     * @param symbol          货币符号
     * @param defaultMaxBalance 默认余额上限（-1 = 不限）
     * @param consoleLog      是否在控制台输出该货币的余额变更日志
     * @return 创建结果：成功返回新货币实体，失败返回 null（标识符已存在）
     */
    fun createCurrency(
        identifier: String,
        name: String,
        precision: Int,
        symbol: String = "",
        defaultMaxBalance: Long = -1L,
        consoleLog: Boolean = true
    ): CurrencyEntity? {
        val id = identifier.lowercase()

        // 检查标识符是否已被使用（含已删除的）
        if (CurrencyRepository.findByIdentifierIncludeDeleted(id) != null) {
            warning("[MCE] 货币标识符 '$id' 已存在（可能已被删除），无法创建。")
            return null
        }

        val entity = CurrencyEntity().apply {
            this.identifier = id
            this.name = name
            this.symbol = symbol
            this.precision = precision.coerceIn(0, 8)
            this.defaultMaxBalance = defaultMaxBalance
            this.primary = false
            this.enabled = true
            this.deleted = false
            this.consoleLog = consoleLog
            this.createdAt = LocalDateTime.now()
            this.updatedAt = LocalDateTime.now()
        }

        CurrencyRepository.insert(entity)
        refreshCache()
        info("[MCE] 货币 '$id' ($name) 创建成功。")
        return entity
    }

    /**
     * 逻辑删除货币。
     * 主货币不允许删除，需先切换主货币。
     *
     * @param identifier 货币标识符
     * @return true = 删除成功
     */
    fun deleteCurrency(identifier: String): Boolean {
        val entity = getByIdentifier(identifier)
        if (entity == null) {
            warning("[MCE] 找不到货币: $identifier")
            return false
        }
        if (entity.primary) {
            warning("[MCE] 无法删除主货币 '$identifier'，请先切换主货币。")
            return false
        }
        CurrencyRepository.softDelete(entity.id)
        refreshCache()
        info("[MCE] 货币 '$identifier' 已逻辑删除。")
        return true
    }

    /**
     * 启用货币。
     *
     * @param identifier 货币标识符
     * @return true = 启用成功
     */
    fun enableCurrency(identifier: String): Boolean {
        val entity = getByIdentifier(identifier) ?: return false
        if (entity.enabled) return true
        entity.enabled = true
        CurrencyRepository.update(entity)
        refreshCache()
        return true
    }

    /**
     * 禁用货币。
     *
     * @param identifier 货币标识符
     * @return true = 禁用成功
     */
    fun disableCurrency(identifier: String): Boolean {
        val entity = getByIdentifier(identifier) ?: return false
        if (!entity.enabled) return true
        entity.enabled = false
        CurrencyRepository.update(entity)
        refreshCache()
        return true
    }

    /**
     * 设置主货币。
     * 先清除所有主货币标记，再将指定货币设为主货币。
     *
     * @param identifier 货币标识符
     * @return true = 设置成功
     */
    fun setPrimary(identifier: String): Boolean {
        val entity = getByIdentifier(identifier) ?: return false
        CurrencyRepository.clearAllPrimary()
        entity.primary = true
        CurrencyRepository.update(entity)
        refreshCache()
        info("[MCE] 已将 '$identifier' 设为主货币。")
        return true
    }

    // ======================== 缓存管理 ========================

    /**
     * 刷新货币缓存 — 从数据库重新加载所有未删除货币到内存。
     */
    fun refreshCache() {
        if (!DatabaseManager.ready) return
        val all = CurrencyRepository.findAllActive()
        currencyCache.clear()
        currencyIdCache.clear()
        all.forEach {
            currencyCache[it.identifier] = it
            currencyIdCache[it.id] = it
        }
    }

    /**
     * 确保默认主货币存在。
     * 首次启动时如果数据库中无货币记录，根据 config.yml 创建默认主货币。
     */
    private fun ensureDefaultCurrency() {
        if (currencyCache.isNotEmpty()) return
        info("[MCE] 数据库中无货币记录，正在创建默认主货币...")
        val entity = CurrencyEntity().apply {
            this.identifier = MainConfig.defaultCurrencyIdentifier
            this.name = MainConfig.defaultCurrencyName
            this.symbol = MainConfig.defaultCurrencySymbol
            this.precision = MainConfig.defaultCurrencyPrecision
            this.defaultMaxBalance = MainConfig.defaultCurrencyMaxBalance
            this.primary = true
            this.enabled = true
            this.deleted = false
            this.consoleLog = MainConfig.defaultCurrencyConsoleLog
            this.createdAt = LocalDateTime.now()
            this.updatedAt = LocalDateTime.now()
        }
        CurrencyRepository.insert(entity)
        refreshCache()
        info("[MCE] 默认主货币 '${entity.identifier}' (${entity.name}) 创建完成。")
    }
}
