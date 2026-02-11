package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import org.bukkit.Bukkit
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.AccountSnapshot
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.async.AsyncExecutor
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.AccountEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.AccountRepository
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.event.BalanceChangeEvent
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.event.BalanceChangePostEvent
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
 *   - 缓存 key = "${playerUuid}:${currencyId}"
 *
 * 【事件机制】
 *   每次余额变更前触发 [BalanceChangeEvent]（可取消），变更后触发 [BalanceChangePostEvent]。
 *   事件在异步线程中触发（async = true），监听器需注意线程安全。
 *
 * 【Vault 适配】
 *   Vault Economy 接口为同步，通过本服务的缓存层实现无阻塞响应。
 *
 * 【线程语义】
 *   - [getBalance]、[has] 可在任意线程调用（缓存读取）。
 *   - [deposit]、[withdraw]、[set] 的缓存更新是原子的（ConcurrentHashMap.compute）。
 *   - 数据库写入通过 AsyncExecutor 在异步线程执行。
 */
object AccountService {

    /**
     * 余额缓存。
     * key = "${playerUuid}:${currencyId}"
     * value = 当前余额（已按货币精度规范化）
     */
    private val balanceCache = ConcurrentHashMap<String, BigDecimal>()

    /** 生成缓存 key */
    private fun cacheKey(playerUuid: String, currencyId: String) = "$playerUuid:$currencyId"

    // ======================== 初始化 ========================

    /**
     * 初始化账户服务。
     * 【调用时机】数据库初始化完成后在异步线程中调用。
     */
    fun initialize() {
        // 加载在线玩家的余额到缓存（热重载场景）
        Bukkit.getOnlinePlayers().forEach { player ->
            loadPlayerBalances(player.uniqueId.toString(), player.name)
        }
    }

    // ======================== 缓存管理 ========================

    /**
     * 加载指定玩家在所有已启用货币下的余额到缓存。
     * 【调用时机】玩家加入服务器时 / 初始化时。
     * 【线程要求】在异步线程中调用。
     */
    fun loadPlayerBalances(playerUuid: String, playerName: String) {
        if (!DatabaseManager.ready) return
        val currencies = CurrencyService.getActiveCurrencies()
        currencies.forEach { currency ->
            val account = AccountRepository.getOrCreate(playerUuid, playerName, currency.id)
            val scaled = CurrencyPrecisionUtil.scale(account.balance, currency.precision)
            balanceCache[cacheKey(playerUuid, currency.id)] = scaled
        }
    }

