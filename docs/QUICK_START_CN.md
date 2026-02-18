# MultiCurrencyEconomy API 快速开始

> 5 分钟接入 MultiCurrencyEconomy 多货币系统

## 📦 第一步：添加依赖

**build.gradle.kts**
```kotlin
repositories {
    maven("https://maven.wcpe.top/repository/maven-public/")
}

dependencies {
    compileOnly("top.wcpe.mc.plugin:multicurrencyeconomy-api:1.0.0-SNAPSHOT")
}
```

**plugin.yml**
```yaml
depend: [MultiCurrencyEconomy]
```

## 🚀 第二步：创建 Hook 类

```kotlin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import top.wcpe.mc.plugin.multicurrencyeconomy.api.MultiCurrencyEconomyApi
import top.wcpe.mc.plugin.multicurrencyeconomy.api.EconomyOperator
import java.math.BigDecimal

object MCEHook {
    private var operator: EconomyOperator? = null
    private var ready = false

    fun init() {
        if (Bukkit.getPluginManager().getPlugin("MultiCurrencyEconomy") == null) {
            println("未找到 MultiCurrencyEconomy 插件")
            return
        }
        
        if (!MultiCurrencyEconomyApi.isReady()) {
            println("MultiCurrencyEconomy API 未就绪")
            return
        }
        
        operator = MultiCurrencyEconomyApi.delegate("YourPlugin")
        ready = true
        println("✓ MultiCurrencyEconomy 集成成功")
    }

    fun isReady() = ready

    fun getBalance(player: Player, currencyId: String): BigDecimal {
        return operator?.getBalance(player.name, currencyId) ?: BigDecimal.ZERO
    }

    fun take(player: Player, currencyId: String, amount: BigDecimal, reason: String): Boolean {
        return operator?.take(player.name, currencyId, amount, reason)?.success ?: false
    }

    fun add(player: Player, currencyId: String, amount: BigDecimal, reason: String): Boolean {
        return operator?.add(player.name, currencyId, amount, reason)?.success ?: false
    }
}
```

## 💡 第三步：初始化

> ✅ **MultiCurrencyEconomy 使用主线程同步初始化**
> 
> MCE 在 `onEnable` 中同步完成所有初始化工作（数据库、服务、API 委托），
> 其他插件可以直接在 `onEnable` 中同步调用 `isReady()` 和 `delegate()`。
> 
> **唯一要求**：在 `plugin.yml` 中配置 `depend: [MultiCurrencyEconomy]` 确保加载顺序。

### 推荐方式：直接同步调用 ✅

```kotlin
class YourPlugin : JavaPlugin() {
    override fun onEnable() {
        // 直接初始化，无需延迟或异步
        MCEHook.init()
        
        if (MCEHook.isReady()) {
            logger.info("✓ MultiCurrencyEconomy 集成成功")
        } else {
            logger.warning("✗ MultiCurrencyEconomy 未安装或加载失败")
        }
    }
}
```

### 完整示例（带检查）

```kotlin
import org.bukkit.plugin.java.JavaPlugin
import top.wcpe.mc.plugin.multicurrencyeconomy.api.MultiCurrencyEconomyApi

class YourPlugin : JavaPlugin() {
    override fun onEnable() {
        // 检查 MCE 是否已加载
        if (server.pluginManager.getPlugin("MultiCurrencyEconomy") == null) {
            logger.warning("未找到 MultiCurrencyEconomy 插件")
            return
        }
        
        // 直接检查 API 是否就绪
        if (!MultiCurrencyEconomyApi.isReady()) {
            logger.severe("MultiCurrencyEconomy API 未就绪")
            logger.severe("请检查：1) plugin.yml 中是否配置 depend: [MultiCurrencyEconomy]")
            logger.severe("         2) CoreLib 是否已安装")
            return
        }
        
        // 创建操作器
        val operator = MultiCurrencyEconomyApi.delegate("YourPlugin")
        logger.info("✓ MultiCurrencyEconomy 集成成功")
    }
}
```

### 📌 重要配置

**在 plugin.yml 中配置依赖关系（必需）：**

```yaml
name: YourPlugin
version: 1.0.0
main: your.package.YourPlugin
depend: [MultiCurrencyEconomy]  # 确保 MCE 先加载
```

或使用软依赖：

