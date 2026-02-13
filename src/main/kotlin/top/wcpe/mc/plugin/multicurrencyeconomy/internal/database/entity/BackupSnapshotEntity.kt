package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity

import com.easy.query.core.annotation.Column
import com.easy.query.core.annotation.EntityProxy
import com.easy.query.core.annotation.Table
import com.easy.query.core.proxy.ProxyEntityAvailable
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.proxy.BackupSnapshotEntityProxy
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 备份快照实体 — 映射 mce_backup_snapshot 表。
 *
 * 【表职责】存储备份快照中每个玩家在每种货币下的余额快照。
 *           一次备份操作会产生多条记录（每个账户一条），通过 [snapshotId] 关联。
 * 【主键策略】使用 BIGINT 自增主键（generatedKey = true），适应高增长场景。
 * 【查询键】以 playerName 作为主要查询维度。
 * 【用途】管理员通过 /mce backup create 创建全量快照，/mce rollback 从快照恢复余额。
 * 【命名策略】列名映射依赖 easy-query 命名策略，建议配置为下划线风格（UNDERLINED）。
 * 【KSP 代理】BackupSnapshotEntityProxy 由 sql-ksp-processor 在构建时自动生成。
 */
@Table(value = "mce_backup_snapshot", comment = "备份快照表")
@EntityProxy
class BackupSnapshotEntity : ProxyEntityAvailable<BackupSnapshotEntity, BackupSnapshotEntityProxy> {

    /** 行自增主键（BIGINT 适应高增长） */
    @Column(primaryKey = true, generatedKey = true, comment = "行自增主键", dbType = "BIGINT")
    var id: Long = 0

    /**
     * 快照批次 ID — 同一次备份操作的所有记录共享此 ID。
     * 管理员回滚时通过此 ID 定位一组快照。
     */
    @Column(comment = "快照批次ID", dbType = "varchar(36)")
    var snapshotId: String = ""

    /** 玩家 UUID（记录字段） */
    @Column(comment = "玩家UUID", dbType = "varchar(36)")
    var playerUuid: String = ""

    /** 玩家名称 — 主要查询键 */
    @Column(comment = "玩家名称", dbType = "varchar(64)")
    var playerName: String = ""

    /** 货币 ID */
    @Column(comment = "货币ID", dbType = "INT")
    var currencyId: Int = 0

    /** 快照时的余额 */
    @Column(comment = "快照余额", dbType = "DECIMAL(20,8)")
    var balance: BigDecimal = BigDecimal.ZERO

    /** 备份备注（管理员创建快照时提供的说明文字） */
    @Column(comment = "备份备注", dbType = "varchar(256)")
    var memo: String = ""

    /** 快照创建时间 */
    @Column(comment = "快照创建时间", dbType = "DATETIME")
    var createdAt: LocalDateTime = LocalDateTime.now()

}
