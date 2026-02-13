@file:Suppress("unused")

package top.wcpe.mc.plugin.multicurrencyeconomy.internal.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.expansion.createHelper
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
 *   /mce                              → 显示帮助（createHelper）
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
 *   /mce admin                        → 打开管理员面板 GUI
 *
 * 【权限体系】
 *   mce.use           → 使用基础命令
 *   mce.user.balance  → 查看余额
 *   mce.user.gui      → 打开 GUI
 *   mce.admin.*       → 管理员全部权限
 *
 * 【优化说明】
 *   - 使用 TabooLib CommandHelper 的 createHelper() 自动生成帮助信息。
 *   - 所有 dynamic 参数通过 context.get("paramName") 获取，不使用已弃用的 context.argument(-N)。
 *   - 所有 suggestion 提供 Tab 补全候选值。
 *   - 金额参数在执行前进行校验。
 *   - 所有操作以 playerName 作为主要标识。
 */
@CommandHeader(
    name = "mce",
    aliases = ["multicurrency", "mceconomy"],
    permission = "mce.use",
    description = "MultiCurrencyEconomy 多货币经济管理"
)
object MainCommand {

    // ======================== 主命令 ========================

    /** 主命令 — 无参数时显示帮助 */
    @CommandBody
    val main = mainCommand {
        createHelper()
    }

    // ======================== balance ========================

