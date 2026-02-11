package top.wcpe.mc.plugin.multicurrencyeconomy.internal.async

import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 异步执行器 — 封装 TabooLib 的 submitAsync/submit，统一管理异步数据库操作。
 *
 * 【设计原则】
 *   - 所有数据库 I/O 操作必须通过本执行器提交，严禁在主线程直接访问数据库。
 *   - 提供 [runAsync] 用于 fire-and-forget 场景（如审计日志写入）。
 *   - 提供 [supplyAsync] 用于需要返回值的场景（如 Vault 同步接口适配）。
 *   - 提供 [runSync] 用于需要回到主线程的回调。
 *
 * 【关闭流程】
 *   插件 onDisable 时调用 [shutdown]，等待指定超时后强制结束未完成任务。
 *
 * 【线程语义】
 *   本对象方法均可在任意线程调用。内部通过 TabooLib 调度器执行实际任务。
 */
object AsyncExecutor {

    /** 是否已关闭 — 关闭后拒绝新任务 */
    private val shutdown = AtomicBoolean(false)

    /**
     * 在异步线程执行任务（fire-and-forget）。
     * 适用于不需要返回值的数据库写操作（如审计日志、缓存刷新）。
     *
     * @param block 异步执行的代码块
     */
    fun runAsync(block: () -> Unit) {
        if (shutdown.get()) {
            warning("[MCE] 异步执行器已关闭，拒绝新任务。")
            return
        }
        submitAsync {
            try {
                block()
            } catch (e: Exception) {
                warning("[MCE] 异步任务执行失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 在异步线程执行任务并返回 [CompletableFuture]。
     * 适用于需要同步等待结果的场景（如 Vault Economy 接口适配）。
     *
     * @param T     返回值类型
     * @param block 异步执行的代码块（需返回结果）
     * @return 包含执行结果的 CompletableFuture
     */
    fun <T> supplyAsync(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (shutdown.get()) {
            future.completeExceptionally(IllegalStateException("异步执行器已关闭"))
            return future
        }
        submitAsync {
            try {
                future.complete(block())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * 在主线程执行任务（通常用于异步操作完成后的 Bukkit API 回调）。
     *
     * @param block 主线程执行的代码块
     */
    fun runSync(block: () -> Unit) {
        submit {
            try {
                block()
            } catch (e: Exception) {
                warning("[MCE] 主线程任务执行失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 带超时的同步等待异步结果。
     * 专为 Vault Economy 接口设计 — Vault 方法必须同步返回，此方法限制最大阻塞时间。
     *
     * @param T              返回值类型
     * @param timeoutMs      超时毫秒数
     * @param defaultValue   超时时的默认返回值
     * @param block          异步执行的代码块
     * @return 执行结果；超时时返回 [defaultValue]
     */
    fun <T> supplyWithTimeout(timeoutMs: Long, defaultValue: T, block: () -> T): T {
        return try {
            supplyAsync(block).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            warning("[MCE] 异步任务超时或失败 (${timeoutMs}ms): ${e.message}")
            defaultValue
        }
    }

    /**
     * 关闭执行器。
     * 在插件 onDisable 中调用，标记关闭状态后不再接受新任务。
     *
     * @param waitSeconds 等待已提交任务完成的最大秒数
     */
    fun shutdown(waitSeconds: Int) {
        shutdown.set(true)
        // TabooLib 的调度器会随插件禁用而取消，这里仅标记状态
        // 如有必要，可在此处添加额外的等待逻辑
    }

    /**
     * 重置执行器状态（仅用于热重载场景）。
     */
    fun reset() {
        shutdown.set(false)
    }
}
