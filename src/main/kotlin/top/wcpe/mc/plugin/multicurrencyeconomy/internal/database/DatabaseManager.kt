package top.wcpe.mc.plugin.multicurrencyeconomy.internal.database

import com.easy.query.api.proxy.client.EasyEntityQuery
import com.easy.query.core.api.client.EasyQueryClient
import taboolib.common.platform.function.info
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import top.wcpe.mc.plugin.corelib.api.CoreLibApi
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.AccountEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.BackupSnapshotEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.TransactionLogEntity

/**
 * 数据库管理器 — 负责数据源获取、Code First 建表 / 迁移、就绪状态管理。
 *
 * 【初始化流程】
 *   1. [initialize] 在异步线程中调用，从 CoreLib 获取 EasyEntityQuery 实例。
 *   2. 执行 Code First syncTableCommand，自动创建 / 迁移四张表。
 *   3. 设置 [ready] = true，服务层开始正常工作。
 *
 * 【就绪门控】
 *   所有服务层方法在 [ready] = false 时返回失败 / 降级结果。
 *   这保证了在数据库初始化完成前，Vault/命令/GUI 不会触发无效 SQL。
 *
 * 【线程语义】
 *   - [initialize] 必须在异步线程中调用。
 *   - [ready] 使用 @Volatile 保证可见性。
 *   - [entityQuery] 和 [queryClient] 在初始化完成后不可变，线程安全。
 */
object DatabaseManager {

    /** 数据库就绪状态 — 服务层在此为 false 时返回降级结果 */
    @Volatile
    var ready: Boolean = false
        private set

    /** easy-query 实体查询客户端（代理模式） — 初始化后不可变 */
    lateinit var entityQuery: EasyEntityQuery
        private set

    /** easy-query 原始查询客户端 — 初始化后不可变 */
    lateinit var queryClient: EasyQueryClient
        private set

    /**
     * 需要 Code First 同步的实体类列表。
     * 新增实体时在此追加即可。
     */
    private val entityClasses: List<Class<*>> = listOf(
        CurrencyEntity::class.java,
        AccountEntity::class.java,
        TransactionLogEntity::class.java,
        BackupSnapshotEntity::class.java
    )

    /**
     * 初始化数据库。
     *
     * 【调用时机】插件 onEnable 阶段，在异步线程中执行。
     * 【执行步骤】
     *   1. 从 CoreLib 获取数据源
     *   2. 执行 Code First 建表 / 迁移
     *   3. 标记就绪
     *
     * @throws Exception 数据源获取或建表失败时抛出
     */
    fun initialize() {
        val key = MainConfig.datasourceKey
        info("[MCE] 正在初始化数据库，数据源: $key")

        try {
            // 从 CoreLib 获取 easy-query 客户端
            entityQuery = CoreLibApi.easyEntityQuery(key)
            queryClient = CoreLibApi.easyQueryClient(key)
            info("[MCE] 成功获取 CoreLib 数据源: $key")

            // Code First — 同步表结构
            syncTables()

            // 标记就绪
            ready = true
            info("[MCE] 数据库初始化完成，服务就绪。")
        } catch (e: Exception) {
            severe("[MCE] 数据库初始化失败: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 执行 Code First 表结构同步。
     *
     * 使用 easy-query 的 DatabaseCodeFirst API，对比实体类与数据库表结构，
     * 自动创建缺失的表或列。在事务中执行以保证原子性。
     */
    private fun syncTables() {
        info("[MCE] 开始 Code First 表结构同步...")
        val codeFirst = queryClient.databaseCodeFirst
        val commands = codeFirst.syncTableCommand(entityClasses)
        commands.executeWithTransaction { arg ->
            arg.commit()
        }
        info("[MCE] Code First 表结构同步完成。")
    }

    /**
     * 关闭数据库管理器。
     * 在插件 onDisable 时调用，标记为未就绪。
     */
    fun shutdown() {
        ready = false
        info("[MCE] 数据库管理器已关闭。")
    }
}
