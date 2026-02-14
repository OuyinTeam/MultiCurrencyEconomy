package top.wcpe.mc.plugin.multicurrencyeconomy.internal.util

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.Test
import top.wcpe.mc.plugin.multicurrencyeconomy.internal.config.MainConfig
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CurrencyPrecisionUtil] 单元测试。
 *
 * 覆盖点：
 * 1. 精度缩放时是否遵循配置中的舍入策略。
 * 2. 纯数字与带符号格式化输出是否符合期望。
 * 3. 正数/非负数判断在边界值（0、负数）上的行为。
 * 4. 金额字符串解析在非法输入下的健壮性。
 */
class CurrencyPrecisionUtilTest {

    /**
     * 测试前统一固定舍入模式，避免受外部配置影响。
     */
    @BeforeTest
    fun setUp() {
        mockkObject(MainConfig)
        every { MainConfig.roundingMode } returns RoundingMode.DOWN
    }

    /**
     * 清理 Mock，防止污染其他测试用例。
     */
    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `scale 应遵循配置中的舍入模式`() {
        val scaled = CurrencyPrecisionUtil.scale(BigDecimal("1.239"), 2)
        assertEquals(BigDecimal("1.23"), scaled, "按 DOWN 舍入时，1.239 保留 2 位应为 1.23")
    }

    @Test
    fun `格式化输出应保留精度并正确附加符号`() {
        val formatted = CurrencyPrecisionUtil.format(BigDecimal("1234.5"), 2)
        val withSymbol = CurrencyPrecisionUtil.formatWithSymbol(BigDecimal("1234.5"), 2, "$")

        assertEquals("1,234.50", formatted, "纯数字格式化结果不符合预期")
        assertEquals("$1,234.50", withSymbol, "带符号格式化结果不符合预期")
    }

    @Test
    fun `正数与非负判断应正确覆盖边界值`() {
        assertTrue(CurrencyPrecisionUtil.isPositive(BigDecimal("0.01")), "0.01 应判定为正数")
        assertFalse(CurrencyPrecisionUtil.isPositive(BigDecimal.ZERO), "0 不应判定为正数")
        assertTrue(CurrencyPrecisionUtil.isNonNegative(BigDecimal.ZERO), "0 应判定为非负数")
        assertFalse(CurrencyPrecisionUtil.isNonNegative(BigDecimal("-0.01")), "-0.01 不应判定为非负数")
    }

    @Test
    fun `非法金额字符串应返回 null`() {
        assertEquals(BigDecimal("12.50"), CurrencyPrecisionUtil.parseAmount("12.50"), "合法数字应正确解析")
        assertNull(CurrencyPrecisionUtil.parseAmount("12.5.0"), "包含多个小数点应解析失败")
        assertNull(CurrencyPrecisionUtil.parseAmount("abc"), "非数字字符串应解析失败")
    }
}
