package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import java.time.LocalDateTime

/**
 * 货币仓库 — 封装 mce_currency 表的所有数据库操作。
 *
 * 【职责】提供 CRUD + 业务查询方法，服务层通过本仓库访问货币数据。
 * 【线程约束】所有方法必须在异步线程中调用（通过 AsyncExecutor）。
 * 【软删除】查询方法默认过滤已删除记录，除非明确要求包含。
 * 【主键】INT 自增主键，标识符（identifier）为唯一业务键。
 */
object CurrencyRepository {

    /** 获取 easy-query 实体查询客户端 */
    private val eq get() = DatabaseManager.entityQuery

    /**
     * 根据 ID 查询货币（不含已删除）。
     *
     * @param id 货币自增 ID
     * @return 货币实体，不存在返回 null
     */
    fun findById(id: Int): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.id().eq(id); it.deleted().eq(false) }
            .firstOrNull()
    }

    /**
     * 根据标识符查询货币（不含已删除）。
     * 标识符在业务上唯一（已删除的除外）。
     *
     * @param identifier 货币标识符（英文小写）
     * @return 货币实体，不存在返回 null
     */
    fun findByIdentifier(identifier: String): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.identifier().eq(identifier.lowercase()); it.deleted().eq(false) }
            .firstOrNull()
    }

    /**
     * 根据标识符查询货币（包含已删除的）。
     * 用于检测标识符是否曾经被使用过。
     *
     * @param identifier 货币标识符
     * @return 货币实体，不存在返回 null
     */
    fun findByIdentifierIncludeDeleted(identifier: String): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.identifier().eq(identifier.lowercase()) }
            .firstOrNull()
    }

    /**
     * 查询所有活跃货币（未删除）。
     *
     * @return 未删除的货币列表
     */
    fun findAllActive(): List<CurrencyEntity> {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false) }
            .toList()
    }

    /**
     * 查询所有已启用且未删除的货币。
     *
     * @return 启用且未删除的货币列表
     */
    fun findAllEnabled(): List<CurrencyEntity> {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false); it.enabled().eq(true) }
            .toList()
    }

    /**
     * 查询主货币（未删除）。
     * 正常情况下应有且仅有一个主货币。
     *
     * @return 主货币实体，不存在返回 null
     */
    fun findPrimary(): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false); it.primary().eq(true) }
            .firstOrNull()
    }

    /**
     * 统计未删除的货币数量。
     *
     * @return 活跃货币数量
     */
    fun countActive(): Long {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false) }
            .count()
    }

    /**
     * 插入新货币。
     * 使用自增主键，插入后实体的 id 字段由数据库自动填充。
     *
     * @param entity 货币实体
     * @return 影响行数
     */
    fun insert(entity: CurrencyEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 更新货币信息。
     * 自动更新 updatedAt 字段。
     *
     * @param entity 货币实体
     * @return 影响行数
     */
    fun update(entity: CurrencyEntity): Long {
        entity.updatedAt = LocalDateTime.now()
        return eq.updatable(entity).executeRows()
    }

    /**
     * 逻辑删除货币（设置 deleted = true）。
     *
     * @param id 货币 ID
     * @return 影响行数
     */
    fun softDelete(id: Int): Long {
        val now = LocalDateTime.now()
        return eq.updatable(CurrencyEntity::class.java)
            .setColumns {
                it.deleted().set(true)
                it.updatedAt().set(now)
            }
            .where {
                it.id().eq(id)
                it.deleted().eq(false)
            }
            .executeRows()
    }

    /**
     * 清除所有货币的主货币标记（用于切换主货币前的重置操作）。
     */
    fun clearAllPrimary() {
        val now = LocalDateTime.now()
        eq.updatable(CurrencyEntity::class.java)
            .setColumns {
                it.primary().set(false)
                it.updatedAt().set(now)
            }
            .where {
                it.deleted().eq(false)
                it.primary().eq(true)
            }
            .executeRows()
    }
}
