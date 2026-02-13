package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.TransactionLogEntity

/**
 * 交易流水仓库 — 封装 mce_transaction_log 表的所有数据库操作。
 *
 * 【职责】提供流水插入和查询方法。流水表仅追加（INSERT-only），不支持修改或删除。
 * 【线程约束】所有方法必须在异步线程中调用。
 * 【查询键】以 playerName 作为主要查询维度。
 * 【性能提示】查询大量流水时应使用分页，避免一次加载过多数据。
 */
object TransactionLogRepository {

    /** 获取 easy-query 实体查询客户端 */
    private val eq get() = DatabaseManager.entityQuery

    /**
     * 插入一条流水记录。
     * 自增主键由数据库生成。
     *
     * @param entity 流水实体
     * @return 影响行数
     */
    fun insert(entity: TransactionLogEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 批量插入流水记录（用于批量操作场景，如回滚）。
     *
     * @param entities 流水实体列表
     * @return 影响行数
     */
    fun insertBatch(entities: List<TransactionLogEntity>): Long {
        if (entities.isEmpty()) return 0
        return eq.insertable(entities).executeRows()
    }

    /**
     * 查询指定玩家的流水，按时间倒序，支持分页。
     *
     * @param playerName 玩家名称（主要查询键）
     * @param pageIndex  页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 流水记录列表
     */
    fun findByPlayer(playerName: String, pageIndex: Long, pageSize: Long): List<TransactionLogEntity> {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerName().eq(playerName) }
            .orderBy { it.occurredAt().desc() }
            .toPageResult(pageIndex, pageSize).data
    }

    /**
     * 查询指定玩家在指定货币下的流水，按时间倒序，支持分页。
     *
     * @param playerName 玩家名称（主要查询键）
     * @param currencyId 货币 ID
     * @param pageIndex  页码（从 1 开始）
     * @param pageSize   每页条数
     * @return 流水记录列表
     */
    fun findByPlayerAndCurrency(
        playerName: String,
        currencyId: Int,
        pageIndex: Long,
        pageSize: Long
    ): List<TransactionLogEntity> {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerName().eq(playerName); it.currencyId().eq(currencyId) }
            .orderBy { it.occurredAt().desc() }
            .toPageResult(pageIndex, pageSize).data
    }

    /**
     * 统计指定玩家的流水总数。
     *
     * @param playerName 玩家名称
     * @return 流水总数
     */
    fun countByPlayer(playerName: String): Long {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerName().eq(playerName) }
            .count()
    }

    /**
     * 统计指定玩家在指定货币下的流水总数。
     *
     * @param playerName 玩家名称
     * @param currencyId 货币 ID
     * @return 流水总数
     */
    fun countByPlayerAndCurrency(playerName: String, currencyId: Int): Long {
        return eq.queryable(TransactionLogEntity::class.java)
            .where { it.playerName().eq(playerName); it.currencyId().eq(currencyId) }
            .count()
    }
}
