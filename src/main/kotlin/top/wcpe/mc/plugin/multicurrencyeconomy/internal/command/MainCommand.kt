@file:Suppress("unused")

package top.wcpe.mc.plugin.multicurrencyeconomy.internal.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.submitAsync
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.async.AsyncExecutor
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.database.DatabaseManager
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui.AdminPanelGui
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui.PlayerWalletGui
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AuditService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.BackupService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil
import java.math.BigDecimal

/**
 * 主命令处理器 — 注册 /mce 命令及其所有子命令。
 *
 * 【命令结构】
 *   /mce                              → 显示帮助
 *   /mce balance [currency]           → 查看余额
 *   /mce give <player> <currency> <amount> [reason]   → 管理员存款
 *   /mce take <player> <currency> <amount> [reason]   → 管理员扣款
 *   /mce set <player> <currency> <amount> [reason]    → 管理员设置余额
 *   /mce currency create <id> <name> <precision> [symbol]  → 创建货币
 *   /mce currency delete <id>         → 逻辑删除货币
 *   /mce currency enable <id>         → 启用货币
 *   /mce currency disable <id>        → 禁用货币
 *   /mce currency setprimary <id>     → 设置主货币
 *   /mce currency list                → 列出所有货币
 *   /mce log <player> [currency] [page] → 查询流水
 *   /mce backup create [memo]         → 创建备份
 *   /mce rollback <snapshotId> [player] → 从快照回滚
 *   /mce setlimit <player> <currency> <max> → 设置余额上限
 *   /mce reload                       → 重载配置
 *   /mce gui                          → 打开钱包 GUI
 *
 * 【权限体系】
 *   mce.use           → 使用基础命令
 *   mce.user.balance  → 查看余额
 *   mce.user.gui      → 打开 GUI
 *   mce.admin.*       → 管理员全部权限
 */
@CommandHeader(
    name = "mce",
    aliases = ["multicurrency", "mceconomy"],
    permission = "mce.use",
    description = "MultiCurrencyEconomy 多货币经济管理"
)
object MainCommand {

