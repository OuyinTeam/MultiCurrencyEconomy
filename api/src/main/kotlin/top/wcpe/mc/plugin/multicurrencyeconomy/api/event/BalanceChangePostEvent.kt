package top.wcpe.mc.plugin.multicurrencyeconomy.api.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import top.wcpe.mc.plugin.multicurrencyeconomy.api.model.ChangeType
import java.math.BigDecimal

/**
 * 余额变更后事件 — 在余额实际修改**完成后**触发（位于 API 模块，供第三方插件监听）。
 *
 * 【用途】允许第三方插件在余额变更后执行后续逻辑（如记分板更新、称号检测等）。
 * 【不可取消】事件已完成，不支持回滚。
 * 【异步标记】构造时 async=true，因为余额变更操作在异步线程完成。
 * 【监听示例】
 * ```kotlin
 * @EventHandler
 * fun onBalanceChanged(e: BalanceChangePostEvent) {
 *     // 余额变更完成后的逻辑
 * }
 * ```
 */
class BalanceChangePostEvent(

    /** 玩家名称（主要标识） */
    val playerName: String,

    /** 玩家 UUID 字符串（记录字段） */
    val playerUuid: String,

    /** 货币标识符 */
    val currencyIdentifier: String,

    /** 变更类型 */
    val type: ChangeType,

    /** 变更金额 */
    val amount: BigDecimal,

    /** 变更前余额 */
    val balanceBefore: BigDecimal,

    /** 变更后余额 */
    val balanceAfter: BigDecimal,

    /** 变更原因 */
    val reason: String,

    /** 操作者标识 */
    val operator: String

) : Event(true) {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
