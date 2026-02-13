package top.wcpe.mc.plugin.multicurrencyeconomy.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import java.math.BigDecimal

/**
 * 余额变更前事件 — 在余额实际修改**之前**触发（位于 API 模块，供第三方插件监听）。
 *
 * 【用途】允许第三方插件在余额变更前进行拦截（取消）或记录。
 * 【可取消】实现 [Cancellable]；若事件被取消，余额变更将被中止。
 * 【异步标记】构造时 async=true，因为余额变更操作在异步线程发起。
 *             监听此事件的处理器应注意线程安全。
 * 【监听示例】
 * ```kotlin
 * @EventHandler
 * fun onBalanceChange(e: BalanceChangeEvent) {
 *     if (e.playerName == "xxx" && e.type == ChangeType.WITHDRAW) {
 *         e.isCancelled = true // 阻止扣款
 *     }
 * }
 * ```
 */
class BalanceChangeEvent(

    /** 玩家名称（主要标识） */
    val playerName: String,

    /** 玩家 UUID 字符串（记录字段） */
    val playerUuid: String,

    /** 货币标识符 */
    val currencyIdentifier: String,

    /** 变更类型 */
    val type: ChangeType,

    /** 变更金额（正数 = 入账方向） */
    val amount: BigDecimal,

    /** 变更前余额 */
    val balanceBefore: BigDecimal,

    /** 预期变更后余额 */
    val balanceAfter: BigDecimal,

    /** 变更原因（必填） */
    val reason: String,

    /** 操作者标识 */
    val operator: String

) : Event(true), Cancellable {

    /** 是否已取消 */
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
