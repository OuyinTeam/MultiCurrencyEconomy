package top.wcpe.mc.plugin.multicurrencyeconomy.api.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 交易流水只读 DTO — 对外暴露单笔交易的完整审计信息。
 *
 * 【用途】流水查询展示（GUI / 指令 / 第三方 API 调用）。
 * 【审计价值】包含变更前后余额、操作者、原因等完整上下文，可用于事后审计与争议处理。
 * 【线程语义】不可变数据类，线程安全。
 */
data class TransactionRecord(

    /** 流水唯一 ID（UUID） */
    val id: String,

    /** 玩家 UUID */
    val playerUuid: String,

    /** 玩家名称 */
    val playerName: String,

    /** 货币标识符 */
    val currencyIdentifier: String,

    /** 操作类型（DEPOSIT / WITHDRAW / SET / ROLLBACK） */
    val type: ChangeType,

    /** 变动金额（正数 = 入账，负数 = 出账） */
    val amount: BigDecimal,

    /** 变动前余额 */
    val balanceBefore: BigDecimal,

    /** 变动后余额 */
    val balanceAfter: BigDecimal,

    /** 变更原因 / 来源（如 "vault:EssentialsX" / "command:admin" / "api:MyPlugin"） */
    val reason: String,

    /** 操作者（UUID 或 "SYSTEM" / "CONSOLE"） */
    val operator: String,

    /** 发生时间 */
    val occurredAt: LocalDateTime
)
