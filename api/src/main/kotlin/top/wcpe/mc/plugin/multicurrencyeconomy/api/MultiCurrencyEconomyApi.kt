package top.wcpe.mc.plugin.multicurrencyeconomy.api

import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.AccountSnapshot
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.CurrencyInfo
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.EconomyResult
import java.math.BigDecimal

/**
 * MultiCurrencyEconomy 开发者 API 入口（单例）。
 *
 * 【用途】第三方插件通过此对象访问多货币经济系统的查询与操作能力。
 * 【委托模式】使用 [delegate] 方法获取 [EconomyOperator]，必须提供操作者标识（operatorId）。
 *             所有余额变更操作通过 [EconomyOperator] 执行，且必须提供变更原因（reason）。
 * 【前置条件】MultiCurrencyEconomy 插件必须已加载并完成数据库初始化。
 * 【线程语义】所有查询方法均可在任意线程调用；内部读取来自内存缓存，无阻塞 I/O。
 *             写操作（add/take/set）先更新缓存后异步写库，也可在任意线程调用。
 * 【注意事项】
 *   - 调用前请确保 MultiCurrencyEconomy 插件已启用，否则将抛出 IllegalStateException。
 *   - MultiCurrencyEconomy 依赖 CoreLib，CoreLib 需要作为前置插件安装并启用。
 *
 * 【使用示例】
 * ```kotlin
 * val operator = MultiCurrencyEconomyApi.delegate("MyPlugin")
 * val result = operator.add("PlayerName", "coin", BigDecimal("100.00"), "任务奖励")
 * if (result.success) {
 *     // 操作成功，result.balance 为新余额
 * }
 * ```
 */
object MultiCurrencyEconomyApi {

    /**
     * 内部委托实现 — 由插件主模块在初始化完成后注入。
     * 第三方插件不应直接访问或修改此字段。
     */
    @Volatile
    var apiDelegate: ApiDelegate? = null

    /**
     * 获取带操作者标识的经济操作器。
     * 操作者标识会记录在审计流水中，用于追踪操作来源。
     *
     * @param operatorId 操作者标识（通常为插件名、玩家名或 "CONSOLE"）
     * @return 经济操作器实例
     * @throws IllegalStateException 插件未初始化
     */
    fun delegate(operatorId: String): EconomyOperator {
        val delegate = requireDelegate()
        return EconomyOperator(operatorId, delegate)
    }

    // ======================== 静态查询方法（不需要操作者） ========================

    /**
     * 获取指定玩家在指定货币下的余额。
     *
     * @param playerName 玩家名称
     * @param currencyIdentifier 货币标识符（如 "coin"），不区分大小写
     * @return 当前余额（已按货币精度规范化）；若玩家无该货币账户则返回 BigDecimal.ZERO
     * @throws IllegalStateException 插件未初始化
     */
    fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal {
        return requireDelegate().getBalance(playerName, currencyIdentifier)
    }

    /**
     * 获取所有已启用（未删除、未禁用）的货币列表。
     *
     * @return 货币信息只读列表
     * @throws IllegalStateException 插件未初始化
     */
    fun getActiveCurrencies(): List<CurrencyInfo> {
        return requireDelegate().getActiveCurrencies()
    }

    /**
     * 获取当前主货币信息。
     *
     * @return 主货币信息；若尚无主货币则返回 null
     * @throws IllegalStateException 插件未初始化
     */
    fun getPrimaryCurrency(): CurrencyInfo? {
        return requireDelegate().getPrimaryCurrency()
    }

    /**
     * 获取指定玩家在所有已启用货币下的账户快照列表。
     *
     * @param playerName 玩家名称
     * @return 账户快照列表；无账户时返回空列表
     * @throws IllegalStateException 插件未初始化
     */
    fun getPlayerAccounts(playerName: String): List<AccountSnapshot> {
        return requireDelegate().getPlayerAccounts(playerName)
    }

    /**
     * 检查 API 是否就绪（插件已加载且数据库初始化完成）。
     *
     * @return true = 可正常调用；false = 插件未加载或初始化未完成
     */
    fun isReady(): Boolean = apiDelegate != null

    /** 获取委托实例，未初始化时抛出异常 */
    private fun requireDelegate(): ApiDelegate {
        return apiDelegate ?: throw IllegalStateException(
            "MultiCurrencyEconomy API 尚未初始化。请确保 MultiCurrencyEconomy 插件已启用且数据库初始化完成。"
        )
    }
}

/**
 * 经济操作器 — 绑定了操作者标识的余额变更操作句柄。
 *
 * 【用途】第三方插件通过 [MultiCurrencyEconomyApi.delegate] 获取此实例后，
 *         调用 [add]、[take]、[set] 方法进行余额变更，所有操作自动关联操作者标识。
 * 【线程语义】可在任意线程调用，内部通过缓存保证读取安全，写入通过异步执行器完成。
 */
class EconomyOperator(
    /** 操作者标识（记录在审计流水中） */
    val operatorId: String,
    /** 内部委托 */
    private val delegate: ApiDelegate
) {
    /**
     * 增加余额（存款）。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @param amount             增加金额（必须为正数）
     * @param reason             变更原因（必填，记录在审计流水中）
     * @return 操作结果
     */
    fun add(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult {
        return delegate.add(playerName, currencyIdentifier, amount, reason, operatorId)
    }

    /**
     * 扣除余额（取款）。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @param amount             扣除金额（必须为正数）
     * @param reason             变更原因（必填，记录在审计流水中）
     * @return 操作结果
     */
    fun take(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult {
        return delegate.take(playerName, currencyIdentifier, amount, reason, operatorId)
    }

    /**
     * 设置余额（直接覆盖为指定值）。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @param amount             目标余额（必须非负）
     * @param reason             变更原因（必填，记录在审计流水中）
     * @return 操作结果
     */
    fun set(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String): EconomyResult {
        return delegate.set(playerName, currencyIdentifier, amount, reason, operatorId)
    }

    /**
     * 获取指定玩家在指定货币下的余额。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @return 当前余额
     */
    fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal {
        return delegate.getBalance(playerName, currencyIdentifier)
    }
}

/**
 * API 委托接口 — 由插件内部实现并注入到 [MultiCurrencyEconomyApi]。
 *
 * 【用途】解耦 API 模块与内部实现，API 模块无需依赖内部实现类。
 * 【注意】第三方插件不应实现此接口。
 */
interface ApiDelegate {
    /** 获取余额 — 以 playerName 查询 */
    fun getBalance(playerName: String, currencyIdentifier: String): BigDecimal

    /** 增加余额 — 必须提供 reason 和 operator */
    fun add(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String, operator: String): EconomyResult

    /** 扣除余额 — 必须提供 reason 和 operator */
    fun take(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String, operator: String): EconomyResult

    /** 设置余额 — 必须提供 reason 和 operator */
    fun set(playerName: String, currencyIdentifier: String, amount: BigDecimal, reason: String, operator: String): EconomyResult

    /** 获取所有已启用货币 */
    fun getActiveCurrencies(): List<CurrencyInfo>

    /** 获取主货币 */
    fun getPrimaryCurrency(): CurrencyInfo?

    /** 获取玩家所有账户快照 — 以 playerName 查询 */
    fun getPlayerAccounts(playerName: String): List<AccountSnapshot>
}
