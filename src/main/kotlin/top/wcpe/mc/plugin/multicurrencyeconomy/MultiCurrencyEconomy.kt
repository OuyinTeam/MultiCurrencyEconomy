package top.wcpe.mc.plugin.multicurrencyeconomy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.multicurrencyeconomy.api.ApiDelegate
import top.wcpe.mc.plugin.multicurrencyeconomy.api.MultiCurrencyEconomyApi
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.AccountSnapshot
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.CurrencyInfo
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.async.AsyncExecutor
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.placeholder.McePlaceholder
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.vault.VaultEconomyProvider
import java.math.BigDecimal

/**
 * MultiCurrencyEconomy 插件主类 — 管理插件的完整生命周期。
 *
 * 【启动流程】（onEnable）
 *   1. 重置异步执行器状态
 *   2. 在异步线程中初始化数据库（CoreLib 数据源 → Code First 建表 → 就绪门控）
 *   3. 初始化货币服务（刷新缓存 + 创建默认主货币）
 *   4. 初始化账户服务（加载在线玩家余额缓存）
 *   5. 注入 API 委托（MultiCurrencyEconomyApi.delegate）
 *   6. 注册 Vault Economy Provider（如果 Vault 可用）
 *   7. 注册 PlaceholderAPI 扩展（如果 PAPI 可用）
 *
 * 【关闭流程】（onDisable）
 *   1. 清理 API 委托
 *   2. 关闭异步执行器（等待未完成任务）
 *   3. 关闭数据库管理器
 *
 * 【依赖关系】
 *   - 硬依赖：CoreLib（数据源管理）
 *   - 软依赖：Vault（经济系统桥接）、PlaceholderAPI（占位符）
 *
 * 【线程安全】
 *   onEnable/onDisable 在主线程调用，数据库初始化在异步线程执行。
 */
object MultiCurrencyEconomy : Plugin() {

    /** Vault Economy Provider 实例（用于反注册） */
    private var vaultProvider: VaultEconomyProvider? = null

    // ======================== 生命周期 ========================

    override fun onEnable() {
        info("[MCE] MultiCurrencyEconomy 正在启动...")

        // 重置异步执行器
        AsyncExecutor.reset()

        // 异步初始化数据库及服务
        submitAsync {
            try {
                // 1. 数据库初始化（Code First 建表）
                DatabaseManager.initialize()

                // 2. 货币服务初始化（缓存 + 默认货币）
                CurrencyService.initialize()

                // 3. 账户服务初始化（加载在线玩家缓存）
                AccountService.initialize()

                // 4. 注入 API 委托
                injectApiDelegate()
                info("[MCE] API 委托注入完成。")

                // 5. 注册 Vault（在主线程执行，因为 Bukkit ServiceManager 非线程安全）
                AsyncExecutor.runSync {
                    registerVault()
                    registerPlaceholderApi()
                    info("[MCE] MultiCurrencyEconomy 启动完成！")
                }
            } catch (e: Exception) {
                severe("[MCE] 插件初始化失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisable() {
        info("[MCE] MultiCurrencyEconomy 正在关闭...")

        // 清理 API 委托
        MultiCurrencyEconomyApi.delegate = null

        // 关闭异步执行器
        AsyncExecutor.shutdown(MainConfig.shutdownWaitSeconds)

        // 关闭数据库管理器
        DatabaseManager.shutdown()

        // 清除账户缓存
        AccountService.clearCache()

        info("[MCE] MultiCurrencyEconomy 已关闭。")
    }

    // ======================== Vault 注册 ========================

    /**
     * 注册 Vault Economy Provider。
     * 仅在 Vault 插件存在时执行。
     */
    private fun registerVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            info("[MCE] 未检测到 Vault 插件，跳过 Economy Provider 注册。")
            return
        }
        try {
            val provider = VaultEconomyProvider()
            Bukkit.getServicesManager().register(
                Economy::class.java,
                provider,
                Bukkit.getPluginManager().getPlugin("MultiCurrencyEconomy")!!,
                ServicePriority.Normal
            )
            vaultProvider = provider
            info("[MCE] Vault Economy Provider 注册成功。")
        } catch (e: Exception) {
            warning("[MCE] Vault Economy Provider 注册失败: ${e.message}")
        }
    }

    // ======================== PlaceholderAPI 注册 ========================

    /**
     * 注册 PlaceholderAPI 扩展。
     * 仅在 PlaceholderAPI 插件存在时执行。
     */
    private fun registerPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            info("[MCE] 未检测到 PlaceholderAPI 插件，跳过占位符注册。")
            return
        }
        try {
            McePlaceholder().register()
            info("[MCE] PlaceholderAPI 扩展注册成功 (标识: mce)。")
        } catch (e: Exception) {
            warning("[MCE] PlaceholderAPI 扩展注册失败: ${e.message}")
        }
    }

    // ======================== API 委托注入 ========================

    /**
     * 注入 API 委托实现。
     * 将内部服务桥接到公开 API 模块。
     */
    private fun injectApiDelegate() {
        MultiCurrencyEconomyApi.delegate = object : ApiDelegate {

            override fun getBalance(playerUuid: String, currencyIdentifier: String): BigDecimal {
                return AccountService.getBalance(playerUuid, currencyIdentifier)
            }

            override fun getActiveCurrencies(): List<CurrencyInfo> {
                return CurrencyService.getActiveCurrencies().map { CurrencyService.toInfo(it) }
            }

            override fun getPrimaryCurrency(): CurrencyInfo? {
                return CurrencyService.getPrimary()?.let { CurrencyService.toInfo(it) }
            }

            override fun getPlayerAccounts(playerUuid: String): List<AccountSnapshot> {
                return AccountService.getPlayerAccounts(playerUuid)
            }
        }
    }
}
