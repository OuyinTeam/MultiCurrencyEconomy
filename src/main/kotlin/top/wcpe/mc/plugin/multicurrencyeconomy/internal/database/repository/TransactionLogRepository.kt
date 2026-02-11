package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.TransactionLogEntity

/**
 * 交易流水仓库 — 封装 mce_transaction_log 表的所有数据库操作。
 *
 * 【职责】提供流水插入和查询方法。流水表仅追加（INSERT-only），不支持修改或删除。
 * 【线程约束】所有方法必须在异步线程中调用。
 * 【性能提示】查询大量流水时应使用分页，避免一次加载过多数据。
 */
object TransactionLogRepository {

    private val eq get() = DatabaseManager.entityQuery

    /**
     * 插入一条流水记录。
     */
    fun insert(entity: TransactionLogEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 批量插入流水记录（用于批量操作场景，如回滚）。
     */
    fun insertBatch(entities: List<TransactionLogEntity>): Long {
        if (entities.isEmpty()) return 0
        return eq.insertable(entities).executeRows()
    }

    /**
     * 查询指定玩家的流水，按时间倒序，支持分页。
     *
     * @param playerUuid 玩家 UUID
     * @param pageIndex  页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 流水记录列表
     */
    fun findByPlayer(playerUuid: String, pageIndex: Long, pageSize: Long): List<TransactionLogEntity> {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerUuid().eq(playerUuid) }
            .orderBy { it.occurredAt().desc() }
            .toPageResult(pageIndex, pageSize).data
    }

    /**
     * 查询指定玩家在指定货币下的流水，按时间倒序，支持分页。
     *
     * @param playerUuid 玩家 UUID
     * @param currencyId 货币 ID
     * @param pageIndex  页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 流水记录列表
     */
    fun findByPlayerAndCurrency(
        playerUuid: String,
        currencyId: String,
        pageIndex: Long,
        pageSize: Long
    ): List<TransactionLogEntity> {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerUuid().eq(playerUuid); it.currencyId().eq(currencyId) }
            .orderBy { it.occurredAt().desc() }
            .toPageResult(pageIndex, pageSize).data
    }

    /**
     * 统计指定玩家的流水总数。
     */
    fun countByPlayer(playerUuid: String): Long {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerUuid().eq(playerUuid) }
            .count()
    }

    /**
     * 统计指定玩家在指定货币下的流水总数。
     */
    fun countByPlayerAndCurrency(playerUuid: String, currencyId: String): Long {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerUuid().eq(playerUuid); it.currencyId().eq(currencyId) }
            .count()
    }
}
