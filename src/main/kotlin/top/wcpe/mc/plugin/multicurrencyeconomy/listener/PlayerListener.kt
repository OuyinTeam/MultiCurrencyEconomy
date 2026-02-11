package top.wcpe.mc.plugin.multicurrencyeconomy.listener

import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submitAsync
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService

/**
 * 玩家事件监听器 — 处理玩家加入/退出时的缓存加载与卸载。
 *
 * 【加入事件】
 *   玩家加入服务器时，在异步线程中加载该玩家在所有已启用货币下的余额到缓存。
 *   这确保了后续的 Vault 调用和余额查询可以从缓存中零延迟读取。
 *
 * 【退出事件】
 *   玩家退出时不立即清除缓存（保留以供离线查询）。
 *   如需释放内存，可取消注释 unloadPlayer 调用。
 *
 * 【线程语义】
 *   - 事件回调在主线程触发。
 *   - 数据库加载操作通过 submitAsync 在异步线程执行。
 */
object PlayerListener {

    /**
     * 玩家加入事件 — 异步加载余额到缓存。
     */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!DatabaseManager.ready) return
        val player = event.player
        val uuid = player.uniqueId.toString()
        val name = player.name
        submitAsync {
            AccountService.loadPlayerBalances(uuid, name)
        }
    }

    /**
     * 玩家退出事件。
     * 当前策略：保留缓存（离线玩家仍可被查询余额）。
     * 如需释放内存，取消下方注释。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // 可选：清除离线玩家缓存以释放内存
        // AccountService.unloadPlayer(event.player.uniqueId.toString())
    }
}
