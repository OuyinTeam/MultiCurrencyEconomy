package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import org.bukkit.Bukkit
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.multicurrencyeconomy.api.event.BalanceChangeEvent
import top.wcpe.mc.plugin.multicurrencyeconomy.api.event.BalanceChangePostEvent
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.AccountSnapshot
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.EconomyResult
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.async.AsyncExecutor
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.AccountRepository
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 账户服务 — 管理玩家余额的核心业务逻辑（存取款、余额查询、缓存维护）。
 *
 * 【缓存架构】
 *   采用 Write-Behind Cache 模式：
 *   - 读操作：直接从 [balanceCache] 读取，零 I/O。
 *   - 写操作：先校验 → 更新缓存 → 异步写入数据库 + 审计日志。
 *   - 缓存 key = "${playerName}:${currencyId}" — 以 playerName 为主要键。
 *
 * 【事件机制】
 *   每次余额变更前触发 [BalanceChangeEvent]（可取消），变更后触发 [BalanceChangePostEvent]。
 *   事件已迁移到 API 模块（api.event 包），方便第三方插件监听。
 *   事件在异步线程中触发（async = true），监听器需注意线程安全。
 *
 * 【控制台日志】
 *   每种货币可独立配置 consoleLog 开关，余额变更时按配置决定是否输出日志。
 *
 * 【EconomyResult 返回值】
 *   所有余额变更操作返回 [EconomyResult]，包含成功状态、新余额和消息。
 *
 * 【Vault 适配】
 *   Vault Economy 接口为同步，通过本服务的缓存层实现无阻塞响应。
 *
 * 【线程语义】
 *   - [getBalance]、[has] 可在任意线程调用（缓存读取）。
 *   - [deposit]、[withdraw]、[setBalance] 的缓存更新是原子的（ConcurrentHashMap.compute）。
 *   - 数据库写入通过 AsyncExecutor 在异步线程执行。
 */
object AccountService {

    /**
     * 余额缓存。
     * key = "${playerName}:${currencyId}"
     * value = 当前余额（已按货币精度规范化）
     */
    private val balanceCache = ConcurrentHashMap<String, BigDecimal>()

    /** 生成缓存 key — 以 playerName 和货币 ID 组合 */
    private fun cacheKey(playerName: String, currencyId: Int) = "$playerName:$currencyId"

    // ======================== 初始化 ========================

    /**
     * 初始化账户服务。
     * 【调用时机】数据库初始化完成后在异步线程中调用。
     */
    fun initialize() {
        // 加载在线玩家的余额到缓存（热重载场景）
        Bukkit.getOnlinePlayers().forEach { player ->
            loadPlayerBalances(player.name, player.uniqueId.toString())
        }
    }

    // ======================== 缓存管理 ========================

    /**
     * 加载指定玩家在所有已启用货币下的余额到缓存。
     * 以 playerName 作为主要标识进行查询和缓存。
     * 【调用时机】玩家加入服务器时 / 初始化时。
     * 【线程要求】在异步线程中调用。
     *
     * @param playerName 玩家名称（主要标识）
     * @param playerUuid 玩家 UUID（记录字段）
     */
    fun loadPlayerBalances(playerName: String, playerUuid: String) {
        if (!DatabaseManager.ready) return
        val currencies = CurrencyService.getActiveCurrencies()
        currencies.forEach { currency ->
            val account = AccountRepository.getOrCreate(playerName, playerUuid, currency.id)
            val scaled = CurrencyPrecisionUtil.scale(account.balance, currency.precision)
            balanceCache[cacheKey(playerName, currency.id)] = scaled
        }
    }

    /**
     * 清除指定玩家的缓存。
     * 【调用时机】玩家退出服务器时（可选）。
     *
     * @param playerName 玩家名称
     */
    fun unloadPlayer(playerName: String) {
        balanceCache.keys.removeIf { it.startsWith("$playerName:") }
    }

    /**
     * 清除所有缓存。
     */
    fun clearCache() {
        balanceCache.clear()
    }

    // ======================== 查询方法 ========================

    /**
     * 获取余额（从缓存读取）。
     * 如果缓存中没有，返回 ZERO。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @return 当前余额
     */
    fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal {
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return BigDecimal.ZERO
        val key = cacheKey(playerName, currency.id)
        return balanceCache[key] ?: BigDecimal.ZERO
    }

    /**
     * 通过货币 ID 获取余额。
     *
     * @param playerName 玩家名称
     * @param currencyId 货币 INT ID
     * @return 当前余额
     */
    fun getBalanceByCurrencyId(playerName: String, currencyId: Int): BigDecimal {
        return balanceCache[cacheKey(playerName, currencyId)] ?: BigDecimal.ZERO
    }

