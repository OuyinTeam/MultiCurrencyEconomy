package top.wcpe.mc.plugin.multicurrencyeconomy.internal.service

import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.TransactionRecord
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.TransactionLogEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.repository.TransactionLogRepository
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 审计服务 — 负责交易流水的写入与查询。
 *
 * 【设计原则】
 *   - 流水表仅追加（INSERT-only），不可修改或删除，保证审计完整性。
 *   - 每次余额变更都必须生成一条流水记录（无论来源是命令、Vault 还是 API）。
 *   - 所有写入操作应在异步线程中调用（由 AccountService 中的 AsyncExecutor.runAsync 保证）。
 *
 * 【查询键】以 playerName 作为主要查询维度，不再以 playerUuid 查询。
 * 【ID 类型】流水 ID 为 BIGINT 自增主键。
 *
 * 【线程约束】
 *   - [writeLog] 在异步线程中调用。
 *   - [queryLogs]、[queryLogsByPlayerAndCurrency] 在异步线程中调用。
 */
object AuditService {

    // ======================== 写入方法 ========================

    /**
     * 写入一条交易流水记录。
     * 使用自增主键，无需手动生成 UUID。
     *
     * @param playerName    玩家名称（主要标识）
     * @param playerUuid    玩家 UUID（记录字段）
     * @param currencyId    货币 ID（INT 自增主键）
     * @param type          变更类型
     * @param amount        变更金额
     * @param balanceBefore 变更前余额
     * @param balanceAfter  变更后余额
     * @param reason        变更原因（必填）
     * @param operator      操作者标识
     */
    fun writeLog(
        playerName: String,
        playerUuid: String,
        currencyId: Int,
        type: ChangeType,
        amount: BigDecimal,
        balanceBefore: BigDecimal,
        balanceAfter: BigDecimal,
        reason: String,
        operator: String
    ) {
        val entity = TransactionLogEntity().apply {
            this.playerName = playerName
            this.playerUuid = playerUuid
            this.currencyId = currencyId
            this.type = type.name
            this.amount = amount
            this.balanceBefore = balanceBefore
            this.balanceAfter = balanceAfter
            this.reason = reason
            this.operator = operator
            this.occurredAt = LocalDateTime.now()
        }
        TransactionLogRepository.insert(entity)
    }

    // ======================== 查询方法 ========================

    /**
     * 查询指定玩家的流水（分页）。
     *
     * @param playerName 玩家名称（主要查询键）
     * @param page       页码（从 1 开始）
     * @param pageSize   每页条数（默认 10）
     * @return 流水记录列表（DTO）
     */
    fun queryLogs(playerName: String, page: Long = 1, pageSize: Long = 10): List<TransactionRecord> {
        if (!DatabaseManager.ready) return emptyList()
        val entities = TransactionLogRepository.findByPlayer(playerName, page, pageSize)
        return entities.map { toRecord(it) }
    }

    /**
     * 查询指定玩家在指定货币下的流水（分页）。
     *
     * @param playerName         玩家名称（主要查询键）
     * @param currencyIdentifier 货币标识符
     * @param page               页码（从 1 开始）
     * @param pageSize           每页条数
     * @return 流水记录列表（DTO）
     */
    fun queryLogsByPlayerAndCurrency(
        playerName: String,
        currencyIdentifier: String,
        page: Long = 1,
        pageSize: Long = 10
    ): List<TransactionRecord> {
        if (!DatabaseManager.ready) return emptyList()
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return emptyList()
        val entities = TransactionLogRepository.findByPlayerAndCurrency(
            playerName, currency.id, page, pageSize
        )
        return entities.map { toRecord(it) }
    }

    /**
     * 获取指定玩家的流水总数。
     *
     * @param playerName 玩家名称
     * @return 流水总数
     */
    fun countLogs(playerName: String): Long {
        if (!DatabaseManager.ready) return 0
        return TransactionLogRepository.countByPlayer(playerName)
    }

    /**
     * 获取指定玩家在指定货币下的流水总数。
     *
     * @param playerName         玩家名称
     * @param currencyIdentifier 货币标识符
     * @return 流水总数
     */
    fun countLogsByPlayerAndCurrency(playerName: String, currencyIdentifier: String): Long {
        if (!DatabaseManager.ready) return 0
        val currency = CurrencyService.getByIdentifier(currencyIdentifier) ?: return 0
        return TransactionLogRepository.countByPlayerAndCurrency(playerName, currency.id)
    }

    // ======================== 转换方法 ========================

    /**
     * 将流水实体转换为 API DTO。
     *
     * @param entity 流水实体
     * @return TransactionRecord DTO
     */
    private fun toRecord(entity: TransactionLogEntity): TransactionRecord {
        val currency = CurrencyService.getById(entity.currencyId)
        return TransactionRecord(
            id = entity.id,
            playerName = entity.playerName,
            playerUuid = entity.playerUuid,
            currencyIdentifier = currency?.identifier ?: "unknown",
            type = runCatching { ChangeType.valueOf(entity.type) }.getOrDefault(ChangeType.SET),
            amount = entity.amount,
            balanceBefore = entity.balanceBefore,
            balanceAfter = entity.balanceAfter,
            reason = entity.reason,
            operator = entity.operator,
            occurredAt = entity.occurredAt
        )
    }
}
