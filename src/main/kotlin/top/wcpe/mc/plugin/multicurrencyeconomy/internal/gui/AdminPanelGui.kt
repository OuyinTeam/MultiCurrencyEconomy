package top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui

import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService

/**
 * 管理员面板 GUI — 提供货币管理与系统操作的可视化入口。
 *
 * 【布局】使用 TabooLib [Chest] 构建 3 行菜单：
 *   - 货币列表按钮 → 展示所有货币状态
 *   - 备份管理按钮 → 快捷备份/查看快照
 *   - 系统信息 → 显示就绪状态与统计
 *
 * 【权限】需要 mce.admin.gui 权限。
 * 【线程语义】在主线程调用。
 */
object AdminPanelGui {

    /** GUI 标题 */
    private const val TITLE = "§4§l管理员面板"

    /**
     * 为管理员打开管理面板。
     *
     * @param player 管理员玩家
     */
    fun open(player: Player) {
        player.openMenu<Chest>(TITLE) {
            rows(3)

            // 货币管理
            set(11, buildItem(XMaterial.GOLD_BLOCK) {
                this.name = "§e货币管理"
                this.lore.addAll(
                    listOf(
                        "",
                        "§7当前货币数量: §f${CurrencyService.getAllCurrencies().size}",
                        "§7主货币: §f${CurrencyService.getPrimary()?.name ?: "未设置"}",
                        "",
                        "§a点击查看货币列表"
                    )
                )
            }) {
                // 点击打开货币列表子菜单
                openCurrencyList(player)
            }

            // 备份管理
            set(13, buildItem(XMaterial.CHEST) {
                this.name = "§e备份管理"
                this.lore.addAll(
                    listOf(
                        "",
                        "§7使用命令管理备份:",
                        "§f/mce backup create [memo]",
                        "§f/mce rollback <id> [player]",
                        "",
                        "§7点击关闭菜单"
                    )
                )
            }) {
                player.closeInventory()
                player.sendMessage("§7请使用命令进行备份管理操作。")
            }

            // 系统信息
            set(15, buildItem(XMaterial.REDSTONE_TORCH) {
                this.name = "§e系统状态"
                this.lore.addAll(
                    listOf(
                        "",
                        "§7数据库就绪: §f${top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager.ready}",
                        "§7货币数量: §f${CurrencyService.getAllCurrencies().size}",
                        "§7启用货币: §f${CurrencyService.getActiveCurrencies().size}",
                        "",
                        "§7版本: §f1.0.0-SNAPSHOT"
                    )
                )
            })

            // 关闭按钮
            set(22, buildItem(XMaterial.BARRIER) {
                this.name = "§c关闭"
            }) {
                player.closeInventory()
            }
        }
    }

    /**
     * 打开货币列表子菜单 — 展示所有货币的详细信息。
     */
    private fun openCurrencyList(player: Player) {
        val currencies = CurrencyService.getAllCurrencies()

        player.openMenu<Chest>("§6货币列表") {
            rows(Math.min(6, (currencies.size / 7) + 2).coerceAtLeast(3))

            currencies.forEachIndexed { index, currency ->
                val slot = 10 + (index / 7) * 9 + (index % 7)
                if (slot < rows * 9) {
                    val material = if (currency.primary) XMaterial.DIAMOND else
                        if (currency.enabled) XMaterial.GOLD_INGOT else XMaterial.IRON_INGOT

                    set(slot, buildItem(material) {
                        this.name = "§e${currency.name} §7(${currency.identifier})"
                        this.lore.addAll(
                            listOf(
                                "",
                                "§7ID: §f${currency.id}",
                                "§7符号: §f${currency.symbol}",
                                "§7精度: §f${currency.precision}",
                                "§7上限: §f${if (currency.defaultMaxBalance < 0) "不限" else currency.defaultMaxBalance.toString()}",
                                "§7主货币: ${if (currency.primary) "§a是" else "§c否"}",
                                "§7状态: ${if (currency.enabled) "§a启用" else "§c禁用"}"
                            )
                        )
                    })
                }
            }

            // 返回按钮
            set(rows * 9 - 5, buildItem(XMaterial.ARROW) {
                this.name = "§e返回"
            }) {
                open(player)
            }
        }
    }
}
