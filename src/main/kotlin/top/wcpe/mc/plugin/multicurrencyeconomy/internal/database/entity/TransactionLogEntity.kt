package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity

import com.easy.query.core.annotation.Column
import com.easy.query.core.annotation.EntityProxy
import com.easy.query.core.annotation.Table
import com.easy.query.core.proxy.ProxyEntityAvailable
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.proxy.TransactionLogEntityProxy
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 交易流水实体 — 映射 mce_transaction_log 表。
 *
 * 【表职责】记录每一笔余额变更的完整审计信息，仅追加（INSERT-only），不可修改或删除。
 * 【审计要素】包含变更前后余额、操作类型、变更原因、操作者、时间等，满足事后审计需求。
 * 【性能考虑】高写入频率表，建议在 (playerUuid, currencyId) 和 (occurredAt) 上建立索引。
 * 【命名策略】列名映射依赖 easy-query 命名策略，建议配置为下划线风格（UNDERLINED）。
 * 【KSP 代理】TransactionLogEntityProxy 由 sql-ksp-processor 在构建时自动生成。
 */
@Table(value = "mce_transaction_log", comment = "交易流水表")
@EntityProxy
class TransactionLogEntity : ProxyEntityAvailable<TransactionLogEntity, TransactionLogEntityProxy> {

    /** 流水唯一 ID（UUID 字符串） */
    @Column(primaryKey = true, comment = "流水唯一ID", dbType = "varchar(36)")
    var id: String = ""

    /** 玩家 UUID */
    @Column(comment = "玩家UUID", dbType = "varchar(36)")
    var playerUuid: String = ""

    /** 玩家名称（冗余字段，方便查询与展示） */
    @Column(comment = "玩家名称", dbType = "varchar(64)")
    var playerName: String = ""

    /** 关联的货币 ID */
    @Column(comment = "货币ID", dbType = "varchar(36)")
    var currencyId: String = ""

    /**
     * 操作类型。
     * DEPOSIT / WITHDRAW / SET / ROLLBACK
     */
    @Column(comment = "操作类型", dbType = "varchar(32)")
    var type: String = ""

    /** 变动金额（对应操作的绝对值） */
    @Column(comment = "变动金额", dbType = "DECIMAL(20,8)")
    var amount: BigDecimal = BigDecimal.ZERO

    /** 变动前余额 */
    @Column(comment = "变动前余额", dbType = "DECIMAL(20,8)")
    var balanceBefore: BigDecimal = BigDecimal.ZERO

    /** 变动后余额 */
    @Column(comment = "变动后余额", dbType = "DECIMAL(20,8)")
    var balanceAfter: BigDecimal = BigDecimal.ZERO

    /**
     * 变更原因 / 来源。
     * 示例值："vault:EssentialsX"、"command:admin"、"api:MyPlugin"
     */
    @Column(comment = "变更原因", dbType = "varchar(512)")
    var reason: String = ""

    /** 操作者（执行者 UUID 字符串，或 "SYSTEM" / "CONSOLE"） */
    @Column(comment = "操作者", dbType = "varchar(64)")
    var operator: String = ""

    /** 操作发生时间 */
    @Column(comment = "操作时间", dbType = "DATETIME(3)")
    var occurredAt: LocalDateTime = LocalDateTime.now()

}
