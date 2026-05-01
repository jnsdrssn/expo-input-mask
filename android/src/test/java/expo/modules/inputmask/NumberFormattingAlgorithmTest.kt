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
    // DecimalFormat can emit a non-breaking space between number and currency;
    // normalize before comparing so the test isn't brittle to that detail.
    val normalized = r.formattedText.replace('\u00A0', ' ')
    assertEquals("123, €", normalized)
  }

  @Test
  fun `de-DE eur full decimal entry`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1,23",
      caretPosition = 4,
      locale = "de-DE",
      currency = "EUR"
    )
    val normalized = r.formattedText.replace('\u00A0', ' ')
    assertEquals("1,23 €", normalized)
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

  // MARK: - Edge cases pinned by regression test

  @Test
  fun `leading decimal separator with caret at zero puts caret after prepended zero`() {
    // Caret was at position 0 in candidate ".5" — before all content. After
    // auto-prepending "0" the caret unconditionally shifts to 1 (between "0"
    // and "."). Documented as acceptable UX; this test pins the behavior.
    val r = NumberFormattingAlgorithm.apply(
      text = ".5",
      caretPosition = 0,
      locale = "en-US"
    )
    assertEquals("0.5", r.formattedText)
    assertEquals(1, r.caretPosition)
  }

  @Test
  fun `bogus currency code falls back gracefully`() {
    // Currency.getInstance("???") throws; the helper swallows and the formatter
    // keeps its default currency behavior. The call must not crash and must
    // still produce digit output.
    val r = NumberFormattingAlgorithm.apply(
      text = "123",
      caretPosition = 3,
      locale = "en-US",
      currency = "???"
    )
    assertFalse(r.exceeded)
    assertEquals("123", r.value)
    assertTrue("formatted should contain 123: ${r.formattedText}", r.formattedText.contains("123"))
  }

  @Test
  fun `fixedDecimalPlaces suppresses trailing decimal separator rendering`() {
    // With fixed decimals, "123," should render as "123,00 €" (both fraction
    // digits shown) — the trailing-decsep hack must not apply.
    val r = NumberFormattingAlgorithm.apply(
      text = "123,",
      caretPosition = 4,
      locale = "de-DE",
      currency = "EUR",
      fixedDecimalPlaces = true,
      decimalPlaces = 2
    )
    val normalized = r.formattedText.replace('\u00A0', ' ')
    assertEquals("123,00 €", normalized)
  }

  @Test
  fun `integer cap can mask a max violation for over-cap pastes`() {
    // Pasting 20 digits starting with leading zeros — after capping at 15,
    // the retained digits parse as ~1.23×10^11, below the max of 1×10^14.
    // Without the cap, the 20-digit value would exceed max.
    // Pin the current (deliberately lossy) behavior so it's visible on the
    // regression record rather than a silent surprise in the field.
    val r = NumberFormattingAlgorithm.apply(
      text = "00012345678901234567",
      caretPosition = 20,
      locale = "en-US",
      max = 1e14
    )
    assertFalse("cap silently masks the over-max intent", r.exceeded)
    assertEquals("000123456789012", r.value)
  }

  // MARK: - applyCents

  @Test
  fun `cents empty text returns empty result`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("", r.formattedText)
    assertEquals("", r.value)
    assertFalse(r.exceeded)
  }

  @Test
  fun `cents one digit renders as one cent`() {
    // "1" → 0.01 → "$0.01"
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("$0.01", r.formattedText)
    assertEquals("0.01", r.value)
  }

  @Test
  fun `cents three digits becomes one point two three`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "123",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("$1.23", r.formattedText)
    assertEquals("1.23", r.value)
  }

  @Test
  fun `cents strips non-digits`() {
    // Mixed input — only digits matter.
    val r = NumberFormattingAlgorithm.applyCents(
      text = "$1.23",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("$1.23", r.formattedText)
    assertEquals("1.23", r.value)
  }

  @Test
  fun `cents zero fraction digits jpy`() {
    // JPY uses 0 fraction digits → cents mode collapses to integer mode.
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1234",
      decimalPlaces = 0,
      locale = "en-US",
      currency = "JPY"
    )
    assertTrue("should contain 1,234: ${r.formattedText}", r.formattedText.contains("1,234"))
    assertEquals("1234", r.value)
  }

  @Test
  fun `cents three fraction digits bhd`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1234",
      decimalPlaces = 3,
      locale = "en-US",
      currency = "BHD"
    )
    assertTrue("should contain 1.234: ${r.formattedText}", r.formattedText.contains("1.234"))
    assertEquals("1.234", r.value)
  }

  @Test
  fun `cents caret at end of formatted`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "12345",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("$123.45", r.formattedText)
    assertEquals(r.formattedText.length, r.caretPosition)
  }

  @Test
  fun `cents max exceeded returns exceeded flag`() {
    // 99999 cents = $999.99, but max is 100.00.
    val r = NumberFormattingAlgorithm.applyCents(
      text = "99999",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD",
      max = 100.0
    )
    assertTrue(r.exceeded)
    assertEquals("", r.formattedText)
  }

  @Test
  fun `cents 15 digit cap`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1234567890123456789",
      decimalPlaces = 2,
      locale = "en-US"
    )
    // Capped at 15 digits: "123456789012345" → 1234567890123.45
    assertEquals("1234567890123.45", r.value)
  }

  @Test
  fun `cents de-DE EUR`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "12345",
      decimalPlaces = 2,
      locale = "de-DE",
      currency = "EUR"
    )
    val normalized = r.formattedText.replace('\u00A0', ' ')
    assertEquals("123,45 €", normalized)
    assertEquals("123.45", r.value)
  }

  // MARK: - minorUnits

  @Test
  fun `minorUnits decimal with full fraction usd`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "12.34",
      caretPosition = 5,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(1234L, r.minorUnits)
  }

  @Test
  fun `minorUnits decimal with partial fraction pads with zeros`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1.5",
      caretPosition = 3,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(150L, r.minorUnits)
  }

  @Test
  fun `minorUnits integer entry pads with zeros`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(123400L, r.minorUnits)
  }

  @Test
  fun `minorUnits jpy zero-fraction collapses to integer`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1234",
      caretPosition = 4,
      locale = "en-US",
      currency = "JPY"
    )
    assertEquals(1234L, r.minorUnits)
  }

  @Test
  fun `minorUnits bhd three-fraction pads to thousandths`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1.234",
      caretPosition = 5,
      locale = "en-US",
      currency = "BHD"
    )
    assertEquals(1234L, r.minorUnits)
  }

  @Test
  fun `minorUnits bhd partial fraction pads to thousandths`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "1.2",
      caretPosition = 3,
      locale = "en-US",
      currency = "BHD"
    )
    assertEquals(1200L, r.minorUnits)
  }

  @Test
  fun `minorUnits empty input is null`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "",
      caretPosition = 0,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(null, r.minorUnits)
  }

  @Test
  fun `minorUnits leading decimal separator with auto-prepended zero`() {
    val r = NumberFormattingAlgorithm.apply(
      text = ".5",
      caretPosition = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals("0.5", r.value)
    assertEquals(50L, r.minorUnits)
  }

  @Test
  fun `minorUnits max-exceeded is null`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "9999",
      caretPosition = 4,
      locale = "en-US",
      currency = "USD",
      max = 100.0
    )
    assertTrue(r.exceeded)
    assertEquals(null, r.minorUnits)
  }

  @Test
  fun `minorUnits cents mode usd one digit`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(1L, r.minorUnits)
  }

  @Test
  fun `minorUnits cents mode usd three digits`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "123",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(123L, r.minorUnits)
  }

  @Test
  fun `minorUnits cents mode jpy zero fraction`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "1234",
      decimalPlaces = 0,
      locale = "en-US",
      currency = "JPY"
    )
    assertEquals(1234L, r.minorUnits)
  }

  @Test
  fun `minorUnits cents mode empty is null`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD"
    )
    assertEquals(null, r.minorUnits)
  }

  @Test
  fun `minorUnits cents mode max-exceeded is null`() {
    val r = NumberFormattingAlgorithm.applyCents(
      text = "99999",
      decimalPlaces = 2,
      locale = "en-US",
      currency = "USD",
      max = 100.0
    )
    assertTrue(r.exceeded)
    assertEquals(null, r.minorUnits)
  }

  @Test
  fun `minorUnits respects explicit decimalPlaces`() {
    val r = NumberFormattingAlgorithm.apply(
      text = "0.0001",
      caretPosition = 6,
      locale = "en-US",
      decimalPlaces = 4
    )
    assertEquals(1L, r.minorUnits)
  }
}
