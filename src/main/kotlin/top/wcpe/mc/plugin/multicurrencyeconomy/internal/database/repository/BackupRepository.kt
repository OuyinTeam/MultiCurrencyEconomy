package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.BackupSnapshotEntity

/**
 * 备份快照仓库 — 封装 mce_backup_snapshot 表的所有数据库操作。
 *
 * 【职责】提供快照的插入、查询方法，支持按批次查询和分页列表。
 * 【线程约束】所有方法必须在异步线程中调用。
 * 【查询键】以 playerName 作为主要查询维度。
 */
object BackupRepository {

    /** 获取 easy-query 实体查询客户端 */
    private val eq get() = DatabaseManager.entityQuery

    /**
     * 批量插入快照记录（一次备份操作产生多条记录）。
     *
     * @param entities 快照实体列表
     * @return 影响行数
     */
    fun insertBatch(entities: List<BackupSnapshotEntity>): Long {
        if (entities.isEmpty()) return 0
        return eq.insertable(entities).executeRows()
    }

    /**
     * 根据快照批次 ID 查询所有快照记录。
     *
     * @param snapshotId 快照批次 ID
     * @return 该批次下的所有快照记录
     */
    fun findBySnapshotId(snapshotId: String): List<BackupSnapshotEntity> {
        return eq.queryable(BackupSnapshotEntity::class.java)
            .where { it.snapshotId().eq(snapshotId) }
            .toList()
    }

    /**
     * 根据快照批次 ID 查询指定玩家的快照记录。
     * 以 playerName 作为查询条件。
     *
     * @param snapshotId 快照批次 ID
     * @param playerName 玩家名称
     * @return 指定玩家在该批次下的快照记录
     */
    fun findBySnapshotIdAndPlayer(snapshotId: String, playerName: String): List<BackupSnapshotEntity> {
        return eq.queryable(BackupSnapshotEntity::class.java)
            .where { it.snapshotId().eq(snapshotId); it.playerName().eq(playerName) }
            .toList()
    }

    /**
     * 查询所有不重复的快照批次信息（按创建时间倒序）。
     * 返回每个批次的首条记录（用于快照列表展示）。
     *
     * 【实现方式】查询所有记录后在应用层按 snapshotId 去重。
     * 如果快照数量非常大，可考虑优化为原生 SQL GROUP BY 查询。
     *
     * @return 去重后的快照列表（每批次一条）
     */
    fun findDistinctSnapshots(): List<BackupSnapshotEntity> {
        val allSnapshots = eq.queryable(BackupSnapshotEntity::class.java)
            .orderBy { it.createdAt().desc() }
            .toList()
        // 按 snapshotId 去重，保留每个批次的第一条记录
        return allSnapshots.distinctBy { it.snapshotId }
    }

    /**
     * 统计不重复的快照批次数量。
     *
     * @return 批次总数
     */
    fun countDistinctSnapshots(): Int {
        return findDistinctSnapshots().size
    }

    /**
     * 删除指定快照批次的所有记录（用于清理旧快照）。
     *
     * @param snapshotId 快照批次 ID
     * @return 删除的行数
     */
    fun deleteBySnapshotId(snapshotId: String): Long {
        val entities = findBySnapshotId(snapshotId)
        if (entities.isEmpty()) return 0
        return eq.deletable(entities).executeRows()
    }
}
