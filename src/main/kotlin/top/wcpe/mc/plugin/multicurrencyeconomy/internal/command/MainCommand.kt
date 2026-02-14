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
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui.AdminPanelGui
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.gui.PlayerWalletGui
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AccountService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.AuditService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.BackupService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.service.CurrencyService
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.util.CurrencyPrecisionUtil

/**
 * 主命令处理器 — 注册 /mce 命令及其所有子命令。
 *
 * 【命令结构】
 *   /mce                              → 显示帮助（createHelper）
 *   /mce balance [currency]           → 查看余额
 *   /mce lookup <player> [currency]    → 后台查询玩家余额(支持离线)
 *   /mce give <player> <currency> <amount> [reason]   → 管理员存款(支持离线)
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
 *   - 使用 TabooLib 新一代命令解析器 (newParser = true)。
 *   - 使用 createHelper() 自动生成帮助信息。
 *   - 所有 dynamic comment 使用 @key 引用语言文件中的翻译。
 *   - 所有 suggestion 提供 Tab 补全候选值。
 *   - 金额参数在执行前进行校验。
 *   - 所有操作以 playerName 作为主要标识。
 *   - give/take/set 命令支持离线玩家操作：
 *     在线玩家走缓存路径（零延迟），离线玩家走 Direct 数据库路径（避免多服缓存冲突）。
 *   - lookup 命令直接从数据库查询，始终返回最新数据。
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
        dynamic(optional = true, comment = "@command-help-currency") {
            suggestion<Player> { _, _ ->
                CurrencyService.getActiveCurrencyIdentifiers()
            }
            execute<Player> { sender, context, _ ->
                val currency = context["@command-help-currency"]
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

    // ======================== lookup ========================

    /** 后台查询玩家余额 — 支持离线玩家，直接从数据库读取 */
    @CommandBody(permission = "mce.admin.lookup")
    val lookup = subCommand {
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["@command-help-player"]
                handleLookup(sender, playerName, null)
            }
            dynamic(optional = true, comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["@command-help-player"]
                    val currencyId = context["@command-help-currency"]
                    handleLookup(sender, playerName, currencyId)
                }
            }
        }
    }

    // ======================== give ========================

    /** 管理员存款 */
    @CommandBody(permission = "mce.admin.give")
    val give = subCommand {
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic(comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic(comment = "@command-help-amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["@command-help-player"]
                        val currencyId = context["@command-help-currency"]
                        val amountStr = context["@command-help-amount"]
                        handleGive(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic(optional = true, comment = "@command-help-reason") {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["@command-help-player"]
                            val currencyId = context["@command-help-currency"]
                            val amountStr = context["@command-help-amount"]
                            val reason = context["@command-help-reason"]
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
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic(comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic(comment = "@command-help-amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["@command-help-player"]
                        val currencyId = context["@command-help-currency"]
                        val amountStr = context["@command-help-amount"]
                        handleTake(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic(optional = true, comment = "@command-help-reason") {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["@command-help-player"]
                            val currencyId = context["@command-help-currency"]
                            val amountStr = context["@command-help-amount"]
                            val reason = context["@command-help-reason"]
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
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic(comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic(comment = "@command-help-amount") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["@command-help-player"]
                        val currencyId = context["@command-help-currency"]
                        val amountStr = context["@command-help-amount"]
                        handleSet(sender, playerName, currencyId, amountStr, "")
                    }
                    dynamic(optional = true, comment = "@command-help-reason") {
                        execute<CommandSender> { sender, context, _ ->
                            val playerName = context["@command-help-player"]
                            val currencyId = context["@command-help-currency"]
                            val amountStr = context["@command-help-amount"]
                            val reason = context["@command-help-reason"]
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
            dynamic(comment = "@command-help-currency-id") {
                dynamic(comment = "@command-help-currency-name") {
                    dynamic(comment = "@command-help-precision") {
                        execute<CommandSender> { sender, context, _ ->
                            val id = context["@command-help-currency-id"]
                            val name = context["@command-help-currency-name"]
                            val precision = context["@command-help-precision"].toIntOrNull() ?: run {
                                sender.sendMessage("§c精度必须为整数。")
                                return@execute
                            }
                            handleCurrencyCreate(sender, id, name, precision, "")
                        }
                        dynamic(optional = true, comment = "@command-help-symbol") {
                            execute<CommandSender> { sender, context, _ ->
                                val id = context["@command-help-currency-id"]
                                val name = context["@command-help-currency-name"]
                                val precision = context["@command-help-precision"].toIntOrNull() ?: run {
                                    sender.sendMessage("§c精度必须为整数。")
                                    return@execute
                                }
                                val symbol = context["@command-help-symbol"]
                                handleCurrencyCreate(sender, id, name, precision, symbol)
                            }
                        }
                    }
                }
            }
        }

        // /mce currency delete <id>
        literal("delete") {
            dynamic(comment = "@command-help-currency-id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val id = context["@command-help-currency-id"]
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
            dynamic(comment = "@command-help-currency-id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getAllCurrencies().map { it.identifier }
                }
                execute<CommandSender> { sender, context, _ ->
                    val id = context["@command-help-currency-id"]
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
            dynamic(comment = "@command-help-currency-id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val id = context["@command-help-currency-id"]
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
            dynamic(comment = "@command-help-currency-id") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val id = context["@command-help-currency-id"]
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
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            execute<CommandSender> { sender, context, _ ->
                val playerName = context["@command-help-player"]
                handleLog(sender, playerName, null, 1)
            }
            dynamic(optional = true, comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                execute<CommandSender> { sender, context, _ ->
                    val playerName = context["@command-help-player"]
                    val currency = context["@command-help-currency"]
                    handleLog(sender, playerName, currency, 1)
                }
                dynamic(optional = true, comment = "@command-help-page") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["@command-help-player"]
                        val currency = context["@command-help-currency"]
                        val page = context["@command-help-page"].toLongOrNull() ?: 1
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
            dynamic(optional = true, comment = "@command-help-memo") {
                execute<CommandSender> { sender, context, _ ->
                    val memo = context["@command-help-memo"]
                    handleBackupCreate(sender, memo)
                }
            }
        }
    }

    // ======================== rollback ========================

    /** 快照回滚 */
    @CommandBody(permission = "mce.admin.rollback")
    val rollback = subCommand {
        dynamic(comment = "@command-help-snapshot-id") {
            execute<CommandSender> { sender, context, _ ->
                val snapshotId = context["@command-help-snapshot-id"]
                handleRollback(sender, snapshotId, null)
            }
            dynamic(optional = true, comment = "@command-help-player") {
                suggestion<CommandSender>(uncheck = true) { _, _ ->
                    Bukkit.getOnlinePlayers().map { it.name }
                }
                execute<CommandSender> { sender, context, _ ->
                    val snapshotId = context["@command-help-snapshot-id"]
                    val playerName = context["@command-help-player"]
                    handleRollback(sender, snapshotId, playerName)
                }
            }
        }
    }

    // ======================== setlimit ========================

    /** 设置玩家余额上限 */
    @CommandBody(permission = "mce.admin.setlimit")
    val setlimit = subCommand {
        dynamic(comment = "@command-help-player") {
            suggestion<CommandSender>(uncheck = true) { _, _ ->
                Bukkit.getOnlinePlayers().map { it.name }
            }
            dynamic(comment = "@command-help-currency") {
                suggestion<CommandSender> { _, _ ->
                    CurrencyService.getActiveCurrencyIdentifiers()
                }
                dynamic(comment = "@command-help-max") {
                    execute<CommandSender> { sender, context, _ ->
                        val playerName = context["@command-help-player"]
                        val currencyId = context["@command-help-currency"]
                        val max = context["@command-help-max"].toLongOrNull() ?: run {
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
     * 后台查询玩家余额 — 直接从数据库读取，支持离线玩家。
     * 指定货币则查单个余额，不指定则显示所有已启用货币的余额。
     *
     * @param sender     命令发送者
     * @param playerName 目标玩家名称（可离线）
     * @param currencyId 货币标识符（null = 显示全部）
     */
    private fun handleLookup(sender: CommandSender, playerName: String, currencyId: String?) {
        AsyncExecutor.runAsync {
            if (currencyId != null) {
                // 查单个货币
                val currency = CurrencyService.getByIdentifier(currencyId)
                if (currency == null) {
                    sender.sendMessage("§c找不到货币: $currencyId")
                    return@runAsync
                }
                val balance = AccountService.getBalanceFromDb(playerName, currencyId)
                val formatted = CurrencyPrecisionUtil.formatWithSymbol(balance, currency.precision, currency.symbol)
                val onlineTag = if (Bukkit.getPlayerExact(playerName) != null) "§a[在线]" else "§7[离线]"
                sender.sendMessage("$onlineTag §e$playerName §7的 §e${currency.name} §7余额: §f$formatted")
            } else {
                // 查所有货币
                val accounts = AccountService.getPlayerAccountsFromDb(playerName)
                if (accounts.isEmpty()) {
                    sender.sendMessage("§7该玩家暂无账户数据。")
                    return@runAsync
                }
                val onlineTag = if (Bukkit.getPlayerExact(playerName) != null) "§a[在线]" else "§7[离线]"
                sender.sendMessage("§6===== $onlineTag §e$playerName §6的余额 =====")
                accounts.forEach { snap ->
                    sender.sendMessage("§7- §e${snap.currencyDisplayName} §7(${snap.currencyIdentifier}): §f${snap.formattedBalance}")
                }
            }
        }
    }

    /**
     * 处理管理员存款命令。
     * 在线玩家走缓存路径（零延迟），离线玩家走 Direct 数据库路径（避免多服缓存冲突）。
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
        val isOnline = Bukkit.getPlayerExact(name) != null

        AsyncExecutor.runAsync {
            val result = if (isOnline) {
                AccountService.deposit(name, uuid, currencyId, amount, reasonStr, operatorName)
            } else {
                AccountService.depositDirect(name, uuid, currencyId, amount, reasonStr, operatorName)
            }
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                val mode = if (isOnline) "" else " §7(离线模式)"
                sender.sendMessage("§a已向 §e$name §a的 §e$currencyId §a账户存入 §f$formatted§a。$mode")
            } else {
                sender.sendMessage("§c操作失败: ${result.message}")
            }
        }
    }

    /**
     * 处理管理员扣款命令。
     * 在线玩家走缓存路径，离线玩家走 Direct 数据库路径。
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
        val isOnline = Bukkit.getPlayerExact(name) != null

        AsyncExecutor.runAsync {
            val result = if (isOnline) {
                AccountService.withdraw(name, uuid, currencyId, amount, reasonStr, operatorName)
            } else {
                AccountService.withdrawDirect(name, uuid, currencyId, amount, reasonStr, operatorName)
            }
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                val mode = if (isOnline) "" else " §7(离线模式)"
                sender.sendMessage("§a已从 §e$name §a的 §e$currencyId §a账户扣除 §f$formatted§a。$mode")
            } else {
                sender.sendMessage("§c操作失败: ${result.message}")
            }
        }
    }

    /**
     * 处理管理员设置余额命令。
     * 在线玩家走缓存路径，离线玩家走 Direct 数据库路径。
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
        val isOnline = Bukkit.getPlayerExact(name) != null

        AsyncExecutor.runAsync {
            val result = if (isOnline) {
                AccountService.setBalance(name, uuid, currencyId, amount, reasonStr, operatorName)
            } else {
                AccountService.setBalanceDirect(name, uuid, currencyId, amount, reasonStr, operatorName)
            }
            if (result.success) {
                val currency = CurrencyService.getByIdentifier(currencyId)
                val formatted = currency?.let { CurrencyPrecisionUtil.format(amount, it.precision) } ?: amount.toString()
                val mode = if (isOnline) "" else " §7(离线模式)"
                sender.sendMessage("§a已将 §e$name §a的 §e$currencyId §a余额设为 §f$formatted§a。$mode")
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
                    "§f${r.type} §7| §f${r.amount} §7| ${r.balanceBefore} → ${r.balanceAfter} §7| ${r.reason}"
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
