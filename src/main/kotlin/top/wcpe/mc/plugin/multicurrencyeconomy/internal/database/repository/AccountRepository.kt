package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.AccountEntity
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 账户仓库 — 封装 mce_account 表的所有数据库操作。
 *
 * 【职责】提供账户的 CRUD、余额查询 / 更新方法。
 * 【线程约束】所有方法必须在异步线程中调用。
 * 【唯一约束】(playerUuid, currencyId) 应为逻辑唯一键。
 */
object AccountRepository {

    private val eq get() = DatabaseManager.entityQuery

    /**
     * 根据玩家 UUID 和货币 ID 查询账户。
     */
    fun findByPlayerAndCurrency(playerUuid: String, currencyId: String): AccountEntity? {
        return eq.queryable(AccountEntity::class.java)
            .where { it.playerUuid().eq(playerUuid); it.currencyId().eq(currencyId) }
            .firstOrNull()
    }

    /**
     * 查询指定玩家的所有账户。
     */
    fun findByPlayer(playerUuid: String): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .where { it.playerUuid().eq(playerUuid) }
            .toList()
    }

    /**
     * 查询指定货币的所有账户。
     */
    fun findByCurrency(currencyId: String): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .where { it.currencyId().eq(currencyId) }
            .toList()
    }

    /**
     * 查询所有账户（用于全量备份）。
     */
    fun findAll(): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .toList()
    }

    /**
     * 获取或创建账户。
     * 如果账户不存在，自动创建一个余额为 0 的新账户。
     *
     * @param playerUuid  玩家 UUID
     * @param playerName  玩家名称
     * @param currencyId  货币 ID
     * @return 已有或新创建的账户实体
     */
    fun getOrCreate(playerUuid: String, playerName: String, currencyId: String): AccountEntity {
        val existing = findByPlayerAndCurrency(playerUuid, currencyId)
        if (existing != null) {
            // 更新玩家名称（可能改名）
            if (existing.playerName != playerName) {
                existing.playerName = playerName
                existing.updatedAt = LocalDateTime.now()
                eq.updatable(existing).executeRows()
            }
            return existing
        }
        // 创建新账户
        val newAccount = AccountEntity().apply {
            this.id = java.util.UUID.randomUUID().toString()
            this.playerUuid = playerUuid
            this.playerName = playerName
            this.currencyId = currencyId
            this.balance = BigDecimal.ZERO
            this.maxBalance = -1L
            this.createdAt = LocalDateTime.now()
            this.updatedAt = LocalDateTime.now()
        }
        eq.insertable(newAccount).executeRows()
        return newAccount
    }

    /**
     * 更新账户信息（含余额）。
     */
    fun update(entity: AccountEntity): Long {
        entity.updatedAt = LocalDateTime.now()
        return eq.updatable(entity).executeRows()
    }

    /**
     * 插入账户。
     */
    fun insert(entity: AccountEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 设置玩家在指定货币下的个人余额上限。
     */
    fun setMaxBalance(playerUuid: String, currencyId: String, maxBalance: Long): Boolean {
        val account = findByPlayerAndCurrency(playerUuid, currencyId) ?: return false
        account.maxBalance = maxBalance
        account.updatedAt = LocalDateTime.now()
        return eq.updatable(account).executeRows() > 0
    }
}