```yaml
softdepend: [MultiCurrencyEconomy]  # MCE 可选，不存在时不报错
```

**区别：**
- `depend`：硬依赖，MCE 不存在时您的插件无法加载
- `softdepend`：软依赖，需要手动检查 MCE 是否存在

## 🎯 第四步：使用

```kotlin
// 查询余额
val balance = MCEHook.getBalance(player, "coin")
player.sendMessage("你的余额: ${balance}")

// 扣款
if (MCEHook.take(player, "coin", BigDecimal("100"), "购买商品")) {
    player.sendMessage("§a购买成功！")
} else {
    player.sendMessage("§c余额不足或扣款失败")
}

// 加钱
if (MCEHook.add(player, "coin", BigDecimal("50"), "任务奖励")) {
    player.sendMessage("§a获得 50 金币")
}
```

## ⚠️ 常见问题

### Q: ClassNotFoundException / 找不到类
**A:** 检查以下几点：
1. ✅ 仓库地址是否正确添加
2. ✅ 使用 `compileOnly` 而不是 `implementation`
3. ✅ 刷新 Gradle 依赖：`./gradlew clean build --refresh-dependencies`

### Q: IllegalStateException: API 尚未初始化
**A:** 原因和解决方案：

**根本原因：未正确配置插件加载顺序**

MCE 在主线程同步初始化，如果您的插件在 MCE 之前加载，API 就还未初始化。

**解决方案（按优先级）：**

1. ✅ **在 plugin.yml 配置 `depend`**（最重要）
   ```yaml
   depend: [MultiCurrencyEconomy]  # 确保 MCE 先加载
   ```

2. ✅ 检查 MCE 是否已安装
   - 服务器 `plugins` 目录下是否有 `MultiCurrencyEconomy.jar`
   - 执行 `/plugins` 确认 MCE 显示为绿色

3. ✅ 检查 CoreLib 是否已安装
   - MCE 依赖 CoreLib，必须先安装

4. ✅ 检查服务器启动日志
   - 查看是否有 MCE 初始化错误
   - 查看数据库连接是否正常

**正确示例：**

```yaml
# plugin.yml
name: YourPlugin
depend: [MultiCurrencyEconomy]
```

```kotlin
// YourPlugin.kt
override fun onEnable() {
    // 现在可以直接同步调用
    if (MultiCurrencyEconomyApi.isReady()) {
        val operator = MultiCurrencyEconomyApi.delegate("YourPlugin")
        // 使用 operator...
    }
}
```

**错误示例（会失败）：**

```yaml
# plugin.yml - 未配置 depend
name: YourPlugin
# 缺少 depend: [MultiCurrencyEconomy]
```

```kotlin
// 此时您的插件可能在 MCE 之前加载
override fun onEnable() {
    MultiCurrencyEconomyApi.isReady()  // 返回 false！
}
```

### Q: NullPointerException
**A:** 确保使用前检查：
```kotlin
if (!MCEHook.isReady()) {
    player.sendMessage("经济系统不可用")
    return
}
```

### Q: 操作失败返回 false
**A:** 检查返回结果的 message：
```kotlin
val result = operator.take(playerName, "coin", amount, reason)
if (!result.success) {
    logger.warning("失败原因: ${result.message}")
}
```

常见失败原因：
- `余额不足` - 玩家金额不够
- `找不到货币` - 货币 ID 拼写错误
- `货币已禁用` - 货币被管理员禁用

## 📚 更多文档

- [完整 API 文档](API_USAGE_CN.md) - 详细接口说明、参数、返回值
- [Hook 完整示例代码](MCEHook_Example.kt) - 复制粘贴即用的 Hook 类（含轮询重试）
- [货币 ID 列表] 游戏内执行 `/eco currencies`
- [测试命令] `/eco balance <玩家> <货币ID>`

## 💾 下载示例代码

直接复制 [MCEHook_Example.kt](MCEHook_Example.kt) 到您的项目中：
- ✅ 支持异步初始化（轮询重试机制）
- ✅ 完善的错误处理
- ✅ 详细的中文注释
- ✅ 包含使用示例

## ✅ 验证集成

在服务器控制台执行：
```
eco balance <玩家名> coin
eco add <玩家名> coin 100
```

看到正确输出即为集成成功！
