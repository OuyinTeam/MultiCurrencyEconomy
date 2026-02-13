package top.wcpe.mc.plugin.multicurrencyeconomy.internal.config

import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.math.RoundingMode

/**
 * 主配置管理器 — 统一读取 config.yml 中的所有配置项。
 *
 * 【生命周期】由 TabooLib 在 INIT 阶段注入 [conf]，之后可随时通过属性访问配置值。
 * 【热重载】使用 [reload] 方法触发配置文件重新读取（由 /mce reload 命令调用）。
 * 【线程语义】读取线程安全（Configuration 内部使用同步机制）。
 * 【依赖模块】TabooLib Configuration 模块。
 */
object MainConfig {

    /** TabooLib 自动注入的配置实例，映射 config.yml */
    @Config("config.yml", autoReload = true)
    lateinit var conf: Configuration
        private set

    // ======================== 数据源 ========================

    /** CoreLib 数据源 key — 必须与 CoreLib 注册的数据源名称一致 */
    val datasourceKey: String
        get() = conf.getString("datasource-key", "default")!!

    // ======================== 默认主货币 ========================

    /** 默认货币标识符 */
    val defaultCurrencyIdentifier: String
        get() = conf.getString("default-currency.identifier", "coin")!!

    /** 默认货币显示名称 */
    val defaultCurrencyName: String
        get() = conf.getString("default-currency.name", "金币")!!

    /** 默认货币符号 */
    val defaultCurrencySymbol: String
        get() = conf.getString("default-currency.symbol", "☆")!!

    /** 默认货币精度（小数位数） */
    val defaultCurrencyPrecision: Int
        get() = conf.getInt("default-currency.precision", 2)

    /** 默认余额上限（-1 = 不限） */
    val defaultCurrencyMaxBalance: Long
        get() = conf.getLong("default-currency.default-max-balance", -1L)

    /** 默认货币控制台日志开关（是否在控制台输出余额变更日志） */
    val defaultCurrencyConsoleLog: Boolean
        get() = conf.getBoolean("default-currency.console-log", true)

    // ======================== 舍入策略 ========================

    /**
     * 金额舍入策略。
     * 从配置读取字符串并转换为 [RoundingMode]，无效值时回退到 DOWN。
     */
    val roundingMode: RoundingMode
        get() = runCatching {
            RoundingMode.valueOf(conf.getString("rounding-mode", "DOWN")!!)
        }.getOrDefault(RoundingMode.DOWN)

    // ======================== 异步配置 ========================

    /** 插件禁用时等待异步任务完成的最大秒数 */
    val shutdownWaitSeconds: Int
        get() = conf.getInt("async.shutdown-wait-seconds", 5)

    // ======================== 备份配置 ========================

    /** 最大备份快照保留数量 */
    val maxSnapshots: Int
        get() = conf.getInt("backup.max-snapshots", 50)

    // ======================== 操作方法 ========================

    /**
     * 重新加载配置文件。
     * 由 /mce reload 命令调用。
     */
    fun reload() {
        conf.reload()
    }
}
