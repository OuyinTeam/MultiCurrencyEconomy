package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity

import com.easy.query.core.annotation.Column
import com.easy.query.core.annotation.EntityProxy
import com.easy.query.core.annotation.Table
import com.easy.query.core.proxy.ProxyEntityAvailable
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.proxy.CurrencyEntityProxy
import java.time.LocalDateTime

/**
 * 货币定义实体 — 映射 mce_currency 表。
 *
 * 【表职责】存储所有货币的元数据定义（如金币、点券、钻石等）。
 * 【主键策略】使用 INT 自增主键（generatedKey = true），由数据库生成。
 * 【软删除】[deleted] 字段实现逻辑删除，被删除的货币在 Vault/GUI/命令中不可见，
 *            但其历史流水与账户数据保留，保证审计完整性。
 * 【主货币】[primary] 标记的货币作为 Vault Economy 默认操作币种，全局仅一个。
 * 【控制台日志】[consoleLog] 控制该货币的余额变更是否在控制台输出日志。
 * 【命名策略】列名映射依赖 easy-query 命名策略，建议配置为下划线风格（UNDERLINED）。
 * 【KSP 代理】CurrencyEntityProxy 由 sql-ksp-processor 在构建时自动生成。
 */
@Table(value = "mce_currency", comment = "货币定义表")
@EntityProxy
class CurrencyEntity : ProxyEntityAvailable<CurrencyEntity, CurrencyEntityProxy> {

    /** 货币自增主键 */
    @Column(primaryKey = true, generatedKey = true, comment = "货币自增主键", dbType = "INT")
    var id: Int = 0

    /** 货币标识符 — 全局唯一业务键（英文小写，如 "coin"） */
    @Column(comment = "货币标识符", dbType = "varchar(64)")
    var identifier: String = ""

    /** 货币显示名称（如 "金币"） */
    @Column(comment = "货币显示名称", dbType = "varchar(128)")
    var name: String = ""

    /** 货币符号（如 "☆"、"$"），用于余额格式化前缀 */
    @Column(comment = "货币符号", dbType = "varchar(16)")
    var symbol: String = ""

    /** 精度 — 小数位数（0 = 仅整数，最大 8） */
    @Column(comment = "小数位精度", dbType = "INT")
    var precision: Int = 2

    /** 默认余额上限（-1 = 不限） */
    @Column(comment = "默认余额上限", dbType = "BIGINT")
    var defaultMaxBalance: Long = -1L

    /** 是否为主货币（Vault Economy 默认币种） */
    @Column(comment = "是否主货币 0-否 1-是", dbType = "TINYINT")
    var primary: Boolean = false

    /** 是否启用（禁用时不允许余额变更） */
    @Column(comment = "是否启用 0-禁用 1-启用", dbType = "TINYINT")
    var enabled: Boolean = true

    /** 是否已逻辑删除 */
    @Column(comment = "逻辑删除 0-正常 1-已删除", dbType = "TINYINT")
    var deleted: Boolean = false

    /** 是否在控制台输出该货币的余额变更日志 */
    @Column(comment = "控制台日志 0-关闭 1-开启", dbType = "TINYINT")
    var consoleLog: Boolean = true

    /** 创建时间 */
    @Column(comment = "创建时间", dbType = "DATETIME")
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** 最后更新时间 */
    @Column(comment = "最后更新时间", dbType = "DATETIME")
    var updatedAt: LocalDateTime = LocalDateTime.now()

}
