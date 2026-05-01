import XCTest
@testable import ExpoInputMaskAlgorithm

/// Mirrors `NumberFormattingAlgorithmTest.kt` on Android. Both suites pin the
/// same observable behavior so the two platforms stay in lock-step.
final class NumberFormattingAlgorithmTests: XCTestCase {

  // MARK: - Plain decimal & grouping

  func test_plainIntegerFormatsWithGrouping_enUS() {
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US")
    XCTAssertEqual(r.formattedText, "1,234")
    XCTAssertEqual(r.value, "1234")
    XCTAssertFalse(r.exceeded)
  }

  func test_decimalWithFraction_enUS() {
    let r = NumberFormattingAlgorithm.apply(text: "12.34", caretPosition: 5, locale: "en_US")
    XCTAssertEqual(r.formattedText, "12.34")
    XCTAssertEqual(r.value, "12.34")
  }

  func test_deDE_decimalWithCommaSeparator() {
    let r = NumberFormattingAlgorithm.apply(text: "12,34", caretPosition: 5, locale: "de_DE")
    XCTAssertEqual(r.formattedText, "12,34")
    XCTAssertEqual(r.value, "12.34")
  }

  func test_emptyText_returnsEmptyResult() {
    let r = NumberFormattingAlgorithm.apply(text: "", caretPosition: 0, locale: "en_US")
    XCTAssertEqual(r.formattedText, "")
    XCTAssertEqual(r.value, "")
    XCTAssertFalse(r.exceeded)
  }

  // MARK: - Currency