    /** 查看余额 — 玩家专用 */
    @CommandBody(permission = "mce.user.balance")
    val balance = subCommand {
        dynamic("currency", optional = true) {
            suggestion<Player> { _, _ ->
                CurrencyService.getActiveCurrencyIdentifiers()
            }
            execute<Player> { sender, context, _ ->
                val currency = context["currency"]
                showBalance(sender, currency)
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

    /** 管理员存款 */
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
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val currencyId = context["currency"]
                        val amountStr = context["amount"]
                        handleGive(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["player"]
                            val currencyId = context["currency"]
                            val amountStr = context["amount"]
                            val reason = context["reason"]
                            handleGive(sender, playerName, currencyId, amountStr, reason)
                        }
                    }
                }
            }
        }
    }

    // ======================== take ========================

    /** 管理员扣款 */
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
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val currencyId = context["currency"]
                        val amountStr = context["amount"]
                        handleTake(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["player"]
                            val currencyId = context["currency"]
                            val amountStr = context["amount"]
                            val reason = context["reason"]
                            handleTake(sender, playerName, currencyId, amountStr, reason)
                        }
                    }
                }
            }
        }
    }

    // ======================== set ========================

    /** 管理员设置余额 */
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
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val currencyId = context["currency"]
                        val amountStr = context["amount"]
                        handleSet(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic("reason", optional = true) {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["player"]
                            val currencyId = context["currency"]
                            val amountStr = context["amount"]
                            val reason = context["reason"]
                            handleSet(sender, playerName, currencyId, amountStr, reason)
                        }
                    }
                }
            }
        }
    }

    // ======================== currency ========================

    /** 货币管理子命令组 */
    @CommandBody(permission = "mce.admin.currency")
    val currency = subCommand {

        // /mce currency create <id> <name> <precision> [symbol]
        literal("create") {
            dynamic("id") {
                dynamic("name") {
                    dynamic("precision") {
                        execute<CommandSender> { sender, context, _ ->
                            val id = context["id"]
                            val name = context["name"]
                            val precision = context["precision"].toIntOrNull() ?: run {
                                sender.sendMessage("§c精度必须为整数。")
                                return@execute
                            }
                            handleCurrencyCreate(sender, id, name, precision, "")
                        }
                        dynamic("symbol", optional = true) {
                            execute<CommandSender> { sender, context, _ ->
                                val id = context["id"]
                                val name = context["name"]
                                val precision = context["precision"].toIntOrNull() ?: run {
                                    sender.sendMessage("§c精度必须为整数。")
                                    return@execute
                                }
                                val symbol = context["symbol"]
                                handleCurrencyCreate(sender, id, name, precision, symbol)
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
                execute<CommandSender> { sender, context, _ ->
                    val id = context["id"]
                    AsyncExecutor.runAsync {
                        if (CurrencyService.deleteCurrency(id)) {
                            sender.sendMessage("§a货币 §e$id §a已逻辑删除。")
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
                execute<CommandSender> { sender, context, _ ->
                    val id = context["id"]
                    AsyncExecutor.runAsync {
                        if (CurrencyService.enableCurrency(id)) {
                            sender.sendMessage("§a货币 §e$id §a已启用。")
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
                execute<CommandSender> { sender, context, _ ->
                    val id = context["id"]
                    AsyncExecutor.runAsync {
                        if (CurrencyService.disableCurrency(id)) {
                            sender.sendMessage("§a货币 §e$id §a已禁用。")
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
                execute<CommandSender> { sender, context, _ ->
                    val id = context["id"]
                    AsyncExecutor.runAsync {
                        if (CurrencyService.setPrimary(id)) {
                            sender.sendMessage("§a已将 §e$id §a设为主货币。")
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

    /** 查询流水 */
    @CommandBody(permission = "mce.admin.log")
    val log = subCommand {
        dynamic("player") {
            suggestion<CommandSender> { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["player"]
                handleLog(sender, playerName, null, 1)
            }
            dynamic("currency", optional = true) {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["player"]
                    val currency = context["currency"]
                    handleLog(sender, playerName, currency, 1)
                }
                dynamic("page", optional = true) {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val currency = context["currency"]
                        val page = context["page"].toLongOrNull() ?: 1
                        handleLog(sender, playerName, currency, page)
                    }
                }
            }
        }
    }

    // ======================== backup ========================

    /** 创建备份 */
    @CommandBody(permission = "mce.admin.backup")
    val backup = subCommand {
        literal("create") {
            execute<CommandSender> { sender, _, _ ->
                handleBackupCreate(sender, "")
            }
            dynamic("memo", optional = true) {
                execute<CommandSender> { sender, context, _ ->
                    val memo = context["memo"]
                    handleBackupCreate(sender, memo)
                }
            }
        }
    }

    // ======================== rollback ========================

    /** 快照回滚 */
    @CommandBody(permission = "mce.admin.rollback")
    val rollback = subCommand {
        dynamic("snapshotId") {
            execute<CommandSender> { sender, context, _ ->
                val snapshotId = context["snapshotId"]
                handleRollback(sender, snapshotId, null)
            }
            dynamic("player", optional = true) {
                suggestion<CommandSender> { _, _ ->
                    Bukkit.getOnlinePlayers().map { it.name }
                }
                execute<CommandSender> { sender, context, _ ->
                    val snapshotId = context["snapshotId"]
                    val playerName = context["player"]
                    handleRollback(sender, snapshotId, playerName)
                }
            }
        }
    }

    // ======================== setlimit ========================

    /** 设置玩家余额上限 */
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
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["player"]
                        val currencyId = context["currency"]
                        val max = context["max"].toLongOrNull() ?: run {
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

    /** 重载配置 */
    @CommandBody(permission = "mce.admin.reload")
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            MainConfig.reload()
            CurrencyService.refreshCache()
            sender.sendMessage("§a配置重载完成。")
        }
    }

    // ======================== gui ========================

    /** 打开玩家钱包 GUI */
    @CommandBody(permission = "mce.user.gui")
    val gui = subCommand {
        execute<Player> { sender, _, _ ->
            PlayerWalletGui.open(sender)
        }
    }

    // ======================== admin ========================

    /** 打开管理员面板 GUI */
    @CommandBody(permission = "mce.admin.gui")
    val admin = subCommand {
        execute<Player> { sender, _, _ ->
            AdminPanelGui.open(sender)
        }
    }

    // ======================== 处理方法 ========================

    /**
     * 显示玩家指定货币的余额。
     *
     * @param player             玩家
     * @param currencyIdentifier 货币标识符
     */
    private fun showBalance(player: Player, currencyIdentifier: String) {
        val currency = CurrencyService.getByIdentifier(currencyIdentifier)
        if (currency == null) {
            player.sendMessage("§c找不到货币: $currencyIdentifier")
            return
        }
        val balance = AccountService.getBalance(player.name, currency.identifier)
        val formatted = CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)
        player.sendMessage("§7你的 §e${currency.name} §7余额: §f$formatted")
    }

    /**
     * 处理管理员存款命令。
     * 以 playerName 为主要标识，UUID 作为记录字段。
     */
    private fun handleGive(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isPositive(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.name else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:give" }

        AsyncExecutor.runAsync {
            val result = AccountService.deposit(name, uuid, currencyId, amount, reasonStr, operatorName)
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已向 §e$name §a的 §e$currencyId §a账户存入 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败: ${result.message}")
            }
        }
    }

    /**
     * 处理管理员扣款命令。
     */
    private fun handleTake(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isPositive(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.name else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:take" }

        AsyncExecutor.runAsync {
            val result = AccountService.withdraw(name, uuid, currencyId, amount, reasonStr, operatorName)
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已从 §e$name §a的 §e$currencyId §a账户扣除 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败: ${result.message}")
            }
        }
    }

    /**
     * 处理管理员设置余额命令。
     */
    private fun handleSet(sender: CommandSender, playerName: String, currencyId: String, amountStr: String, reason: String) {
        val amount = CurrencyPrecisionUtil.parseAmount(amountStr)
        if (amount == null || !CurrencyPrecisionUtil.isNonNegative(amount)) {
            sender.sendMessage("§c无效的金额: $amountStr")
            return
        }
        val target = Bukkit.getOfflinePlayer(playerName)
        val uuid = target.uniqueId.toString()
        val name = target.name ?: playerName
        val operatorName = if (sender is Player) sender.name else "CONSOLE"
        val reasonStr = reason.ifEmpty { "command:set" }

        AsyncExecutor.runAsync {
            val result = AccountService.setBalance(name, uuid, currencyId, amount, reasonStr, operatorName)
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                sender.sendMessage("§a已将 §e$name §a的 §e$currencyId §a余额设为 §f$formatted§a。")
            } else {
                sender.sendMessage("§c操作失败: ${result.message}")
            }
        }
    }

    /**
     * 处理货币创建命令。
     */
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

    /**
     * 处理流水查询命令。
     * 以 playerName 查询流水记录。
     */
    private fun handleLog(sender: CommandSender, playerName: String, currencyId: String?, page: Long) {
        AsyncExecutor.runAsync {
            val records = if (currencyId != null) {
                AuditService.queryLogsByPlayerAndCurrency(playerName, currencyId, page)
            } else {
                AuditService.queryLogs(playerName, page)
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

    /**
     * 处理备份创建命令。
     */
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

    /**
     * 处理回滚命令。
     * 以 playerName 作为回滚标识。
     */
    private fun handleRollback(sender: CommandSender, snapshotId: String, playerName: String?) {
        AsyncExecutor.runAsync {
            val success = if (playerName != null) {
                BackupService.rollbackPlayer(snapshotId, playerName)
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

    /**
     * 处理设置余额上限命令。
     * 以 playerName 设置上限。
     */
    private fun handleSetLimit(sender: CommandSender, playerName: String, currencyId: String, max: Long) {
        AsyncExecutor.runAsync {
            val success = AccountService.setMaxBalance(playerName, currencyId, max)
            if (success) {
                sender.sendMessage("§a已将 §e$playerName §a的 §e$currencyId §a余额上限设为 §f$max§a。")
            } else {
                sender.sendMessage("§c设置失败（账户或货币不存在）。")
            }
        }
    }
}
