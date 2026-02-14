package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.BackupSnapshotEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.AccountRepository
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.BackupRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 备份与回滚服务 — 负责全量快照的创建与余额恢复。
 *
 * 【备份策略】
 *   每次备份创建一个 snapshotId（UUID），遍历所有账户生成快照记录。
 *   快照数量超过 [MainConfig.maxSnapshots] 时，自动删除最旧的快照。
 *
 * 【回滚策略】
 *   回滚操作将指定快照中的余额恢复到对应玩家的账户。
 *   支持全量回滚（所有玩家）和单人回滚（指定玩家名称）。
 *   每次余额恢复都会生成 ROLLBACK 类型的审计流水。
 *
 * 【查询键】以 playerName 作为主要查询维度。
 * 【线程约束】所有方法必须在异步线程中调用。
 */
object BackupService {

    // ======================== 备份方法 ========================

    /**
     * 创建全量备份快照。
     * 自增主键由数据库生成，snapshotId 仍使用 UUID 作为批次标识。
     *
     * @param memo 备份备注（管理员提供的说明文字）
     * @return 快照批次 ID，失败返回 null
     */
    fun createBackup(memo: String = ""): String? {
        if (!DatabaseManager.ready) return null

        val snapshotId = UUID.randomUUID().toString()
        val allAccounts = AccountRepository.findAll()

        if (allAccounts.isEmpty()) {
            warning("[MCE] 没有任何账户数据，无法创建备份。")
            return null
        }

        val now = LocalDateTime.now()
        val snapshots = allAccounts.map { account ->
            BackupSnapshotEntity().apply {
                this.snapshotId = snapshotId
                this.playerUuid = account.playerUuid
                this.playerName = account.playerName
                this.currencyId = account.currencyId
                this.balance = account.balance
                this.memo = memo
                this.createdAt = now
            }
        }

        BackupRepository.insertBatch(snapshots)
        info("[MCE] 备份快照创建成功: $snapshotId (共 ${snapshots.size} 条账户记录)")

        // 清理旧快照
        cleanupOldSnapshots()

        return snapshotId
    }

    // ======================== 回滚方法 ========================

    /**
     * 从快照回滚 — 全量恢复所有玩家的余额。
     *
     * @param snapshotId 快照批次 ID
     * @return true = 回滚成功
     */
    fun rollback(snapshotId: String): Boolean {
        if (!DatabaseManager.ready) return false

        val snapshots = BackupRepository.findBySnapshotId(snapshotId)
        if (snapshots.isEmpty()) {
            warning("[MCE] 找不到快照: $snapshotId")
            return false
        }

        return executeRollback(snapshots)
    }

    /**
     * 从快照回滚 — 仅恢复指定玩家的余额。
     * 以 playerName 作为查询条件。
     *
     * @param snapshotId 快照批次 ID
     * @param playerName 目标玩家名称
     * @return true = 回滚成功
     */
    fun rollbackPlayer(snapshotId: String, playerName: String): Boolean {
        if (!DatabaseManager.ready) return false

        val snapshots = BackupRepository.findBySnapshotIdAndPlayer(snapshotId, playerName)
        if (snapshots.isEmpty()) {
            warning("[MCE] 在快照 $snapshotId 中找不到玩家 $playerName 的数据。")
            return false
        }

        return executeRollback(snapshots)
    }

    // ======================== 查询方法 ========================

    /**
     * 获取所有快照批次列表（用于管理员查看）。
     *
     * @return 快照列表
     */
    fun listSnapshots(): List<BackupSnapshotEntity> {
        if (!DatabaseManager.ready) return emptyList()
        return BackupRepository.findDistinctSnapshots()
    }

    // ======================== 内部方法 ========================

    /**
     * 执行回滚操作 — 将快照中的余额恢复到对应账户。
     * 使用 [AccountRepository.updateForce] 跳过乐观锁检查，
     * 因为回滚是管理员强制操作，必须无条件成功覆盖当前余额。
     *
     * @param snapshots 需要回滚的快照列表
     * @return true = 回滚成功
     */
    private fun executeRollback(snapshots: List<BackupSnapshotEntity>): Boolean {
        try {
            snapshots.forEach { snapshot ->
                val currency = CurrencyService.getById(snapshot.currencyId) ?: return@forEach
                val account = AccountRepository.getOrCreate(
                    snapshot.playerName, snapshot.playerUuid, snapshot.currencyId
                )
                val oldBalance = account.balance
                val newBalance = snapshot.balance

                // 更新账户余额（强制写入，跳过乐观锁）
                account.balance = newBalance
                AccountRepository.updateForce(account)

                // 写入回滚审计流水
                AuditService.writeLog(
                    playerName = snapshot.playerName,
                    playerUuid = snapshot.playerUuid,
                    currencyId = snapshot.currencyId,
                    type = ChangeType.ROLLBACK,
                    amount = (newBalance - oldBalance).abs(),
                    balanceBefore = oldBalance,
                    balanceAfter = newBalance,
                    reason = "rollback:${snapshot.snapshotId}",
                    operator = "SYSTEM"
                )

                // 更新缓存
                AccountService.loadPlayerBalances(snapshot.playerName, snapshot.playerUuid)
            }
            info("[MCE] 回滚完成，共恢复 ${snapshots.size} 条账户记录。")
            return true
        } catch (e: Exception) {
            warning("[MCE] 回滚执行失败: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 清理旧快照 — 保留最新的 [MainConfig.maxSnapshots] 个快照。
     */
    private fun cleanupOldSnapshots() {
        val allSnapshots = BackupRepository.findDistinctSnapshots()
        val maxSnapshots = MainConfig.maxSnapshots
        if (allSnapshots.size <= maxSnapshots) return

        // 按创建时间降序排列，删除超出部分
        val toDelete = allSnapshots.drop(maxSnapshots)
        toDelete.forEach { snapshot ->
            BackupRepository.deleteBySnapshotId(snapshot.snapshotId)
        }
        info("[MCE] 已清理 ${toDelete.size} 个旧快照。")
    }
}
