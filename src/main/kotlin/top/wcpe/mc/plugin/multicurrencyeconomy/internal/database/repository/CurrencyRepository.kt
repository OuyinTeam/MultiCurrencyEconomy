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
 */
object CurrencyRepository {

    private val eq get() = DatabaseManager.entityQuery

    /**
     * 根据 ID 查询货币（不含已删除）。
     */
    fun findById(id: String): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.id().eq(id); it.deleted().eq(false) }
            .firstOrNull()
    }

    /**
     * 根据标识符查询货币（不含已删除）。
     * 标识符在业务上唯一（已删除的除外）。
     */
    fun findByIdentifier(identifier: String): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.identifier().eq(identifier.lowercase()); it.deleted().eq(false) }
            .firstOrNull()
    }

    /**
     * 根据标识符查询货币（包含已删除的）。
     * 用于检测标识符是否曾经被使用过。
     */
    fun findByIdentifierIncludeDeleted(identifier: String): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.identifier().eq(identifier.lowercase()) }
            .firstOrNull()
    }

    /**
     * 查询所有活跃货币（未删除）。
     */
    fun findAllActive(): List<CurrencyEntity> {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false) }
            .toList()
    }

    /**
     * 查询所有已启用且未删除的货币。
     */
    fun findAllEnabled(): List<CurrencyEntity> {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false); it.enabled().eq(true) }
            .toList()
    }

    /**
     * 查询主货币（未删除）。
     * 正常情况下应有且仅有一个主货币。
     */
    fun findPrimary(): CurrencyEntity? {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false); it.primary().eq(true) }
            .firstOrNull()
    }

    /**
     * 统计未删除的货币数量。
     */
    fun countActive(): Long {
        return eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false) }
            .count()
    }

    /**
     * 插入新货币。
     */
    fun insert(entity: CurrencyEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 更新货币信息。
     */
    fun update(entity: CurrencyEntity): Long {
        entity.updatedAt = LocalDateTime.now()
        return eq.updatable(entity).executeRows()
    }

    /**
     * 逻辑删除货币（设置 deleted = true）。
     */
    fun softDelete(id: String): Long {
        val entity = findById(id) ?: return 0
        entity.deleted = true
        entity.updatedAt = LocalDateTime.now()
        return eq.updatable(entity).executeRows()
    }

    /**
     * 清除所有货币的主货币标记（用于切换主货币前的重置操作）。
     */
    fun clearAllPrimary() {
        val primaries = eq.queryable(CurrencyEntity::class.java)
            .where { it.deleted().eq(false); it.primary().eq(true) }
            .toList()
        primaries.forEach {
            it.primary = false
            it.updatedAt = LocalDateTime.now()
        }
        if (primaries.isNotEmpty()) {
            eq.updatable(primaries).executeRows()
        }
    }
}
