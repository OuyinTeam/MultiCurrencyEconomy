package top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui

import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil

/**
 * 玩家钱包 GUI — 展示玩家在所有已启用货币下的余额信息。
 *
 * 【布局】使用 TabooLib [PageableChest] 实现分页：
 *   - 中央区域展示货币卡片（每种货币一个物品）
 *   - 底部导航栏：上一页 / 下一页 / 关闭
 *
 * 【物品展示】
 *   每种货币使用 [buildItem] 构建展示物品：
 *   - 名称 = 货币显示名称
 *   - Lore = 余额、符号、精度信息
 *
 * 【线程语义】
 *   [open] 在主线程调用（Bukkit GUI 操作必须在主线程）。
 *   余额数据来自内存缓存，无异步 I/O。
 */
object PlayerWalletGui {

    /** GUI 标题 */
    private const val TITLE = "§6§l我的钱包"

    /**
     * 为指定玩家打开钱包 GUI。
     *
     * @param player 目标玩家
     */
    fun open(player: Player) {
        val currencies = CurrencyService.getActiveCurrencies()
        val uuid = player.uniqueId.toString()

        // 构建每种货币的展示数据
        val items = currencies.map { currency ->
            val balance = AccountService.getBalanceByCurrencyId(uuid, currency.id)
            val formatted = CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)

            buildItem(XMaterial.GOLD_INGOT) {
                this.name = "§e${currency.name}"
                this.lore.addAll(
                    listOf(
                        "",
                        "§7标识: §f${currency.identifier}",
                        "§7符号: §f${currency.symbol}",
                        "§7精度: §f${currency.precision} 位小数",
                        "",
                        "§7余额: §f$formatted",
                        "",
                        if (currency.primary) "§a✦ 主货币" else "§7普通货币"
                    )
                )
            } to currency
        }

        player.openMenu<PageableChest<Pair<org.bukkit.inventory.ItemStack, top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.entity.CurrencyEntity>>>(TITLE) {
            rows(6)

            // 定义可用槽位（中央 4x7 = 28 格）
            val slots = mutableListOf<Int>()
            for (row in 1..4) {
                for (col in 1..7) {
                    slots.add(row * 9 + col)
                }
            }
            this.slots(slots)

            // 设置元素列表
            elements { items }

            // 每个元素的展示物品
            onGenerate { _, element, _, _ ->
                element.first
            }

            // 点击事件（暂不处理，仅展示）
            onClick { _, _ -> }

            // 上一页按钮（左下角）
            setPreviousPage(45) { _, hasPreviousPage ->
                if (hasPreviousPage) {
                    buildItem(XMaterial.ARROW) { this.name = "§e上一页" }
                } else {
                    buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { this.name = "§7已是第一页" }
                }
            }

            // 下一页按钮（右下角）
            setNextPage(53) { _, hasNextPage ->
                if (hasNextPage) {
                    buildItem(XMaterial.ARROW) { this.name = "§e下一页" }
                } else {
                    buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { this.name = "§7已是最后一页" }
                }
            }

            // 边框装饰
            set(49, buildItem(XMaterial.BARRIER) {
                this.name = "§c关闭"
            }) {
                player.closeInventory()
            }
        }
    }
}
