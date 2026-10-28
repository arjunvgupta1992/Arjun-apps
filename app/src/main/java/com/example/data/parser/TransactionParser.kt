package com.example.data.parser

import com.example.data.model.ExpenseCategory
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import java.util.Locale
import java.util.regex.Pattern

data class ParsedTransaction(
    val type: TransactionType,
    val amount: Double,
    val category: String,
    val paymentMethod: PaymentMethod,
    val merchant: String,
    val account: String,
    val referenceId: String,
    val isTransferSelf: Boolean,
    val confidenceScore: Float,
    val rawText: String,
    val tags: String = ""
)

object TransactionParser {

    // Strict OTP and Security Filter - Never parse or store sensitive OTPs
    private val OTP_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(otp|one[ -]time[ -]password|verification[ -]code|security[ -]code|auth[ -]code|cvv|secret[ -]pin|atm[ -]pin)\\b"),
        Pattern.compile("(?i)\\bdo not share\\b"),
        Pattern.compile("(?i)\\bvalid for (?:only )?\\d+ (?:min|second|sec)")
    )

    // Spam / Promotional filter that does not contain financial execution
    private val PROMO_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(pre-approved|apply for loan|congratulations you won|claim your reward|flat \\d+% off)\\b")
    )

    // Amount patterns
    // e.g. "Rs. 1,250.00", "Rs 500", "INR 450.50", "₹ 2,400", "debited by Rs 1500"
    private val AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:rs\\.?|inr|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    // Direction indicators
    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "sent", "paid", "spent", "dr", "transfer to", "withdrawn", "purchase", "charged"
    )
    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "cr", "refund", "added to", "cashback", "salary"
    )
    private val SELF_TRANSFER_KEYWORDS = listOf(
        "to own account", "self transfer", "between your accounts", "linked a/c", "transferred to a/c"
    )

    // Reference ID extraction (UPI Ref, UTR, IMPS)
    private val REF_PATTERNS = listOf(
        Pattern.compile("(?i)(?:upi\\s*(?:ref|txn|transaction)?\\s*(?:no\\.?|id|#)?\\s*[:\\-]?\\s*)([0-9A-Za-z]{8,22})"),
        Pattern.compile("(?i)(?:ref\\s*(?:no\\.?|id)?\\s*[:\\-]?\\s*)([0-9]{10,22})"),
        Pattern.compile("(?i)(?:utr\\s*(?:no\\.?|id)?\\s*[:\\-]?\\s*)([0-9A-Za-z]{10,22})"),
        Pattern.compile("(?i)(?:rrn\\s*[:\\-]?\\s*)([0-9]{10,18})"),
        Pattern.compile("(?i)(?:imps\\s*(?:ref)?\\s*[:\\-]?\\s*)([0-9]{10,18})")
    )

    // Merchant extraction
    private val MERCHANT_TO_PATTERN = Pattern.compile(
        "(?i)(?:to|at|paid to|transfer to|sent to)\\s+([A-Za-z0-9&.\\-']{2,32}?)(?=\\s+on|\\s+via|\\s+using|\\s+ref|\\s+upi|\\.|\\,|\\n|$)"
    )
    private val MERCHANT_FROM_PATTERN = Pattern.compile(
        "(?i)(?:from|by)\\s+([A-Za-z0-9&.\\-']{2,32}?)(?=\\s+on|\\s+via|\\s+ref|\\s+upi|\\.|\\,|\\n|$)"
    )
    private val INFO_PATTERN = Pattern.compile(
        "(?i)(?:info[:\\s]+|vpa\\s+)([A-Za-z0-9&.\\-@]{2,32})"
    )

    // Account extraction
    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|acct|acc|account|card)\\s*(?:ending\\s*)?(?:with\\s*)?(?:no\\.?\\s*)?([xX*]*\\d{3,4})"
    )

    /**
     * Parse incoming message. Returns ParsedTransaction or null if not a valid transaction or contains OTP/promo.
     */
    fun parse(text: String, packageName: String? = null): ParsedTransaction? {
        if (text.isBlank()) return null

        // 1. Check for OTP / Security sensitive data -> NEVER parse or store OTPs
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return null
            }
        }

        // 2. Extract Amount
        val amountMatcher = AMOUNT_PATTERN.matcher(text)
        if (!amountMatcher.find()) {
            return null
        }

        val rawAmountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
        val amount = try {
            rawAmountStr.toDouble()
        } catch (_: Exception) {
            return null
        }

        if (amount <= 0.0) return null

        // 3. Determine Direction / Type
        val lowerText = text.lowercase(Locale.ROOT)

        val isSelfTransfer = SELF_TRANSFER_KEYWORDS.any { lowerText.contains(it) }

        var type: TransactionType? = null
        if (isSelfTransfer) {
            type = TransactionType.TRANSFER
        } else {
            // Find positions of debit vs credit words
            val debitMatch = DEBIT_KEYWORDS.any { lowerText.contains(it) }
            val creditMatch = CREDIT_KEYWORDS.any { lowerText.contains(it) }

            if (debitMatch && !creditMatch) {
                type = TransactionType.EXPENSE
            } else if (creditMatch && !debitMatch) {
                type = TransactionType.INCOME
            } else if (debitMatch && creditMatch) {
                // If both present, pick the one appearing closer to the beginning or before amount
                val debitIdx = DEBIT_KEYWORDS.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
                val creditIdx = CREDIT_KEYWORDS.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
                type = if (debitIdx < creditIdx) TransactionType.EXPENSE else TransactionType.INCOME
            }
        }

        if (type == null) {
            // Check promo patterns
            if (PROMO_PATTERNS.any { it.matcher(text).find() }) {
                return null
            }
            // If amount exists and is from known UPI package (like GPay/PhonePe), assume expense with lower confidence
            if (packageName != null && isUpiPackage(packageName)) {
                type = TransactionType.EXPENSE
            } else {
                return null
            }
        }

        // 4. Extract Reference ID
        var referenceId = ""
        for (refPat in REF_PATTERNS) {
            val m = refPat.matcher(text)
            if (m.find()) {
                val candidate = m.group(1)?.trim() ?: ""
                if (candidate.length >= 6) {
                    referenceId = candidate
                    break
                }
            }
        }

        // 5. Extract Merchant / Counterparty
        var merchant = ""
        if (type == TransactionType.INCOME) {
            val fromMatcher = MERCHANT_FROM_PATTERN.matcher(text)
            if (fromMatcher.find()) {
                merchant = cleanMerchantName(fromMatcher.group(1))
            }
        } else {
            val toMatcher = MERCHANT_TO_PATTERN.matcher(text)
            if (toMatcher.find()) {
                merchant = cleanMerchantName(toMatcher.group(1))
            }
        }

        if (merchant.isBlank()) {
            val infoMatcher = INFO_PATTERN.matcher(text)
            if (infoMatcher.find()) {
                merchant = cleanMerchantName(infoMatcher.group(1))
            }
        }

        if (merchant.isBlank()) {
            merchant = deduceMerchantFromPackage(packageName) ?: "UPI Merchant"
        }

        // 6. Extract Account
        var account = "Primary Account"
        val accMatcher = ACCOUNT_PATTERN.matcher(text)
        if (accMatcher.find()) {
            val digits = accMatcher.group(1)?.replace("*", "")?.replace("x", "")?.replace("X", "")
            if (!digits.isNullOrBlank()) {
                account = "A/c **$digits"
            }
        } else {
            account = deduceBankName(text) ?: "Bank A/c"
        }

        // 7. Payment Method
        val paymentMethod = when {
            lowerText.contains("upi") || (packageName != null && isUpiPackage(packageName)) -> PaymentMethod.UPI
            lowerText.contains("card") || lowerText.contains("visa") || lowerText.contains("mastercard") -> PaymentMethod.CREDIT_CARD
            lowerText.contains("atm") || lowerText.contains("cash") -> PaymentMethod.CASH
            lowerText.contains("neft") || lowerText.contains("rtgs") || lowerText.contains("imps") -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.UPI
        }

        // 8. Deduce Category
        val category = deduceCategory(merchant, lowerText, type)

        // 9. Extract hashtags (e.g. #vacation, #work)
        val tagsFound = Regex("#[A-Za-z0-9_-]+").findAll(text).map { it.value.lowercase(Locale.ROOT) }.distinct().toList()
        val tagsStr = tagsFound.joinToString(", ")

        // 10. Calculate Confidence
        var confidence = 0.65f
        if (merchant.isNotBlank() && merchant != "UPI Merchant") confidence += 0.15f
        if (referenceId.isNotBlank()) confidence += 0.15f
        if (account != "Bank A/c" && account != "Primary Account") confidence += 0.05f
        confidence = confidence.coerceIn(0.5f, 0.99f)

        return ParsedTransaction(
            type = type,
            amount = amount,
            category = category.displayName,
            paymentMethod = paymentMethod,
            merchant = merchant,
            account = account,
            referenceId = referenceId,
            isTransferSelf = isSelfTransfer,
            confidenceScore = confidence,
            rawText = text,
            tags = tagsStr
        )
    }

    private fun cleanMerchantName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var cleaned = raw.trim()
            .replace(Regex("(?i)^(vpa|a/c|acct|upi|m/s|mr|mrs|ms)\\s+"), "")
            .replace(Regex("(?i)\\s+(ltd|pvt|limited|inc|corp|co)\\.?$"), "")
            .trim()
        if (cleaned.length > 30) cleaned = cleaned.take(30)
        return cleaned.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun deduceBankName(text: String): String? {
        val t = text.uppercase(Locale.ROOT)
        return when {
            t.contains("HDFC") -> "HDFC Bank"
            t.contains("SBI") || t.contains("STATE BANK") -> "SBI"
            t.contains("ICICI") -> "ICICI Bank"
            t.contains("AXIS") -> "Axis Bank"
            t.contains("KOTAK") -> "Kotak Bank"
            t.contains("PUNJAB") || t.contains("PNB") -> "PNB"
            t.contains("CANARA") -> "Canara Bank"
            t.contains("BOB") || t.contains("BANK OF BARODA") -> "Bank of Baroda"
            t.contains("IDFC") -> "IDFC FIRST"
            t.contains("INDUSIND") -> "IndusInd Bank"
            t.contains("PAYTM PAYMENTS BANK") -> "Paytm Bank"
            t.contains("AIRTEL PAYMENTS BANK") -> "Airtel Bank"
            else -> null
        }
    }

    private fun isUpiPackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.ROOT)
        return p.contains("paisa") || p.contains("gpay") || p.contains("phonepe") || 
               p.contains("paytm") || p.contains("npci") || p.contains("cred") ||
               p.contains("bhim") || p.contains("amazon")
    }

    private fun deduceMerchantFromPackage(pkg: String?): String? {
        if (pkg == null) return null
        val p = pkg.lowercase(Locale.ROOT)
        return when {
            p.contains("phonepe") -> "PhonePe Contact"
            p.contains("paisa") || p.contains("google.android.apps.nbu.paisa") -> "Google Pay Contact"
            p.contains("paytm") -> "Paytm Contact"
            p.contains("cred") -> "CRED Payment"
            p.contains("bhim") -> "BHIM UPI"
            else -> null
        }
    }

    fun deduceCategory(merchant: String, fullText: String, type: TransactionType): ExpenseCategory {
        if (type == TransactionType.INCOME) {
            val lower = (merchant + " " + fullText).lowercase(Locale.ROOT)
            return if (lower.contains("salary") || lower.contains("payroll")) {
                ExpenseCategory.SALARY
            } else if (lower.contains("dividend") || lower.contains("interest") || lower.contains("mutual fund")) {
                ExpenseCategory.INVESTMENT
            } else {
                ExpenseCategory.OTHER
            }
        }

        if (type == TransactionType.TRANSFER) {
            return ExpenseCategory.TRANSFERS
        }

        val combined = (merchant + " " + fullText).lowercase(Locale.ROOT)
        return when {
            combined.contains("swiggy") || combined.contains("zomato") || combined.contains("restaurant") ||
            combined.contains("cafe") || combined.contains("mcdonald") || combined.contains("starbucks") ||
            combined.contains("burger") || combined.contains("pizza") || combined.contains("eats") ||
            combined.contains("tea") || combined.contains("chai") || combined.contains("coffee") -> ExpenseCategory.FOOD

            combined.contains("blinkit") || combined.contains("zepto") || combined.contains("instamart") ||
            combined.contains("dmart") || combined.contains("bigbasket") || combined.contains("grocer") ||
            combined.contains("supermarket") || combined.contains("spencer") || combined.contains("reliance fresh") -> ExpenseCategory.GROCERIES

            combined.contains("uber") || combined.contains("ola") || combined.contains("rapido") ||
            combined.contains("metro") || combined.contains("irctc") || combined.contains("railway") ||
            combined.contains("makemytrip") || combined.contains("flight") || combined.contains("bus") -> ExpenseCategory.TRAVEL

            combined.contains("petrol") || combined.contains("fuel") || combined.contains("shell") ||
            combined.contains("hpcl") || combined.contains("bpcl") || combined.contains("indian oil") -> ExpenseCategory.FUEL

            combined.contains("electricity") || combined.contains("bescom") || combined.contains("tneb") ||
            combined.contains("broadband") || combined.contains("airtel") || combined.contains("jio") ||
            combined.contains("vodafone") || combined.contains("vi") || combined.contains("water") ||
            combined.contains("gas bill") || combined.contains("utility") -> ExpenseCategory.BILLS

            combined.contains("netflix") || combined.contains("spotify") || combined.contains("youtube") ||
            combined.contains("prime") || combined.contains("hotstar") || combined.contains("apple.com/bill") ||
            combined.contains("subscription") -> ExpenseCategory.SUBSCRIPTIONS

            combined.contains("amazon") || combined.contains("flipkart") || combined.contains("myntra") ||
            combined.contains("ajio") || combined.contains("nykaa") || combined.contains("tata cliq") ||
            combined.contains("shopping") || combined.contains("mall") -> ExpenseCategory.SHOPPING

            combined.contains("hospital") || combined.contains("pharmacy") || combined.contains("apollo") ||
            combined.contains("pharmeasy") || combined.contains("1mg") || combined.contains("clinic") ||
            combined.contains("doctor") || combined.contains("medplus") -> ExpenseCategory.HEALTHCARE

            combined.contains("emi") || combined.contains("loan") || combined.contains("bajaj finance") ||
            combined.contains("hdb") || combined.contains("muthoot") -> ExpenseCategory.EMI

            combined.contains("rent") || combined.contains("landlord") || combined.contains("nobroker") -> ExpenseCategory.RENT

            else -> ExpenseCategory.OTHER
        }
    }
}
