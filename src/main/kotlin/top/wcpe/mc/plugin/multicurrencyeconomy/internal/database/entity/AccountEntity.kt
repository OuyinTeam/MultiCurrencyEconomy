package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity

import com.easy.query.core.annotation.Column
import com.easy.query.core.annotation.EntityProxy
import com.easy.query.core.annotation.Table
import com.easy.query.core.proxy.ProxyEntityAvailable
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.proxy.AccountEntityProxy
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 玩家账户余额实体 — 映射 mce_account 表。
 *
 * 【表职责】存储玩家在每种货币下的当前余额，每个 (playerUuid, currencyId) 组合唯一。
 * 【余额精度】balance 列使用 DECIMAL(20,8) 存储，应用层在读写时按货币精度进行舍入。
 * 【上限机制】maxBalance 字段可按玩家+货币粒度覆盖货币默认上限（-1 = 使用货币默认值）。
 * 【命名策略】列名映射依赖 easy-query 命名策略，建议配置为下划线风格（UNDERLINED）。
 * 【KSP 代理】AccountEntityProxy 由 sql-ksp-processor 在构建时自动生成。
 */
@Table(value = "mce_account", comment = "玩家账户余额表")
@EntityProxy
class AccountEntity : ProxyEntityAvailable<AccountEntity, AccountEntityProxy> {

    /** 账户唯一 ID（UUID 字符串） */
    @Column(primaryKey = true, comment = "账户唯一ID", dbType = "varchar(36)")
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
     * 当前余额。
     * 数据库中以 DECIMAL(20,8) 存储，应用层读写时按货币精度舍入。
     */
    @Column(comment = "当前余额", dbType = "DECIMAL(20,8)")
    var balance: BigDecimal = BigDecimal.ZERO

    /**
     * 玩家级余额上限。
     * -1 = 使用货币默认上限；正数 = 覆盖上限。
     */
    @Column(comment = "个人余额上限 -1使用默认", dbType = "bigint")
    var maxBalance: Long = -1L

    /** 创建时间 */
    @Column(comment = "创建时间", dbType = "DATETIME")
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** 最后更新时间 */
    @Column(comment = "最后更新时间", dbType = "DATETIME")
    var updatedAt: LocalDateTime = LocalDateTime.now()

}
