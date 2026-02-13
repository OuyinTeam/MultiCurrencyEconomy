package top.wcpe.mc.plugin.multicurrencyeconomy.internal.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil

/**
 * PlaceholderAPI 扩展 — 注册 %mce_xxx% 占位符。
 *
 * 【占位符列表】
 *   %mce_balance_<currency>%          → 原始余额数字（如 1234.56）
 *   %mce_balance_formatted_<currency>% → 格式化余额（如 ☆1,234.56）
 *   %mce_balance_primary%             → 主货币原始余额
 *   %mce_balance_primary_formatted%   → 主货币格式化余额
 *   %mce_currency_<id>_name%          → 货币显示名称
 *   %mce_currency_<id>_symbol%        → 货币符号
 *   %mce_currency_primary_name%       → 主货币名称
 *   %mce_ready%                       → 系统就绪状态（true/false）
 *
 * 【线程语义】
 *   PlaceholderAPI 在主线程调用占位符解析。
 *   所有数据来自内存缓存，无 I/O 阻塞。
 *
 * 【注册时机】
 *   在插件 onEnable 中检测 PlaceholderAPI 可用后调用 register()。
 */
class McePlaceholder : PlaceholderExpansion() {

    override fun getIdentifier(): String = "mce"

    override fun getAuthor(): String = "WCPE"

    override fun getVersion(): String = "1.0.0"

    /** 依赖 MultiCurrencyEconomy 插件 */
    override fun getRequiredPlugin(): String = "MultiCurrencyEconomy"

    /** 插件卸载时自动取消注册 */
    override fun persist(): Boolean = true

    /**
     * 解析占位符。
     *
     * @param player 请求占位符的玩家（可能为离线玩家）
     * @param params 占位符参数（%mce_xxx% 中的 xxx 部分）
     * @return 占位符值，无法解析时返回 null
     */
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        // 系统就绪状态
        if (params.equals("ready", true)) {
            return DatabaseManager.ready.toString()
        }

        // 需要玩家上下文的占位符
        if (player == null) return null
        val playerName = player.name ?: return null

        return when {
            // %mce_balance_primary% → 主货币原始余额
            params.equals("balance_primary", true) -> {
                val primary = CurrencyService.getPrimary() ?: return "0"
                AccountService.getBalance(playerName, primary.identifier).toPlainString()
            }

            // %mce_balance_primary_formatted% → 主货币格式化余额
            params.equals("balance_primary_formatted", true) -> {
                val primary = CurrencyService.getPrimary() ?: return "0"
                val balance = AccountService.getBalance(playerName, primary.identifier)
                CurrencyPrecisionUtil.formatWithSymbol(balance, primary.precision, primary.symbol)
            }

            // %mce_balance_formatted_<currency>% → 指定货币格式化余额
            params.startsWith("balance_formatted_", true) -> {
                val currencyId = params.removePrefix("balance_formatted_")
                val currency = CurrencyService.getByIdentifier(currencyId) ?: return "0"
                val balance = AccountService.getBalance(playerName, currency.identifier)
                CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)
            }

            // %mce_balance_<currency>% → 指定货币原始余额
            params.startsWith("balance_", true) -> {
                val currencyId = params.removePrefix("balance_")
                val currency = CurrencyService.getByIdentifier(currencyId) ?: return "0"
                AccountService.getBalance(playerName, currency.identifier).toPlainString()
            }

            // %mce_currency_primary_name% → 主货币名称
            params.equals("currency_primary_name", true) -> {
                CurrencyService.getPrimary()?.name ?: "N/A"
            }

            // %mce_currency_<id>_name% → 指定货币名称
            params.startsWith("currency_") && params.endsWith("_name") -> {
                val currencyId = params.removePrefix("currency_").removeSuffix("_name")
                CurrencyService.getByIdentifier(currencyId)?.name ?: "N/A"
            }

            // %mce_currency_<id>_symbol% → 指定货币符号
            params.startsWith("currency_") && params.endsWith("_symbol") -> {
                val currencyId = params.removePrefix("currency_").removeSuffix("_symbol")
                CurrencyService.getByIdentifier(currencyId)?.symbol ?: ""
            }

            else -> null
        }
    }
}
