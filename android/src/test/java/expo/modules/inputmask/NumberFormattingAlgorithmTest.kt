package expo.modules.inputmask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberFormattingAlgorithmTest {

  // MARK: - Plain decimal & grouping

  @Test
  fun `plain integer formats with grouping en-US`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US"
    )
    assertEquals("1,234", r.formattedText)
    assertEquals("1234", r.value)
    assertFalse(r.exceeded)
  }

  @Test
  fun `decimal with fraction en-US`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12.34",
      caretPosition = 5,
      locale = "en-US"
    )
    assertEquals("12.34", r.formattedText)
    assertEquals("12.34", r.value)
  }

  @Test
  fun `de-DE decimal with comma separator`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12,34",
      caretPosition = 5,
      locale = "de-DE"
    )
    assertEquals("12,34", r.formattedText)
    assertEquals("12.34", r.value)
  }

  @Test
  fun `empty text returns empty result`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "",
      caretPosition = 0,
      locale = "en-US"
    )
    assertEquals("", r.formattedText)
    assertEquals("", r.value)
    assertFalse(r.exceeded)
  }

  // MARK: - Currency

  @Test
  fun `usd currency with grouping`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("$1,234", r.formattedText)
  }

  @Test
  fun `usd fixed decimal places pads with zeros`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12",
      caretPosition = 2,
      locale = "en-US",
      currency = "USD",
      fixedDecimalPlaces = true
    )
    assertEquals("$12.00", r.formattedText)
  }

  @Test
  fun `jpy defaults to zero fraction digits`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US",
      currency = "JPY"
    )
    // JPY.defaultFractionDigits == 0, so no ".00" suffix
    assertFalse("should have no fraction separator: ${r.formattedText}", r.formattedText.contains("."))
    assertTrue("should contain 1,234: ${r.formattedText}", r.formattedText.contains("1,234"))
  }

  @Test
  fun `bhd defaults to three fraction digits`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1.234",
      caretPosition = 5,
      locale = "en-US",
      currency = "BHD",
      fixedDecimalPlaces = true
    )
    // BHD.defaultFractionDigits == 3
    assertTrue("should contain 1.234: ${r.formattedText}", r.formattedText.contains("1.234"))
  }

  // MARK: - de-DE EUR trailing separator

  @Test
  fun `de-DE eur trailing separator keeps currency suffix in place`() {
    // User typed "123," — expected "123, €", not "123 €,"
    val r = NumberFormattingAlgorithm.apply(
      text = "123,",
      caretPosition = 4,
      locale = "de-DE",
      currency = "EUR"
    )
    val commaIdx = r.formattedText.indexOf(',')
    val euroIdx = r.formattedText.indexOf('€')
    assertTrue("expected '€' in output: ${r.formattedText}", euroIdx >= 0)
    assertTrue("expected ',' before '€' in output: ${r.formattedText}", commaIdx in 0 until euroIdx)
  }

  @Test
  fun `de-DE eur full decimal entry`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1,23",
      caretPosition = 4,
      locale = "de-DE",
      currency = "EUR"
    )
    // Expect format like "1,23 €"
    assertTrue("should start with 1,23: ${r.formattedText}", r.formattedText.startsWith("1,23"))
    assertTrue("should end with €: ${r.formattedText}", r.formattedText.trim().endsWith("€"))
  }

  // MARK: - Leading decimal separator (fix)

  @Test
  fun `leading decimal separator with digits auto-prepends zero`() {
    val r = NumberFormattingAlgorithm.apply(
      text = ".5",
      caretPosition = 2,
      locale = "en-US"
    )
    assertEquals("0.5", r.value)
    assertEquals("0.5", r.formattedText)
  }

  @Test
  fun `leading decimal separator alone renders as zero dot`() {
    val r = NumberFormattingAlgorithm.apply(
      text = ".",
      caretPosition = 1,
      locale = "en-US"
    )
    assertEquals("0.", r.value)
    // Trailing-decsep hack: renders "0" then appends separator → "0."
    assertEquals("0.", r.formattedText)
  }

  // MARK: - Integer digit cap (fix)

  @Test
  fun `exactly 15 integer digits accepted`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "123456789012345",
      caretPosition = 15,
      locale = "en-US"
    )
    assertEquals("123456789012345", r.value)
    assertEquals("123,456,789,012,345", r.formattedText)
  }

  @Test
  fun `16th integer digit silently dropped`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234567890123456",
      caretPosition = 16,
      locale = "en-US"
    )
    assertEquals("123456789012345", r.value)
  }

  @Test
  fun `paste 20 digits truncates integer part`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12345678901234567890",
      caretPosition = 20,
      locale = "en-US"
    )
    assertEquals("123456789012345", r.value)
    assertEquals("123,456,789,012,345", r.formattedText)
  }

  @Test
  fun `fraction digits still capped when integer is at max`() {
    // 15 int digits + "." + 3 fraction, decimalPlaces=2 should keep only 2
    val r = NumberFormattingAlgorithm.apply(
      text = "123456789012345.678",
      caretPosition = 19,
      locale = "en-US",
      decimalPlaces = 2
    )
    assertEquals("123456789012345.67", r.value)
  }

  // MARK: - Min / Max

  @Test
  fun `max exceeded returns exceeded flag`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "150",
      caretPosition = 3,
      locale = "en-US",
      max = 100.0
    )
    assertTrue(r.exceeded)
    assertFalse(r.complete)
    assertEquals("", r.formattedText)
  }

  @Test
  fun `min not met leaves complete false`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "5",
      caretPosition = 1,
      locale = "en-US",
      min = 10.0
    )
    assertFalse(r.exceeded)
    assertFalse(r.complete)
  }

  @Test
  fun `value within min and max is complete`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "50",
      caretPosition = 2,
      locale = "en-US",
      min = 10.0,
      max = 100.0
    )
    assertTrue(r.complete)
    assertFalse(r.exceeded)
  }

  @Test
  fun `empty with no min is complete`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "",
      caretPosition = 0,
      locale = "en-US"
    )
    assertTrue(r.complete)
  }

  @Test
  fun `empty with positive min is incomplete`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "",
      caretPosition = 0,
      locale = "en-US",
      min = 10.0
    )
    assertFalse(r.complete)
  }

  // MARK: - Caret position

  @Test
  fun `caret lands after last typed digit`() {
    // "1234" → "1,234", caret after "4" in raw (index 4) should be at index 5 in formatted
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US"
    )
    assertEquals(5, r.caretPosition)
    assertEquals("1,234", r.formattedText)
  }

  @Test
  fun `caret lands after typed decimal separator`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12.",
      caretPosition = 3,
      locale = "en-US"
    )
    assertEquals("12.", r.formattedText)
    assertEquals(3, r.caretPosition)
  }

  @Test
  fun `deleting at grouping separator reformats`() {
    // Current "1,234.56", delete separator → "1234.56" re-renders as "1,234.56"
    val r = NumberFormattingAlgorithm.apply(
      text = "1234.56",
      caretPosition = 1,
      locale = "en-US"
    )
    assertEquals("1,234.56", r.formattedText)
    assertEquals("1234.56", r.value)
  }

  // MARK: - Overrides

  @Test
  fun `custom decimalPlaces truncates input`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1.2345",
      caretPosition = 6,
      locale = "en-US",
      decimalPlaces = 2
    )
    assertEquals("1.23", r.value)
    assertEquals("1.23", r.formattedText)
  }

  @Test
  fun `grouping separator override applies to formatted output`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234567",
      caretPosition = 7,
      locale = "en-US",
      groupingSeparator = " "
    )
    // Grouping changed to space
    assertEquals("1 234 567", r.formattedText)
  }
}
