@file:Suppress("unused")

package top.wcpe.mc.plugin.multicurrencyeconomy.internal.command

import org.bukkit.command.CommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.test.FunctionalTestCommandSupport
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.test.FunctionalTestRunner

private const val DEFAULT_THREADS = 16
private const val DEFAULT_OPERATIONS = 400

@CommandHeader(
    name = "eco-test-currency",
    permission = "mce.admin.test.currency",
    description = "执行货币增删改查功能测试"
)
object EcoTestCurrencyCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuite(sender, "货币增删改查") {
                FunctionalTestRunner.runCurrencyCrudSuite()
            }
        }
    }
}

@CommandHeader(
    name = "eco-test-account",
    permission = "mce.admin.test.account",
    description = "执行多货币账户隔离功能测试"
)
object EcoTestAccountCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuite(sender, "账户隔离性") {
                FunctionalTestRunner.runAccountIsolationSuite()
            }
        }
    }
}

@CommandHeader(
    name = "eco-test-concurrency",
    permission = "mce.admin.test.concurrency",
    description = "执行余额并发安全功能测试"
)
object EcoTestConcurrencyCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuite(sender, "并发安全") {
                FunctionalTestRunner.runConcurrencySuite(DEFAULT_THREADS, DEFAULT_OPERATIONS)
            }
        }
        dynamic(optional = true, comment = "线程数") {
            execute<CommandSender> { sender, context, _ ->
                val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                FunctionalTestCommandSupport.runSuite(sender, "并发安全") {
                    FunctionalTestRunner.runConcurrencySuite(threads, DEFAULT_OPERATIONS)
                }
            }
            dynamic(optional = true, comment = "操作次数") {
                execute<CommandSender> { sender, context, _ ->
                    val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                    val operations = parseNumber(sender, context["操作次数"], "操作次数", 20, 20_000)
                        ?: return@execute
                    FunctionalTestCommandSupport.runSuite(sender, "并发安全") {
                        FunctionalTestRunner.runConcurrencySuite(threads, operations)
                    }
                }
            }
        }
    }
}

@CommandHeader(
    name = "eco-test-stability",
    permission = "mce.admin.test.stability",
    description = "执行异常输入与高并发稳定性测试"
)
object EcoTestStabilityCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuite(sender, "稳定性与异常输入") {
                FunctionalTestRunner.runStabilitySuite(DEFAULT_THREADS, DEFAULT_OPERATIONS)
            }
        }
        dynamic(optional = true, comment = "线程数") {
            execute<CommandSender> { sender, context, _ ->
                val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                FunctionalTestCommandSupport.runSuite(sender, "稳定性与异常输入") {
                    FunctionalTestRunner.runStabilitySuite(threads, DEFAULT_OPERATIONS)
                }
            }
            dynamic(optional = true, comment = "操作次数") {
                execute<CommandSender> { sender, context, _ ->
                    val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                    val operations = parseNumber(sender, context["操作次数"], "操作次数", 20, 20_000)
                        ?: return@execute
                    FunctionalTestCommandSupport.runSuite(sender, "稳定性与异常输入") {
                        FunctionalTestRunner.runStabilitySuite(threads, operations)
                    }
                }
            }
        }
    }
}

@CommandHeader(
    name = "eco-test-log",
    permission = "mce.admin.test.log",
    description = "执行流水记录一致性测试"
)
object EcoTestLogCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuite(sender, "流水记录一致性") {
                FunctionalTestRunner.runTransactionLogSuite()
            }
        }
    }
}

@CommandHeader(
    name = "eco-test-all",
    permission = "mce.admin.test.all",
    description = "执行全部功能测试"
)
object EcoTestAllCommand {

    @CommandBody
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            FunctionalTestCommandSupport.runSuites(sender, "全部功能测试") {
                FunctionalTestRunner.runAllSuites(DEFAULT_THREADS, DEFAULT_OPERATIONS)
            }
        }
        dynamic(optional = true, comment = "线程数") {
            execute<CommandSender> { sender, context, _ ->
                val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                FunctionalTestCommandSupport.runSuites(sender, "全部功能测试") {
                    FunctionalTestRunner.runAllSuites(threads, DEFAULT_OPERATIONS)
                }
            }
            dynamic(optional = true, comment = "操作次数") {
                execute<CommandSender> { sender, context, _ ->
                    val threads = parseNumber(sender, context["线程数"], "线程数", 2, 64) ?: return@execute
                    val operations = parseNumber(sender, context["操作次数"], "操作次数", 20, 20_000)
                        ?: return@execute
                    FunctionalTestCommandSupport.runSuites(sender, "全部功能测试") {
                        FunctionalTestRunner.runAllSuites(threads, operations)
                    }
                }
            }
        }
    }
}

private fun parseNumber(
    sender: CommandSender,
    raw: String,
    field: String,
    min: Int,
    max: Int
): Int? {
    val value = raw.toIntOrNull()
    if (value == null) {
        sender.sendMessage("§c参数无效：$field = $raw（必须是整数）")
        return null
    }
    if (value !in min..max) {
        sender.sendMessage("§c参数越界：$field = $value（允许范围 $min-$max）")
        return null
    }
    return value
}