    // ======================== 主命令 ========================

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sendHelp(sender)
        }
    }

    // ======================== balance ========================

    @CommandBody(permission = "mce.user.balance")
    val balance = subCommand {
        dynamic("currency", optional = true) {
            suggestion<Player> { _, _ ->
                CurrencyService.getActiveCurrencyIdentifiers()
            }
            execute<Player> { sender, _, argument ->
                showBalance(sender, argument)
            }
        }
        execute<Player> { sender, _, _ ->
            // 无参数 → 显示主货币余额
            val primary = CurrencyService.getPrimary()
            if (primary != null) {
                showBalance(sender, primary.identifier)
            } else {
                sender.sendMessage("§c未配置主货币。")
            }
        }
    }

    // ======================== give ========================

    @CommandBody(permission = "mce.admin.give")
    val give = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic("currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic("amount") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context.argument(-2)
                        val currencyId = context.argument(-1)
                        handleGive(sender, playerName, currencyId, argument, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, argument ->
                            val playerName = context.argument(-3)
                            val currencyId = context.argument(-2)
                            val amountStr = context.argument(-1)
                            handleGive(sender, playerName, currencyId, amountStr, argument)
                        }
                    }
                }
            }
        }
    }

    // ======================== take ========================

    @CommandBody(permission = "mce.admin.take")
    val take = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic("currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic("amount") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context.argument(-2)
                        val currencyId = context.argument(-1)
                        handleTake(sender, playerName, currencyId, argument, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, argument ->
                            val playerName = context.argument(-3)
                            val currencyId = context.argument(-2)
                            val amountStr = context.argument(-1)
                            handleTake(sender, playerName, currencyId, amountStr, argument)
                        }
                    }
                }
            }
        }
    }

    // ======================== set ========================

    @CommandBody(permission = "mce.admin.set")
    val set = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic("currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic("amount") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context.argument(-2)
                        val currencyId = context.argument(-1)
                        handleSet(sender, playerName, currencyId, argument, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, argument ->
                            val playerName = context.argument(-3)
                            val currencyId = context.argument(-2)
                            val amountStr = context.argument(-1)
                            handleSet(sender, playerName, currencyId, amountStr, argument)
                        }
                    }
                }
            }
        }
    }

    // ======================== currency ========================

    @CommandBody(permission = "mce.admin.currency")
    val currency = subCommand {

        // /mce currency create <id> <name> <precision> [symbol]
        literal("create") {
            dynamic("id") {
                dynamic("name") {
                    dynamic("precision") {
                        execute<CommandSender> { sender, context, argument ->
                            val id = context.argument(-2)
                            val name = context.argument(-1)
                            val precision = argument.toIntOrNull() ?: run {
                                sender.sendMessage("§c精度必须为整数。")
                                return@execute
                            }
                            handleCurrencyCreate(sender, id, name, precision, "")
                        }
                        dynamic("symbol", optional = true) {
                            execute<CommandSender> { sender, context, argument ->
                                val id = context.argument(-3)
                                val name = context.argument(-2)
                                val precision = context.argument(-1).toIntOrNull() ?: run {
                                    sender.sendMessage("§c精度必须为整数。")
                                    return@execute
                                }
                                handleCurrencyCreate(sender, id, name, precision, argument)
                            }
                        }
                    }
                }
            }
        }

        // /mce currency delete <id>
        literal("delete") {
            dynamic("id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, _, argument ->
                    AsyncExecutor.runAsync {
                        if (CurrencyService.deleteCurrency(argument)) {
                            sender.sendMessage("§a货币 §e$argument §a已逻辑删除。")
                        } else {
                            sender.sendMessage("§c删除失败（货币不存在或为主货币）。")
                        }
                    }
                }
            }
        }

        // /mce currency enable <id>
        literal("enable") {
            dynamic("id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getAllCurrencies().map { it.identifier }
                }
                execute<CommandSender> { sender, _, argument ->
                    AsyncExecutor.runAsync {
                        if (CurrencyService.enableCurrency(argument)) {
                            sender.sendMessage("§a货币 §e$argument §a已启用。")
                        } else {
                            sender.sendMessage("§c启用失败，找不到货币。")
                        }
                    }
                }
            }
        }

        // /mce currency disable <id>
        literal("disable") {
            dynamic("id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, _, argument ->
                    AsyncExecutor.runAsync {
                        if (CurrencyService.disableCurrency(argument)) {
                            sender.sendMessage("§a货币 §e$argument §a已禁用。")
                        } else {
                            sender.sendMessage("§c禁用失败，找不到货币。")
                        }
                    }
                }
            }
        }

        // /mce currency setprimary <id>
        literal("setprimary") {
            dynamic("id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, _, argument ->
                    AsyncExecutor.runAsync {
                        if (CurrencyService.setPrimary(argument)) {
                            sender.sendMessage("§a已将 §e$argument §a设为主货币。")
                        } else {
                            sender.sendMessage("§c设置失败，找不到货币。")
                        }
                    }
                }
            }
        }

        // /mce currency list
        literal("list") {
            execute<CommandSender> { sender, _, _ ->
                val all = CurrencyService.getAllCurrencies()
                if (all.isEmpty()) {
                    sender.sendMessage("§7暂无货币。")
                    return@execute
                }
                sender.sendMessage("§6===== 货币列表 =====")
                all.forEach { c ->
                    val status = buildString {
                        if (c.primary) append("§a[主]")
                        if (c.enabled) append("§a[启用]") else append("§c[禁用]")
                    }
                    sender.sendMessage("§7- §e${c.identifier} §7| §f${c.name} §7| ${c.symbol} §7| 精度:${c.precision} $status")
                }
            }
        }
    }

    // ======================== log ========================

    @CommandBody(permission = "mce.admin.log")
    val log = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, _, argument ->
                handleLog(sender, argument, null, 1)
            }
            dynamic("currency", optional = true) {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, argument ->
                    val playerName = context.argument(-1)
                    handleLog(sender, playerName, argument, 1)
                }
                dynamic("page", optional = true) {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context.argument(-2)
                        val currencyId = context.argument(-1)
                        val page = argument.toLongOrNull() ?: 1
                        handleLog(sender, playerName, currencyId, page)
                    }
                }
            }
        }
    }

    // ======================== backup ========================

    @CommandBody(permission = "mce.admin.backup")
    val backup = subCommand {
        literal("create") {
            execute<CommandSender> { sender, _, _ ->
                handleBackupCreate(sender, "")
            }
            dynamic("memo", optional = true) {
                execute<CommandSender> { sender, _, argument ->
                    handleBackupCreate(sender, argument)
                }
            }
        }
    }

    // ======================== rollback ========================

    @CommandBody(permission = "mce.admin.rollback")
    val rollback = subCommand {
        dynamic("snapshotId") {
            execute<CommandSender> { sender, _, argument ->
                handleRollback(sender, argument, null)
            }
            dynamic("player", optional = true) {
                suggestion<CommandSender> { _, _ ->
                    Bukkit.getOnlinePlayers().map { it.name }
                }
                execute<CommandSender> { sender, context, argument ->
                    val snapshotId = context.argument(-1)
                    handleRollback(sender, snapshotId, argument)
                }
            }
        }
    }

    // ======================== setlimit ========================

    @CommandBody(permission = "mce.admin.setlimit")
    val setlimit = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic("currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic("max") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context.argument(-2)
                        val currencyId = context.argument(-1)
                        val max = argument.toLongOrNull() ?: run {
                            sender.sendMessage("§c上限必须为整数（-1 = 不限）。")
                            return@execute
                        }
                        handleSetLimit(sender, playerName, currencyId, max)
                    }
                }
            }
        }
    }

    // ======================== reload ========================

    @CommandBody(permission = "mce.admin.reload")
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            MainConfig.reload()
            sender.sendMessage("§a配置重载完成。")
        }
    }

    // ======================== gui ========================

    @CommandBody(permission = "mce.user.gui")
    val gui = subCommand {
        execute<Player> { sender, _, _ ->
            PlayerWalletGui.open(sender)
        }
    }

    // ======================== 处理方法 ========================

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6===== MultiCurrencyEconomy 帮助 =====")
        sender.sendMessage("§e/mce balance [currency] §7- 查看余额")
        sender.sendMessage("§e/mce gui §7- 打开钱包界面")
        if (sender.hasPermission("mce.admin.give")) {
            sender.sendMessage("§e/mce give <player> <currency> <amount> [reason] §7- 给予金额")
            sender.sendMessage("§e/mce take <player> <currency> <amount> [reason] §7- 扣除金额")
            sender.sendMessage("§e/mce set <player> <currency> <amount> [reason] §7- 设置余额")
            sender.sendMessage("§e/mce currency <create|delete|enable|disable|setprimary|list> §7- 货币管理")
            sender.sendMessage("§e/mce log <player> [currency] [page] §7- 查询流水")
            sender.sendMessage("§e/mce backup create [memo] §7- 创建备份")
            sender.sendMessage("§e/mce rollback <snapshotId> [player] §7- 回滚备份")
            sender.sendMessage("§e/mce setlimit <player> <currency> <max> §7- 设置余额上限")
            sender.sendMessage("§e/mce reload §7- 重载配置")
        }
    }

    private fun showBalance(player: Player, currencyIdentifier: String) {
        val currency = CurrencyService.getByIdentifier(currencyIdentifier)
        if (currency == null) {
            player.sendMessage("§c找不到货币: $currencyIdentifier")
            return
        }
        val balance = AccountService.getBalance(player.uniqueId.toString(), currency.identifier)
        val formatted = CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)
        player.sendMessage("§7你的 §e${currency.name} §7余额: §f$formatted")
    }

    @Suppress("SameParameterValue")
    private fun handleGive(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isPositive(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.uniqueId.toString() else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:give" }

        AsyncExecutor.runAsync {
            val success = AccountService.deposit(uuid, name, currencyId, amount, reasonStr, operatorName)
            if (success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已向 §e$name §a的 §e$currencyId §a账户存入 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败（货币不存在、未启用或超出上限）。")
            }
        }
    }

    private fun handleTake(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isPositive(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.uniqueId.toString() else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:take" }

        AsyncExecutor.runAsync {
            val success = AccountService.withdraw(uuid, name, currencyId, amount, reasonStr, operatorName)
            if (success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已从 §e$name §a的 §e$currencyId §a账户扣除 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败（余额不足、货币不存在或未启用）。")
            }
        }
    }

    private fun handleSet(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isNonNegative(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.uniqueId.toString() else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:set" }

        AsyncExecutor.runAsync {
            val success = AccountService.setBalance(uuid, name, currencyId, amount, reasonStr, operatorName)
            if (success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已将 §e$name §a的 §e$currencyId §a余额设为 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败。")
            }
        }
    }

    private fun handleCurrencyCreate(sender: CommandSender, id: String, name: String, precision: Int, symbol: String) {
        AsyncExecutor.runAsync {
            val entity = CurrencyService.createCurrency(id, name, precision, symbol)
            if (entity != null) {
                sender.sendMessage("§a货币 §e${entity.identifier} §a(${entity.name}) 创建成功。")
            } else {
                sender.sendMessage("§c创建失败（标识符已存在）。")
            }
        }
    }

    private fun handleLog(sender: CommandSender, playerName: String, currencyId: String?, page: Long) {
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()

        AsyncExecutor.runAsync {
            val records = if (currencyId != null) {
                AuditService.queryLogsByPlayerAndCurrency(uuid, currencyId, page)
            } else {
                AuditService.queryLogs(uuid, page)
            }
            if (records.isEmpty()) {
                sender.sendMessage("§7没有更多流水记录。")
                return@runAsync
            }
            sender.sendMessage("§6===== $playerName 的流水 (第 $page 页) =====")
            records.forEach { r ->
                sender.sendMessage(
                    "§7[${r.occurredAt}] §e${r.currencyIdentifier} §7| " +
                    "§f${r.type} §7| §f${r.amount} §7| ${r.balanceBefore}→${r.balanceAfter} §7| ${r.reason}"
                )
            }
        }
    }

    private fun handleBackupCreate(sender: CommandSender, memo: String) {
        AsyncExecutor.runAsync {
            val snapshotId = BackupService.createBackup(memo)
            if (snapshotId != null) {
                sender.sendMessage("§a备份快照创建成功: §e$snapshotId")
            } else {
                sender.sendMessage("§c备份失败（无账户数据或服务未就绪）。")
            }
        }
    }

    private fun handleRollback(sender: CommandSender, snapshotId: String, playerName: String?) {
        AsyncExecutor.runAsync {
            val success = if (playerName != null) {
                val target = Bukkit.getOfflinePlayer(playerName)
                BackupService.rollbackPlayer(snapshotId, target.uniqueId.toString())
            } else {
                BackupService.rollback(snapshotId)
            }
            if (success) {
                sender.sendMessage("§a回滚完成。")
            } else {
                sender.sendMessage("§c回滚失败（快照不存在或执行出错）。")
            }
        }
    }

    private fun handleSetLimit(sender: CommandSender, playerName: String, currencyId: String, max: Long) {
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()

        AsyncExecutor.runAsync {
            val success = AccountService.setMaxBalance(uuid, currencyId, max)
            if (success) {
                sender.sendMessage("§a已将 §e${target.name ?: playerName} §a的 §e$currencyId §a余额上限设为 §f$max§a。")
            } else {
                sender.sendMessage("§c设置失败（账户或货币不存在）。")
            }
        }
    }
}