    /**
     * 检查余额是否足够。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @param amount             需要的金额
     * @return true = 余额充足
     */
    fun has(playerName: String, currencyIdentifier: String, amount: BigDecimal): Boolean {
        return getBalance(playerName, currencyIdentifier) >= amount
    }

    /**
     * 获取玩家在所有已启用货币下的账户快照列表。
     *
     * @param playerName 玩家名称
     * @return 账户快照列表
     */
    fun getPlayerAccounts(playerName: String): List<AccountSnapshot> {
        val currencies = CurrencyService.getActiveCurrencies()
        // 尝试获取 UUID
        val player = Bukkit.getOfflinePlayer(playerName)
        val uuid = player.uniqueId.toString()

        return currencies.map { currency ->
            val balance = getBalanceByCurrencyId(playerName, currency.id)
            AccountSnapshot(
                playerName = playerName,
                playerUuid = uuid,
                currencyIdentifier = currency.identifier,
                currencyDisplayName = currency.name,
                currencySymbol = currency.symbol,
                currencyPrecision = currency.precision,
                balance = balance,
                formattedBalance = CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)
            )
        }
    }

    // ======================== 写操作 ========================

    /**
     * 存款（增加余额）。
     *
     * @param playerName         玩家名称（主要标识）
     * @param playerUuid         玩家 UUID（记录字段）
     * @param currencyIdentifier 货币标识符
     * @param amount             存款金额（正数）
     * @param reason             变更原因（必填）
     * @param operator           操作者标识
     * @return EconomyResult 操作结果
     */
    fun deposit(
        playerName: String,
        playerUuid: String,
        currencyIdentifier: String,
        amount: BigDecimal,
        reason: String,
        operator: String
    ): EconomyResult {
        if (!DatabaseManager.ready) return EconomyResult.failure(BigDecimal.ZERO, "服务未就绪")
        if (!CurrencyPrecisionUtil.isPositive(amount)) return EconomyResult.failure(BigDecimal.ZERO, "金额必须为正数")

        val currency = CurrencyService.getByIdentifier(currencyIdentifier)
            ?: return EconomyResult.failure(BigDecimal.ZERO, "找不到货币: $currencyIdentifier")
        if (!currency.enabled) return EconomyResult.failure(BigDecimal.ZERO, "货币已禁用: $currencyIdentifier")

        val scaledAmount = CurrencyPrecisionUtil.scale(amount, currency.precision)
        val key = cacheKey(playerName, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)
        val newBalance = CurrencyPrecisionUtil.scale(currentBalance.add(scaledAmount), currency.precision)

        // 检查上限
        if (!checkMaxBalance(currency, newBalance)) {
            return EconomyResult.failure(currentBalance, "操作将超过余额上限")
        }

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerName, playerUuid, currency.identifier,
            ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return EconomyResult.failure(currentBalance, "操作被事件取消")

        // 原子更新缓存
        balanceCache[key] = newBalance

        // 控制台日志
        logIfEnabled(currency, "存款", playerName, scaledAmount, currentBalance, newBalance, reason, operator)

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerName, playerUuid, currency.id)
                account.balance = newBalance
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerName, playerUuid, currency.id,
                    ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
                )
                // 触发变更后事件
                val postEvent = BalanceChangePostEvent(
                    playerName, playerUuid, currency.identifier,
                    ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                // 数据库写入失败 — 回滚缓存
                warning("[MCE] 存款数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return EconomyResult.success(newBalance)
    }

    /**
     * 取款（减少余额）。
     *
     * @param playerName         玩家名称（主要标识）
     * @param playerUuid         玩家 UUID（记录字段）
     * @param currencyIdentifier 货币标识符
     * @param amount             扣除金额（正数）
     * @param reason             变更原因（必填）
     * @param operator           操作者标识
     * @return EconomyResult 操作结果
     */
    fun withdraw(
        playerName: String,
        playerUuid: String,
        currencyIdentifier: String,
        amount: BigDecimal,
        reason: String,
        operator: String
    ): EconomyResult {
        if (!DatabaseManager.ready) return EconomyResult.failure(BigDecimal.ZERO, "服务未就绪")
        if (!CurrencyPrecisionUtil.isPositive(amount)) return EconomyResult.failure(BigDecimal.ZERO, "金额必须为正数")

        val currency = CurrencyService.getByIdentifier(currencyIdentifier)
            ?: return EconomyResult.failure(BigDecimal.ZERO, "找不到货币: $currencyIdentifier")
        if (!currency.enabled) return EconomyResult.failure(BigDecimal.ZERO, "货币已禁用: $currencyIdentifier")

        val scaledAmount = CurrencyPrecisionUtil.scale(amount, currency.precision)
        val key = cacheKey(playerName, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)

        // 余额不足检查
        if (currentBalance < scaledAmount) {
            return EconomyResult.failure(currentBalance, "余额不足")
        }

        val newBalance = CurrencyPrecisionUtil.scale(currentBalance.subtract(scaledAmount), currency.precision)

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerName, playerUuid, currency.identifier,
            ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return EconomyResult.failure(currentBalance, "操作被事件取消")

        // 原子更新缓存
        balanceCache[key] = newBalance

        // 控制台日志
        logIfEnabled(currency, "取款", playerName, scaledAmount, currentBalance, newBalance, reason, operator)

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerName, playerUuid, currency.id)
                account.balance = newBalance
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerName, playerUuid, currency.id,
                    ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
                )
                val postEvent = BalanceChangePostEvent(
                    playerName, playerUuid, currency.identifier,
                    ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                warning("[MCE] 取款数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return EconomyResult.success(newBalance)
    }

    /**
     * 设置余额（直接设定为指定值）。
     *
     * @param playerName         玩家名称（主要标识）
     * @param playerUuid         玩家 UUID（记录字段）
     * @param currencyIdentifier 货币标识符
     * @param newAmount          目标余额（必须非负）
     * @param reason             变更原因（必填）
     * @param operator           操作者标识
     * @return EconomyResult 操作结果
     */
    fun setBalance(
        playerName: String,
        playerUuid: String,
        currencyIdentifier: String,
        newAmount: BigDecimal,
        reason: String,
        operator: String
    ): EconomyResult {
        if (!DatabaseManager.ready) return EconomyResult.failure(BigDecimal.ZERO, "服务未就绪")
        if (!CurrencyPrecisionUtil.isNonNegative(newAmount)) return EconomyResult.failure(BigDecimal.ZERO, "金额必须非负")

        val currency = CurrencyService.getByIdentifier(currencyIdentifier)
            ?: return EconomyResult.failure(BigDecimal.ZERO, "找不到货币: $currencyIdentifier")

        val scaledNew = CurrencyPrecisionUtil.scale(newAmount, currency.precision)
        val key = cacheKey(playerName, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)
        val delta = scaledNew.subtract(currentBalance).abs()

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerName, playerUuid, currency.identifier,
            ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return EconomyResult.failure(currentBalance, "操作被事件取消")

        // 原子更新缓存
        balanceCache[key] = scaledNew

        // 控制台日志
        logIfEnabled(currency, "设置余额", playerName, delta, currentBalance, scaledNew, reason, operator)

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerName, playerUuid, currency.id)
                account.balance = scaledNew
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerName, playerUuid, currency.id,
                    ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
                )
                val postEvent = BalanceChangePostEvent(
                    playerName, playerUuid, currency.identifier,
                    ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                warning("[MCE] 设置余额数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return EconomyResult.success(scaledNew)
    }

    /**
     * 设置玩家在指定货币下的个人余额上限。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @param maxBalance         余额上限（-1 = 使用默认值）
     * @return true = 设置成功
     */
    fun setMaxBalance(playerName: String, currencyIdentifier: String, maxBalance: Long): Boolean {
        if (!DatabaseManager.ready) return false
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return false
        return AccountRepository.setMaxBalance(playerName, currency.id, maxBalance)
    }

    // ======================== 内部方法 ========================

    /**
     * 检查新余额是否超过上限。
     *
     * @param currency   货币实体
     * @param newBalance 新余额
     * @return true = 未超过上限（可以继续操作）
     */
    private fun checkMaxBalance(currency: CurrencyEntity, newBalance: BigDecimal): Boolean {
        val maxBalance = currency.defaultMaxBalance
        if (maxBalance <= 0) return true  // -1 = 不限
        return newBalance <= BigDecimal.valueOf(maxBalance)
    }

    /**
     * 控制台日志输出 — 根据货币的 consoleLog 配置决定是否输出。
     *
     * @param currency       货币实体
     * @param action         操作类型描述
     * @param playerName     玩家名称
     * @param amount         变更金额
     * @param balanceBefore  变更前余额
     * @param balanceAfter   变更后余额
     * @param reason         变更原因
     * @param operator       操作者标识
     */
    private fun logIfEnabled(
        currency: CurrencyEntity,
        action: String,
        playerName: String,
        amount: BigDecimal,
        balanceBefore: BigDecimal,
        balanceAfter: BigDecimal,
        reason: String,
        operator: String
    ) {
        if (!currency.consoleLog) return
        info("[MCE] [$action] 玩家=$playerName 货币=${currency.identifier} 金额=$amount 余额=$balanceBefore→$balanceAfter 原因=$reason 操作者=$operator")
    }
}
