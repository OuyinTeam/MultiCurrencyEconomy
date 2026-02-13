package top.wcpe.mc.plugin.multicurrencyeconomy.internal.vault

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil
import java.math.BigDecimal

/**
 * Vault Economy 接口实现 — 将 MultiCurrencyEconomy 的多货币体系桥接到 Vault 单货币接口。
 *
 * 【映射策略】
 *   Vault 操作默认针对"主货币"（[CurrencyService.getPrimary]）。
 *   第三方插件如需操作非主货币，请使用 MultiCurrencyEconomy API。
 *
 * 【同步适配】
 *   Vault Economy 接口为同步设计。本实现通过内存缓存（AccountService.balanceCache）
 *   实现无阻塞读取，写操作（deposit/withdraw）先更新缓存后异步刷库。
 *
 * 【playerName 为主键】
 *   所有操作以 playerName 作为主要标识，UUID 仅作为记录字段传入。
 *
 * 【银行功能】
 *   不支持银行功能，所有 bank 相关方法返回 NOT_IMPLEMENTED。
 *
 * 【线程语义】
 *   所有方法可在任意线程安全调用（内部通过 ConcurrentHashMap 保证）。
 */
class VaultEconomyProvider : Economy {

    /** 主货币标识符的快捷获取 */
    private val primaryIdentifier: String?
        get() = CurrencyService.getPrimary()?.identifier

    // ======================== 基本信息 ========================

    override fun isEnabled(): Boolean = DatabaseManager.ready

    override fun getName(): String = "MultiCurrencyEconomy"

    override fun hasBankSupport(): Boolean = false

    override fun fractionalDigits(): Int {
        return CurrencyService.getPrimary()?.precision ?: 2
    }

    override fun format(amount: Double): String {
        val currency = CurrencyService.getPrimary() ?: return amount.toString()
        return CurrencyPrecisionUtil.formatWithSymbol(
            BigDecimal.valueOf(amount), currency.precision, currency.symbol
        )
    }

    override fun currencyNamePlural(): String {
        return CurrencyService.getPrimary()?.name ?: "coins"
    }

    override fun currencyNameSingular(): String {
        return CurrencyService.getPrimary()?.name ?: "coin"
    }

    // ======================== 账户存在性 ========================

    override fun hasAccount(playerName: String): Boolean = true

    override fun hasAccount(playerName: String, worldName: String): Boolean = true

    override fun hasAccount(player: OfflinePlayer): Boolean = true

    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean = true

    // ======================== 创建账户 ========================

    override fun createPlayerAccount(playerName: String): Boolean = true

    override fun createPlayerAccount(playerName: String, worldName: String): Boolean = true

    override fun createPlayerAccount(player: OfflinePlayer): Boolean = true

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean = true

    // ======================== 余额查询 ========================

    override fun getBalance(playerName: String): Double {
        val identifier = primaryIdentifier ?: return 0.0
        return AccountService.getBalance(playerName, identifier).toDouble()
    }

    override fun getBalance(playerName: String, worldName: String): Double {
        return getBalance(playerName)
    }

    override fun getBalance(player: OfflinePlayer): Double {
        val name = player.name ?: return 0.0
        return getBalance(name)
    }

    override fun getBalance(player: OfflinePlayer, worldName: String): Double {
        return getBalance(player)
    }

    // ======================== 余额检查 ========================

    override fun has(playerName: String, amount: Double): Boolean {
        val identifier = primaryIdentifier ?: return false
        return AccountService.has(playerName, identifier, BigDecimal.valueOf(amount))
    }

    override fun has(playerName: String, worldName: String, amount: Double): Boolean {
        return has(playerName, amount)
    }

    override fun has(player: OfflinePlayer, amount: Double): Boolean {
        val name = player.name ?: return false
        return has(name, amount)
    }

    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean {
        return has(player, amount)
    }

    // ======================== 取款 ========================

    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse {
        val identifier = primaryIdentifier
            ?: return EconomyResponse(amount, 0.0, EconomyResponse.ResponseType.FAILURE, "主货币未配置")

        if (amount < 0) {
            return EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "金额不能为负数")
        }

        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val result = AccountService.withdraw(
            name, uuid, identifier,
            BigDecimal.valueOf(amount), "vault:withdraw", "VAULT"
        )

        val newBalance = AccountService.getBalance(name, identifier).toDouble()
        return if (result.success) {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "")
        } else {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.FAILURE, result.message)
        }
    }

    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        return withdrawPlayer(playerName, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        val name = player.name ?: return EconomyResponse(amount, 0.0, EconomyResponse.ResponseType.FAILURE, "玩家名称为空")
        return withdrawPlayer(name, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        return withdrawPlayer(player, amount)
    }

    // ======================== 存款 ========================

    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse {
        val identifier = primaryIdentifier
            ?: return EconomyResponse(amount, 0.0, EconomyResponse.ResponseType.FAILURE, "主货币未配置")

        if (amount < 0) {
            return EconomyResponse(amount, getBalance(playerName), EconomyResponse.ResponseType.FAILURE, "金额不能为负数")
        }

        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val result = AccountService.deposit(
            name, uuid, identifier,
            BigDecimal.valueOf(amount), "vault:deposit", "VAULT"
        )

        val newBalance = AccountService.getBalance(name, identifier).toDouble()
        return if (result.success) {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, "")
        } else {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.FAILURE, result.message)
        }
    }

    override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        return depositPlayer(playerName, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        val name = player.name ?: return EconomyResponse(amount, 0.0, EconomyResponse.ResponseType.FAILURE, "玩家名称为空")
        return depositPlayer(name, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        return depositPlayer(player, amount)
    }

    // ======================== 银行功能（不支持） ========================

    private val notSupported = EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "不支持银行功能")

    override fun createBank(name: String, player: String): EconomyResponse = notSupported
    override fun createBank(name: String, player: OfflinePlayer): EconomyResponse = notSupported
    override fun deleteBank(name: String): EconomyResponse = notSupported
    override fun bankBalance(name: String): EconomyResponse = notSupported
    override fun bankHas(name: String, amount: Double): EconomyResponse = notSupported
    override fun bankWithdraw(name: String, amount: Double): EconomyResponse = notSupported
    override fun bankDeposit(name: String, amount: Double): EconomyResponse = notSupported
    override fun isBankOwner(name: String, playerName: String): EconomyResponse = notSupported
    override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse = notSupported
    override fun isBankMember(name: String, playerName: String): EconomyResponse = notSupported
    override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse = notSupported
    override fun getBanks(): MutableList<String> = mutableListOf()
}