  func test_usdCurrencyWithGrouping() {
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "$1,234")
  }

  func test_usdFixedDecimalPlacesPadsWithZeros() {
    let r = NumberFormattingAlgorithm.apply(text: "12", caretPosition: 2, locale: "en_US", currency: "USD", fixedDecimalPlaces: true)
    XCTAssertEqual(r.formattedText, "$12.00")
  }

  func test_jpyDefaultsToZeroFractionDigits() {
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US", currency: "JPY")
    // JPY default is 0 fraction digits.
    XCTAssertFalse(r.formattedText.contains("."), "should have no fraction separator: \(r.formattedText)")
    XCTAssertTrue(r.formattedText.contains("1,234"), "should contain 1,234: \(r.formattedText)")
  }

  func test_bhdDefaultsToThreeFractionDigits() {
    let r = NumberFormattingAlgorithm.apply(text: "1.234", caretPosition: 5, locale: "en_US", currency: "BHD", fixedDecimalPlaces: true)
    XCTAssertTrue(r.formattedText.contains("1.234"), "should contain 1.234: \(r.formattedText)")
  }

  // MARK: - de-DE EUR trailing separator

  func test_deDE_eurTrailingSeparatorKeepsCurrencySuffixInPlace() {
    // User typed "123," — expected "123, €", not "123 €,".
    let r = NumberFormattingAlgorithm.apply(text: "123,", caretPosition: 4, locale: "de_DE", currency: "EUR")
    // NumberFormatter can emit a non-breaking space between number and currency;
    // normalize so the test isn't brittle to that detail.
    let normalized = r.formattedText.replacingOccurrences(of: "\u{00A0}", with: " ")
    XCTAssertEqual(normalized, "123, €")
  }

  func test_deDE_eurFullDecimalEntry() {
    let r = NumberFormattingAlgorithm.apply(text: "1,23", caretPosition: 4, locale: "de_DE", currency: "EUR")
    let normalized = r.formattedText.replacingOccurrences(of: "\u{00A0}", with: " ")
    XCTAssertEqual(normalized, "1,23 €")
  }

  // MARK: - Leading decimal separator

  func test_leadingDecimalSeparatorWithDigits_autoPrependsZero() {
    let r = NumberFormattingAlgorithm.apply(text: ".5", caretPosition: 2, locale: "en_US")
    XCTAssertEqual(r.value, "0.5")
    XCTAssertEqual(r.formattedText, "0.5")
  }

  func test_leadingDecimalSeparatorAlone_rendersAsZeroDot() {
    let r = NumberFormattingAlgorithm.apply(text: ".", caretPosition: 1, locale: "en_US")
    XCTAssertEqual(r.value, "0.")
    XCTAssertEqual(r.formattedText, "0.")
  }

  // MARK: - Integer digit cap

  func test_exactly15IntegerDigitsAccepted() {
    let r = NumberFormattingAlgorithm.apply(text: "123456789012345", caretPosition: 15, locale: "en_US")
    XCTAssertEqual(r.value, "123456789012345")
    XCTAssertEqual(r.formattedText, "123,456,789,012,345")
  }

  func test_sixteenthIntegerDigitSilentlyDropped() {
    let r = NumberFormattingAlgorithm.apply(text: "1234567890123456", caretPosition: 16, locale: "en_US")
    XCTAssertEqual(r.value, "123456789012345")
  }

  func test_paste20DigitsTruncatesIntegerPart() {
    let r = NumberFormattingAlgorithm.apply(text: "12345678901234567890", caretPosition: 20, locale: "en_US")
    XCTAssertEqual(r.value, "123456789012345")
    XCTAssertEqual(r.formattedText, "123,456,789,012,345")
  }

  func test_fractionDigitsStillCappedWhenIntegerIsAtMax() {
    let r = NumberFormattingAlgorithm.apply(text: "123456789012345.678", caretPosition: 19, locale: "en_US", decimalPlaces: 2)
    XCTAssertEqual(r.value, "123456789012345.67")
  }

  // MARK: - Min / Max

  func test_maxExceededReturnsExceededFlag() {
    let r = NumberFormattingAlgorithm.apply(text: "150", caretPosition: 3, locale: "en_US", max: 100.0)
    XCTAssertTrue(r.exceeded)
    XCTAssertFalse(r.complete)
    XCTAssertEqual(r.formattedText, "")
  }

  func test_minNotMetLeavesCompleteFalse() {
    let r = NumberFormattingAlgorithm.apply(text: "5", caretPosition: 1, locale: "en_US", min: 10.0)
    XCTAssertFalse(r.exceeded)
    XCTAssertFalse(r.complete)
  }

  func test_valueWithinMinAndMaxIsComplete() {
    let r = NumberFormattingAlgorithm.apply(text: "50", caretPosition: 2, locale: "en_US", min: 10.0, max: 100.0)
    XCTAssertTrue(r.complete)
    XCTAssertFalse(r.exceeded)
  }

  func test_emptyWithNoMinIsComplete() {
    let r = NumberFormattingAlgorithm.apply(text: "", caretPosition: 0, locale: "en_US")
    XCTAssertTrue(r.complete)
  }

  func test_emptyWithPositiveMinIsIncomplete() {
    let r = NumberFormattingAlgorithm.apply(text: "", caretPosition: 0, locale: "en_US", min: 10.0)
    XCTAssertFalse(r.complete)
  }

  // MARK: - Caret position

  func test_caretLandsAfterLastTypedDigit() {
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US")
    XCTAssertEqual(r.caretPosition, 5)
    XCTAssertEqual(r.formattedText, "1,234")
  }

  func test_caretLandsAfterTypedDecimalSeparator() {
    let r = NumberFormattingAlgorithm.apply(text: "12.", caretPosition: 3, locale: "en_US")
    XCTAssertEqual(r.formattedText, "12.")
    XCTAssertEqual(r.caretPosition, 3)
  }

  func test_deletingAtGroupingSeparatorReformats() {
    let r = NumberFormattingAlgorithm.apply(text: "1234.56", caretPosition: 1, locale: "en_US")
    XCTAssertEqual(r.formattedText, "1,234.56")
    XCTAssertEqual(r.value, "1234.56")
  }

  // MARK: - Overrides

  func test_customDecimalPlacesTruncatesInput() {
    let r = NumberFormattingAlgorithm.apply(text: "1.2345", caretPosition: 6, locale: "en_US", decimalPlaces: 2)
    XCTAssertEqual(r.value, "1.23")
    XCTAssertEqual(r.formattedText, "1.23")
  }

  func test_groupingSeparatorOverrideAppliesToFormattedOutput() {
    let r = NumberFormattingAlgorithm.apply(text: "1234567", caretPosition: 7, locale: "en_US", groupingSeparator: " ")
    XCTAssertEqual(r.formattedText, "1 234 567")
  }

  // MARK: - Edge cases pinned by regression test

  func test_leadingDecimalSeparatorWithCaretAtZero_putsCaretAfterPrependedZero() {
    let r = NumberFormattingAlgorithm.apply(text: ".5", caretPosition: 0, locale: "en_US")
    XCTAssertEqual(r.formattedText, "0.5")
    XCTAssertEqual(r.caretPosition, 1)
  }

  func test_bogusCurrencyCodeFallsBackGracefully() {
    let r = NumberFormattingAlgorithm.apply(text: "123", caretPosition: 3, locale: "en_US", currency: "???")
    XCTAssertFalse(r.exceeded)
    XCTAssertEqual(r.value, "123")
    XCTAssertTrue(r.formattedText.contains("123"), "formatted should contain 123: \(r.formattedText)")
  }

  func test_fixedDecimalPlacesSuppressesTrailingDecimalSeparatorRendering() {
    let r = NumberFormattingAlgorithm.apply(text: "123,", caretPosition: 4, locale: "de_DE", currency: "EUR", decimalPlaces: 2, fixedDecimalPlaces: true)
    let normalized = r.formattedText.replacingOccurrences(of: "\u{00A0}", with: " ")
    XCTAssertEqual(normalized, "123,00 €")
  }

  func test_integerCapCanMaskAMaxViolationForOverCapPastes() {
    let r = NumberFormattingAlgorithm.apply(text: "00012345678901234567", caretPosition: 20, locale: "en_US", max: 1e14)
    XCTAssertFalse(r.exceeded, "cap silently masks the over-max intent")
    XCTAssertEqual(r.value, "000123456789012")
  }

  // MARK: - applyCents

  func test_cents_emptyText() {
    let r = NumberFormattingAlgorithm.applyCents(text: "", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "")
    XCTAssertEqual(r.value, "")
  }

  func test_cents_oneDigitRendersAsCent() {
    // "1" → 0.01 → "$0.01"
    let r = NumberFormattingAlgorithm.applyCents(text: "1", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "$0.01")
    XCTAssertEqual(r.value, "0.01")
  }

  func test_cents_threeDigitsBecomesOnePointTwoThree() {
    let r = NumberFormattingAlgorithm.applyCents(text: "123", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "$1.23")
    XCTAssertEqual(r.value, "1.23")
  }

  func test_cents_stripsNonDigits() {
    // Mixed input — only digits matter.
    let r = NumberFormattingAlgorithm.applyCents(text: "$1.23", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "$1.23")
    XCTAssertEqual(r.value, "1.23")
  }

  func test_cents_zeroFractionDigits_jpy() {
    // JPY uses 0 fraction digits → cents mode collapses to integer mode.
    let r = NumberFormattingAlgorithm.applyCents(text: "1234", decimalPlaces: 0, locale: "en_US", currency: "JPY")
    XCTAssertTrue(r.formattedText.contains("1,234"), "should contain 1,234: \(r.formattedText)")
    XCTAssertEqual(r.value, "1234")
  }

  func test_cents_threeFractionDigits_bhd() {
    let r = NumberFormattingAlgorithm.applyCents(text: "1234", decimalPlaces: 3, locale: "en_US", currency: "BHD")
    XCTAssertTrue(r.formattedText.contains("1.234"), "should contain 1.234: \(r.formattedText)")
    XCTAssertEqual(r.value, "1.234")
  }

  func test_cents_caretAtEndOfFormatted() {
    let r = NumberFormattingAlgorithm.applyCents(text: "12345", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.formattedText, "$123.45")
    XCTAssertEqual(r.caretPosition, r.formattedText.count)
  }

  func test_cents_maxExceededReturnsExceededFlag() {
    // 99999 cents = $999.99, but max is 100.00.
    let r = NumberFormattingAlgorithm.applyCents(text: "99999", decimalPlaces: 2, locale: "en_US", currency: "USD", max: 100.0)
    XCTAssertTrue(r.exceeded)
    XCTAssertEqual(r.formattedText, "")
  }

  func test_cents_15DigitCap() {
    let r = NumberFormattingAlgorithm.applyCents(text: "1234567890123456789", decimalPlaces: 2, locale: "en_US")
    // Capped at 15 digits: "123456789012345" → 1234567890123.45
    XCTAssertEqual(r.value, "1234567890123.45")
  }

  func test_cents_deDE_EUR() {
    let r = NumberFormattingAlgorithm.applyCents(text: "12345", decimalPlaces: 2, locale: "de_DE", currency: "EUR")
    let normalized = r.formattedText.replacingOccurrences(of: "\u{00A0}", with: " ")
    XCTAssertEqual(normalized, "123,45 €")
    XCTAssertEqual(r.value, "123.45")
  }

  // MARK: - minorUnits

  func test_minorUnits_decimalWithFullFraction_usd() {
    // "$12.34" → 1234 cents
    let r = NumberFormattingAlgorithm.apply(text: "12.34", caretPosition: 5, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.minorUnits, 1234)
  }

  func test_minorUnits_decimalWithPartialFractionPadsWithZeros() {
    // "$1.5" with decimalPlaces=2 → 150 cents (pad "5" → "50")
    let r = NumberFormattingAlgorithm.apply(text: "1.5", caretPosition: 3, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.minorUnits, 150)
  }

  func test_minorUnits_integerEntryPadsWithZeros() {
    // "$1234" with decimalPlaces=2 → 123400 cents (no fraction → "00")
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.minorUnits, 123400)
  }

  func test_minorUnits_jpyZeroFractionCollapsesToInteger() {
    // ¥1,234 with decimalPlaces=0 → 1234 (no padding, no scaling)
    let r = NumberFormattingAlgorithm.apply(text: "1234", caretPosition: 4, locale: "en_US", currency: "JPY")
    XCTAssertEqual(r.minorUnits, 1234)
  }

  func test_minorUnits_bhdThreeFractionPadsToThousandths() {
    let r = NumberFormattingAlgorithm.apply(text: "1.234", caretPosition: 5, locale: "en_US", currency: "BHD")
    XCTAssertEqual(r.minorUnits, 1234)
  }

  func test_minorUnits_bhdPartialFractionPadsToThousandths() {
    let r = NumberFormattingAlgorithm.apply(text: "1.2", caretPosition: 3, locale: "en_US", currency: "BHD")
    XCTAssertEqual(r.minorUnits, 1200)
  }

  func test_minorUnits_emptyInputIsNil() {
    let r = NumberFormattingAlgorithm.apply(text: "", caretPosition: 0, locale: "en_US", currency: "USD")
    XCTAssertNil(r.minorUnits)
  }

  func test_minorUnits_leadingDecimalSeparatorWithAutoPrependedZero() {
    // ".5" rendered as "0.5" → 50 cents
    let r = NumberFormattingAlgorithm.apply(text: ".5", caretPosition: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.value, "0.5")
    XCTAssertEqual(r.minorUnits, 50)
  }

  func test_minorUnits_maxExceededIsNil() {
    let r = NumberFormattingAlgorithm.apply(text: "9999", caretPosition: 4, locale: "en_US", currency: "USD", max: 100.0)
    XCTAssertTrue(r.exceeded)
    XCTAssertNil(r.minorUnits)
  }

  func test_minorUnits_centsModeUsdOneDigit() {
    // Cents mode: "1" → "$0.01" → 1 cent
    let r = NumberFormattingAlgorithm.applyCents(text: "1", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.minorUnits, 1)
  }

  func test_minorUnits_centsModeUsdThreeDigits() {
    // Cents mode: "123" → "$1.23" → 123 cents
    let r = NumberFormattingAlgorithm.applyCents(text: "123", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertEqual(r.minorUnits, 123)
  }

  func test_minorUnits_centsModeJpyZeroFraction() {
    let r = NumberFormattingAlgorithm.applyCents(text: "1234", decimalPlaces: 0, locale: "en_US", currency: "JPY")
    XCTAssertEqual(r.minorUnits, 1234)
  }

  func test_minorUnits_centsModeEmptyIsNil() {
    let r = NumberFormattingAlgorithm.applyCents(text: "", decimalPlaces: 2, locale: "en_US", currency: "USD")
    XCTAssertNil(r.minorUnits)
  }

  func test_minorUnits_centsModeMaxExceededIsNil() {
    // 99999 cents = $999.99, max = $100 → exceeded
    let r = NumberFormattingAlgorithm.applyCents(text: "99999", decimalPlaces: 2, locale: "en_US", currency: "USD", max: 100.0)
    XCTAssertTrue(r.exceeded)
    XCTAssertNil(r.minorUnits)
  }

  func test_minorUnits_respectsExplicitDecimalPlaces() {
    // Explicit decimalPlaces=4, "0.0001" → 1 minor unit
    let r = NumberFormattingAlgorithm.apply(text: "0.0001", caretPosition: 6, locale: "en_US", decimalPlaces: 4)
    XCTAssertEqual(r.minorUnits, 1)
  }
}