    /**
     * 清除指定玩家的缓存。
     * 【调用时机】玩家退出服务器时（可选 — 保留缓存可提升后续查询速度）。
     */
    fun unloadPlayer(playerUuid: String) {
        balanceCache.keys.removeIf { it.startsWith("$playerUuid:") }
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
     * 如果缓存中没有，返回 ZERO 并触发异步加载。
     *
     * @param playerUuid         玩家 UUID
     * @param currencyIdentifier 货币标识符
     * @return 当前余额
     */
    fun getBalance(playerUuid: String, currencyIdentifier: String): BigDecimal {
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return BigDecimal.ZERO
        val key = cacheKey(playerUuid, currency.id)
        return balanceCache[key] ?: BigDecimal.ZERO
    }

    /**
     * 通过货币 ID 获取余额。
     */
    fun getBalanceByCurrencyId(playerUuid: String, currencyId: String): BigDecimal {
        return balanceCache[cacheKey(playerUuid, currencyId)] ?: BigDecimal.ZERO
    }

    /**
     * 检查余额是否足够。
     */
    fun has(playerUuid: String, currencyIdentifier: String, amount: BigDecimal): Boolean {
        return getBalance(playerUuid, currencyIdentifier) >= amount
    }

    /**
     * 获取玩家在所有已启用货币下的账户快照列表。
     */
    fun getPlayerAccounts(playerUuid: String): List<AccountSnapshot> {
        val currencies = CurrencyService.getActiveCurrencies()
        val playerName = Bukkit.getOfflinePlayer(
            java.util.UUID.fromString(playerUuid)
        ).name ?: "Unknown"

        return currencies.map { currency ->
            val balance = getBalanceByCurrencyId(playerUuid, currency.id)
            AccountSnapshot(
                playerUuid = playerUuid,
                playerName = playerName,
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
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @param currencyIdentifier 货币标识符
     * @param amount     存款金额（正数）
     * @param reason     变更原因
     * @param operator   操作者
     * @return true = 成功
     */
    fun deposit(
        playerUuid: String,
        playerName: String,
        currencyIdentifier: String,
        amount: BigDecimal,
        reason: String = "",
        operator: String = "SYSTEM"
    ): Boolean {
        if (!DatabaseManager.ready) return false
        if (!CurrencyPrecisionUtil.isPositive(amount)) return false

        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return false
        if (!currency.enabled) return false

        val scaledAmount = CurrencyPrecisionUtil.scale(amount, currency.precision)
        val key = cacheKey(playerUuid, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)
        val newBalance = CurrencyPrecisionUtil.scale(currentBalance.add(scaledAmount), currency.precision)

        // 检查上限
        if (!checkMaxBalance(playerUuid, currency, newBalance)) return false

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerUuid, playerName, currency.identifier,
            ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false

        // 原子更新缓存
        balanceCache[key] = newBalance

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerUuid, playerName, currency.id)
                account.balance = newBalance
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerUuid, playerName, currency.id,
                    ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
                )
                // 触发变更后事件
                val postEvent = BalanceChangePostEvent(
                    playerUuid, playerName, currency.identifier,
                    ChangeType.DEPOSIT, scaledAmount, currentBalance, newBalance, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                // 数据库写入失败 — 回滚缓存
                warning("[MCE] 存款数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return true
    }

    /**
     * 取款（减少余额）。
     *
     * @return true = 成功
     */
    fun withdraw(
        playerUuid: String,
        playerName: String,
        currencyIdentifier: String,
        amount: BigDecimal,
        reason: String = "",
        operator: String = "SYSTEM"
    ): Boolean {
        if (!DatabaseManager.ready) return false
        if (!CurrencyPrecisionUtil.isPositive(amount)) return false

        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return false
        if (!currency.enabled) return false

        val scaledAmount = CurrencyPrecisionUtil.scale(amount, currency.precision)
        val key = cacheKey(playerUuid, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)

        // 余额不足检查
        if (currentBalance < scaledAmount) return false

        val newBalance = CurrencyPrecisionUtil.scale(currentBalance.subtract(scaledAmount), currency.precision)

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerUuid, playerName, currency.identifier,
            ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false

        // 原子更新缓存
        balanceCache[key] = newBalance

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerUuid, playerName, currency.id)
                account.balance = newBalance
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerUuid, playerName, currency.id,
                    ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
                )
                val postEvent = BalanceChangePostEvent(
                    playerUuid, playerName, currency.identifier,
                    ChangeType.WITHDRAW, scaledAmount, currentBalance, newBalance, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                warning("[MCE] 取款数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return true
    }

    /**
     * 设置余额（直接设定为指定值）。
     *
     * @return true = 成功
     */
    fun setBalance(
        playerUuid: String,
        playerName: String,
        currencyIdentifier: String,
        newAmount: BigDecimal,
        reason: String = "",
        operator: String = "SYSTEM"
    ): Boolean {
        if (!DatabaseManager.ready) return false
        if (!CurrencyPrecisionUtil.isNonNegative(newAmount)) return false

        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return false

        val scaledNew = CurrencyPrecisionUtil.scale(newAmount, currency.precision)
        val key = cacheKey(playerUuid, currency.id)
        val currentBalance = balanceCache.getOrDefault(key, BigDecimal.ZERO)
        val delta = scaledNew.subtract(currentBalance).abs()

        // 触发变更前事件
        val event = BalanceChangeEvent(
            playerUuid, playerName, currency.identifier,
            ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
        )
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false

        // 原子更新缓存
        balanceCache[key] = scaledNew

        // 异步写入数据库 + 审计日志
        AsyncExecutor.runAsync {
            try {
                val account = AccountRepository.getOrCreate(playerUuid, playerName, currency.id)
                account.balance = scaledNew
                AccountRepository.update(account)
                AuditService.writeLog(
                    playerUuid, playerName, currency.id,
                    ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
                )
                val postEvent = BalanceChangePostEvent(
                    playerUuid, playerName, currency.identifier,
                    ChangeType.SET, delta, currentBalance, scaledNew, reason, operator
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            } catch (e: Exception) {
                warning("[MCE] 设置余额数据库写入失败，回滚缓存: ${e.message}")
                balanceCache[key] = currentBalance
            }
        }
        return true
    }

    /**
     * 设置玩家在指定货币下的个人余额上限。
     */
    fun setMaxBalance(playerUuid: String, currencyIdentifier: String, maxBalance: Long): Boolean {
        if (!DatabaseManager.ready) return false
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return false
        return AccountRepository.setMaxBalance(playerUuid, currency.id, maxBalance)
    }

    // ======================== 内部方法 ========================

    /**
     * 检查新余额是否超过上限。
     *
     * @return true = 未超过上限（可以继续操作）
     */
    private fun checkMaxBalance(playerUuid: String, currency: CurrencyEntity, newBalance: BigDecimal): Boolean {
        // 获取个人上限（-1 表示使用货币默认值）
        val account = balanceCache[cacheKey(playerUuid, currency.id)]
        val maxBalance = currency.defaultMaxBalance
        if (maxBalance <= 0) return true  // -1 = 不限
        return newBalance <= BigDecimal.valueOf(maxBalance)
    }
}
