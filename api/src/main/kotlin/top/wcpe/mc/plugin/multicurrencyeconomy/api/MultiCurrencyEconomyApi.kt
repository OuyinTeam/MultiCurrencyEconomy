package top.wcpe.mc.plugin.multicurrencyeconomy.api

import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.AccountSnapshot
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.CurrencyInfo
import java.math.BigDecimal

/**
 * MultiCurrencyEconomy 开发者 API 入口（单例）。
 *
 * 【用途】第三方插件通过此对象访问多货币经济系统的查询与信息能力。
 * 【前置条件】MultiCurrencyEconomy 插件必须已加载并完成数据库初始化（readiness = true）。
 * 【线程语义】所有方法均可在任意线程调用；内部读取来自内存缓存，无阻塞 I/O。
 * 【注意事项】
 *   - 调用前请确保 MultiCurrencyEconomy 插件已启用，否则将抛出 IllegalStateException。
 *   - 余额变更请通过 Vault Economy 接口或 Bukkit 事件系统，不在此 API 中直接暴露写操作。
 *   - MultiCurrencyEconomy 依赖 CoreLib，CoreLib 需要作为前置插件安装并启用。
 */
object MultiCurrencyEconomyApi {

    /**
     * 内部委托实现 — 由插件主模块在初始化完成后注入。
     * 第三方插件不应直接访问或修改此字段。
     */
    @Volatile
    var delegate: ApiDelegate? = null

    /**
     * 获取指定玩家在指定货币下的余额。
     *
     * @param playerUuid 玩家 UUID 字符串
     * @param currencyIdentifier 货币标识符（如 "coin"），不区分大小写
     * @return 当前余额（已按货币精度规范化）；若玩家无该货币账户则返回 BigDecimal.ZERO
     * @throws IllegalStateException 插件未初始化
     */
    fun getBalance(playerUuid: String, currencyIdentifier: String): BigDecimal {
        return requireDelegate().getBalance(playerUuid, currencyIdentifier)
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
     * @return 主货币信息；若尚无主货币（极端情况）则返回 null
     * @throws IllegalStateException 插件未初始化
     */
    fun getPrimaryCurrency(): CurrencyInfo? {
        return requireDelegate().getPrimaryCurrency()
    }

    /**
     * 获取指定玩家在所有已启用货币下的账户快照列表。
     *
     * @param playerUuid 玩家 UUID 字符串
     * @return 账户快照列表；无账户时返回空列表
     * @throws IllegalStateException 插件未初始化
     */
    fun getPlayerAccounts(playerUuid: String): List<AccountSnapshot> {
        return requireDelegate().getPlayerAccounts(playerUuid)
    }

    /**
     * 检查 API 是否就绪（插件已加载且数据库初始化完成）。
     *
     * @return true = 可正常调用；false = 插件未加载或初始化未完成
     */
    fun isReady(): Boolean = delegate != null

    /** 获取委托实例，未初始化时抛出异常 */
    private fun requireDelegate(): ApiDelegate {
        return delegate ?: throw IllegalStateException(
            "MultiCurrencyEconomy API 尚未初始化。请确保 MultiCurrencyEconomy 插件已启用且数据库初始化完成。"
        )
    }
}

/**
 * API 委托接口 — 由插件内部实现并注入到 [MultiCurrencyEconomyApi]。
 *
 * 【用途】解耦 API 模块与内部实现，API 模块无需依赖内部实现类。
 * 【注意】第三方插件不应实现此接口。
 */
interface ApiDelegate {
    /** 获取余额 */
    fun getBalance(playerUuid: String, currencyIdentifier: String): BigDecimal
    /** 获取所有已启用货币 */
    fun getActiveCurrencies(): List<CurrencyInfo>
    /** 获取主货币 */
    fun getPrimaryCurrency(): CurrencyInfo?
    /** 获取玩家所有账户快照 */
    fun getPlayerAccounts(playerUuid: String): List<AccountSnapshot>
}
