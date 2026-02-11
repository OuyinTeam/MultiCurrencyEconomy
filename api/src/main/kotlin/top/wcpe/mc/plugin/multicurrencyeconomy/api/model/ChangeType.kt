package top.wcpe.mc.plugin.multicurrencyeconomy.api.model

/**
 * 余额变更类型枚举。
 *
 * 【用途】标识每笔流水的操作类型，贯穿服务层、审计流水、事件与 API。
 * 【扩展】新增类型时需同步更新流水查询/展示逻辑与数据库 type 字段映射。
 * 【线程语义】枚举常量，天然线程安全。
 */
enum class ChangeType {

    /** 存款 — 增加余额（如管理员 give、Vault deposit） */
    DEPOSIT,

    /** 取款 — 减少余额（如管理员 take、Vault withdraw） */
    WITHDRAW,

    /** 设置 — 直接将余额设定为指定值（如管理员 set） */
    SET,

    /** 回滚 — 从备份快照恢复余额（如管理员 rollback） */
    ROLLBACK
}
