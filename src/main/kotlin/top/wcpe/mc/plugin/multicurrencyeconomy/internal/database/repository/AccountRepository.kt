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
 * 【唯一约束】(playerName, currencyId) 为逻辑唯一键，所有查询以 playerName 为主。
 * 【主键】INT 自增主键，playerUuid 仅作为记录字段。
 * 【乐观锁】AccountEntity 包含 @Version 版本号字段，
 *          [update] 方法自动校验并递增版本号（返回 0 表示冲突），
 *          [updateForce] 跳过版本检查（用于管理员回滚等强制操作）。
 */
object AccountRepository {

    /** 获取 easy-query 实体查询客户端 */
    private val eq get() = DatabaseManager.entityQuery

    /**
     * 根据玩家名称和货币 ID 查询账户。
     *
     * @param playerName 玩家名称
     * @param currencyId 货币 ID
     * @return 账户实体，不存在返回 null
     */
    fun findByPlayerAndCurrency(playerName: String, currencyId: Int): AccountEntity? {
        return eq.queryable(AccountEntity::class.java)
            .where { it.playerName().eq(playerName); it.currencyId().eq(currencyId) }
            .firstOrNull()
    }

    /**
     * 查询指定玩家名称的所有账户。
     *
     * @param playerName 玩家名称
     * @return 该玩家的所有账户列表
     */
    fun findByPlayer(playerName: String): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .where { it.playerName().eq(playerName) }
            .toList()
    }

    /**
     * 查询指定货币的所有账户。
     *
     * @param currencyId 货币 ID
     * @return 该货币下的所有账户列表
     */
    fun findByCurrency(currencyId: Int): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .where { it.currencyId().eq(currencyId) }
            .toList()
    }

    /**
     * 查询所有账户（用于全量备份）。
     *
     * @return 所有账户列表
     */
    fun findAll(): List<AccountEntity> {
        return eq.queryable(AccountEntity::class.java)
            .toList()
    }

    /**
     * 获取或创建账户。
     * 如果账户不存在，自动创建一个余额为 0 的新账户。
     * 以 playerName + currencyId 作为唯一查询条件。
     *
     * @param playerName  玩家名称（主要查询键）
     * @param playerUuid  玩家 UUID（仅记录用途）
     * @param currencyId  货币 ID
     * @return 已有或新创建的账户实体
     */
    fun getOrCreate(playerName: String, playerUuid: String, currencyId: Int): AccountEntity {
        val existing = findByPlayerAndCurrency(playerName, currencyId)
        if (existing != null) {
            // 更新玩家 UUID（可能改名后 UUID 变化的兼容处理）
            // 实体刚从 DB 读取，版本号是最新的，乐观锁校验可正常通过
            if (existing.playerUuid != playerUuid && playerUuid.isNotEmpty()) {
                existing.playerUuid = playerUuid
                existing.updatedAt = LocalDateTime.now()
                eq.updatable(existing).executeRows()
            }
            return existing
        }
        // 创建新账户 — 自增主键由数据库生成
        val newAccount = AccountEntity().apply {
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
     * 更新账户信息（含余额）— 带乐观锁版本检查。
     * EasyQuery 自动在 WHERE 中追加 version 校验，并在 SET 中将 version + 1。
     * 返回 0 表示版本冲突（数据已被其他事务修改），调用方应重试。
     * 自动更新 updatedAt 字段。
     *
     * @param entity 账户实体
     * @return 影响行数（0 = 版本冲突，1 = 成功）
     */
    fun update(entity: AccountEntity): Long {
        entity.updatedAt = LocalDateTime.now()
        return eq.updatable(entity).executeRows()
    }

    /**
     * 强制更新账户信息 — 先从数据库读取最新版本号再执行更新。
     * 用于管理员回滚等必须成功的强制操作。
     * 通过同步最新版本号确保乐观锁校验通过，而非跳过版本检查。
     * 如果账户在数据库中不存在，返回 0。
     * 自动更新 updatedAt 字段。
     *
     * @param entity 账户实体
     * @return 影响行数（0 = 账户不存在或版本冲突，1 = 成功）
     */
    fun updateForce(entity: AccountEntity): Long {
        entity.updatedAt = LocalDateTime.now()
        // 获取数据库中的最新版本号，确保乐观锁校验通过
        val current = findByPlayerAndCurrency(entity.playerName, entity.currencyId) ?: return 0
        entity.version = current.version
        return eq.updatable(entity).executeRows()
    }

    /**
     * 插入账户。
     *
     * @param entity 账户实体
     * @return 影响行数
     */
    fun insert(entity: AccountEntity): Long {
        return eq.insertable(entity).executeRows()
    }

    /**
     * 设置玩家在指定货币下的个人余额上限。
     * 实体刚从 DB 读取，版本号是最新的，乐观锁校验可正常通过。
     *
     * @param playerName 玩家名称
     * @param currencyId 货币 ID
     * @param maxBalance 余额上限（-1 = 使用货币默认值）
     * @return true = 设置成功
     */
    fun setMaxBalance(playerName: String, currencyId: Int, maxBalance: Long): Boolean {
        val account = findByPlayerAndCurrency(playerName, currencyId) ?: return false
        account.maxBalance = maxBalance
        account.updatedAt = LocalDateTime.now()
        return eq.updatable(account).executeRows() > 0
    }
}
