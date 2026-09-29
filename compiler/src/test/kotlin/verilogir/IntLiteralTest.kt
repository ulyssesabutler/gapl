package verilogir

import com.uabutler.verilogir.module.statement.expression.IntLiteral
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class IntLiteralTest {

    // Regression: literals used to always be emitted unsized, which Verilog treats as 32 bits, so
    // Verilator rejected any wider value ("Too many digits for 32 bit number").
    @Test
    fun `sized literal serializes with its width`() {
        assertEquals("64'd9223372036854775808", IntLiteral(BigInteger("9223372036854775808"), 64).verilogSerialize())
        assertEquals("4'd0", IntLiteral(BigInteger.ZERO, 4).verilogSerialize())
    }

    @Test
    fun `literal without a width serializes unsized`() {
        assertEquals("3", IntLiteral(BigInteger.valueOf(3)).verilogSerialize())
    }
}
