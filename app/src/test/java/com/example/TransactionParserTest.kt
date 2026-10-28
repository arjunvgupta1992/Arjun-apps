package com.example

import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import com.example.data.parser.TransactionParser
import org.junit.Assert.*
import org.junit.Test

class TransactionParserTest {

    @Test
    fun parse_hdfcBankUpiDebit() {
        val message = "Sent Rs. 450.00 from HDFC Bank A/C **1234 to SWIGGY on 28-OCT-26. UPI Ref 429384928349"
        val parsed = TransactionParser.parse(message)

        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(450.0, parsed.amount, 0.01)
        assertEquals("Swiggy", parsed.merchant)
        assertEquals("429384928349", parsed.referenceId)
        assertEquals(PaymentMethod.UPI, parsed.paymentMethod)
        assertEquals(ExpenseCategory.FOOD.displayName, parsed.category)
        assertFalse(parsed.isTransferSelf)
    }

    @Test
    fun parse_sbiSalaryCredit() {
        val message = "Your A/c XX4321 is credited with Rs 85,000.00 on 28-Oct-26 by Salary. Avail Bal INR 1,12,000"
        val parsed = TransactionParser.parse(message)

        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(85000.0, parsed.amount, 0.01)
        assertEquals(ExpenseCategory.SALARY.displayName, parsed.category)
        assertFalse(parsed.isTransferSelf)
    }

    @Test
    fun parse_selfAccountTransfer() {
        val message = "Transferred Rs 10,000 from A/C XX1234 to own account XX5678 on 28-Oct-26"
        val parsed = TransactionParser.parse(message)

        assertNotNull(parsed)
        assertEquals(TransactionType.TRANSFER, parsed!!.type)
        assertEquals(10000.0, parsed.amount, 0.01)
        assertTrue(parsed.isTransferSelf)
    }

    @Test
    fun parse_strictOtpFilter_returnsNull() {
        val otpMessage = "Your OTP for NetBanking login is 839201. Valid for 10 minutes. Do not share with anyone including bank staff."
        val parsed = TransactionParser.parse(otpMessage)

        assertNull("OTPs must NEVER be parsed or stored as transactions", parsed)
    }

    @Test
    fun parse_promoMessageWithoutExecution_returnsNull() {
        val promo = "Congratulations! You have pre-approved personal loan of Rs 5,00,000 at low interest. Apply now."
        val parsed = TransactionParser.parse(promo)

        assertNull("Promotional offers must not be recorded as transactions", parsed)
    }

    @Test
    fun parse_withCustomTags() {
        val message = "Paid Rs. 1,450 to MakeMyTrip via UPI. #vacation #work"
        val parsed = TransactionParser.parse(message)

        assertNotNull(parsed)
        assertEquals(1450.0, parsed!!.amount, 0.01)
        assertTrue(parsed.tags.contains("#vacation"))
        assertTrue(parsed.tags.contains("#work"))
    }
}
