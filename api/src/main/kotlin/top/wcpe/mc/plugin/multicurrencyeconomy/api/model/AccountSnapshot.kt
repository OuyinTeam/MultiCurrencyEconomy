package top.wcpe.mc.plugin.multicurrencyeconomy.api.model

import java.math.BigDecimal

/**
 * 账户快照只读 DTO — 对外暴露玩家在某一货币下的余额信息。
 *
 * 【用途】第三方插件通过 API 查询玩家资产组合，每种货币一条记录。
 * 【线程语义】不可变数据类，线程安全。
 */
data class AccountSnapshot(

    /** 玩家 UUID 字符串 */
    val playerUuid: String,

    /** 玩家名称（冗余，方便展示） */
    val playerName: String,

    /** 货币标识符（如 "coin"） */
    val currencyIdentifier: String,

    /** 货币显示名称（如 "金币"） */
    val currencyDisplayName: String,

    /** 货币符号 */
    val currencySymbol: String,

    /** 货币精度（小数位数） */
    val currencyPrecision: Int,

    /** 当前余额（已按货币精度规范化） */
    val balance: BigDecimal,

    /** 余额格式化字符串（含符号，如 "☆1,234.56"） */
    val formattedBalance: String
)
