package top.wcpe.mc.plugin.multicurrencyeconomy.internal.util

import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * 货币金额精度与格式化工具。
 *
 * 【用途】
 *   - 按货币精度裁剪 / 舍入 BigDecimal 值
 *   - 生成带千分位和符号的格式化字符串
 * 【舍入规则】全局统一使用 [MainConfig.roundingMode]。
 * 【线程语义】所有方法均为纯函数 / 无状态，线程安全。
 */
object CurrencyPrecisionUtil {

    /**
     * 按指定精度对金额进行舍入。
     *
     * @param value     原始金额
     * @param precision 小数位数（0-8）
     * @return 舍入后的金额
     */
    fun scale(value: BigDecimal, precision: Int): BigDecimal {
        return value.setScale(precision, MainConfig.roundingMode)
    }

    /**
     * 格式化金额为带千分位分隔符的字符串。
     *
     * @param value     金额
     * @param precision 小数位数
     * @return 格式化后的字符串（如 "1,234.56"）
     */
    fun format(value: BigDecimal, precision: Int): String {
        val scaled = scale(value, precision)
        val pattern = if (precision > 0) {
            "#,##0.${"0".repeat(precision)}"
        } else {
            "#,##0"
        }
        return DecimalFormat(pattern).format(scaled)
    }

    /**
     * 格式化金额并附带货币符号。
     *
     * @param value     金额
     * @param precision 小数位数
     * @param symbol    货币符号（如 "☆"）
     * @return 带符号的格式化字符串（如 "☆1,234.56"）
     */
    fun formatWithSymbol(value: BigDecimal, precision: Int, symbol: String): String {
        return "$symbol${format(value, precision)}"
    }

    /**
     * 验证金额是否为正数。
     *
     * @param value 待验证金额
     * @return true = 正数（> 0）
     */
    fun isPositive(value: BigDecimal): Boolean {
        return value.compareTo(BigDecimal.ZERO) > 0
    }

    /**
     * 验证金额是否非负。
     *
     * @param value 待验证金额
     * @return true = 非负（>= 0）
     */
    fun isNonNegative(value: BigDecimal): Boolean {
        return value.compareTo(BigDecimal.ZERO) >= 0
    }

    /**
     * 安全地将字符串解析为 BigDecimal。
     *
     * @param text 金额字符串
     * @return 解析后的 BigDecimal，解析失败时返回 null
     */
    fun parseAmount(text: String): BigDecimal? {
        return runCatching { BigDecimal(text) }.getOrNull()
    }
}
