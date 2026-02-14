# MultiCurrencyEconomy

## 项目简介

`MultiCurrencyEconomy` 是一个面向 Bukkit/Spigot/Paper 的多货币经济插件，支持在同一服务器中并行管理多种货币（如金币、点券、代币等），并提供：

- 多货币增删改查（创建、查询、启用/禁用、删除）
- 玩家账户按货币独立管理
- 乐观锁并发保护（防止超发、覆盖写、负余额异常）
- 审计日志与备份回滚能力
- Vault 兼容（主货币适配）
- 对外开放 Kotlin/Java API

## 环境要求

- Java 8+
- Bukkit / Spigot / Paper（建议较新版本）
- CoreLib（必需依赖）
- Vault（可选，用于经济生态兼容）
- PlaceholderAPI（可选）

## 安装步骤

1. 将 `MultiCurrencyEconomy` 与 `CoreLib` 放入服务器 `plugins` 目录。
2. 如需 Vault 生态支持，再放入 `Vault` 插件。
3. 启动服务器，确认插件成功加载并自动建表。
4. 检查 `plugins/MultiCurrencyEconomy/config.yml`：
   - `datasource-key` 与 CoreLib 数据源配置一致
   - 默认货币配置符合预期
5. 重启或执行 `/mce reload` 使配置生效。

## 快速开始示例

### 1. 创建并管理货币

```text
/mce currency create coin 金币 2 ￥
/mce currency create point 点券 0 P
/mce currency setprimary coin
/mce currency list
```

### 2. 对玩家余额进行操作

```text
/mce give Notch coin 100 新手奖励
/mce take Notch coin 30 商店消费
/mce set Notch point 50 活动补偿
/mce lookup Notch
```

### 3. 运行内置功能测试命令

```text
/eco-test-currency
/eco-test-account
/eco-test-concurrency 16 400
/eco-test-stability 16 400
/eco-test-all
```

测试结果为结构化中文输出，包含：测试集、用例、状态、原因、耗时。

### 4. 代码中调用 API（Kotlin）

```kotlin
val op = MultiCurrencyEconomyApi.delegate("MyPlugin")
val result = op.add(
    playerName = "Notch",
    currencyIdentifier = "coin",
    amount = java.math.BigDecimal("100.00"),
    reason = "任务奖励"
)
if (result.success) {
    println("加款成功，新余额=${result.balance}")
} else {
    println("加款失败，原因=${result.message}")
}
```

## 常见问题（FAQ）

### Q1：提示“经济服务未就绪”怎么办？

通常是数据库未完成初始化。请确认：

- CoreLib 已正常加载
- `datasource-key` 配置正确
- 数据库连接可用且账号有建表权限

### Q2：为什么 Vault 插件看不到多货币，只看到一种？

Vault 标准接口是单货币模型。本插件会把“主货币”映射给 Vault，其他货币请使用本插件命令或开放 API 调用。

### Q3：高并发下如何避免余额异常？

插件在账户层使用乐观锁与重试机制。建议线上变更前先执行：

- `/eco-test-concurrency`
- `/eco-test-stability`

### Q4：删除货币后数据会丢失吗？

货币删除是逻辑删除。历史流水和账户快照仍可保留用于审计，但该货币不会继续用于正常交易。

### Q5：如何回滚误操作？

可先创建备份，再按快照执行回滚：

```text
/mce backup create 回滚前备份
/mce rollback <snapshotId>
```

## 文档索引

- API 使用文档：`docs/API_USAGE_CN.md`
