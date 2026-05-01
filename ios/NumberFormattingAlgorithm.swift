import Foundation

/// Pure number-formatting algorithm shared by `applyNumberFormat` (the JS
/// bridge function) and `NumberInputView` (the native view's text watcher).
///
/// Mirrors the Kotlin `NumberFormattingAlgorithm` on Android. Extracted so the
/// two platforms share a single reference implementation of the walk →
/// parse → format → caret-remap pipeline and so both can be unit-tested
/// without instantiating the Expo module / UIKit.
enum NumberFormattingAlgorithm {
  struct Result {
    let formattedText: String
    let value: String
    let complete: Bool
    let caretPosition: Int
    let exceeded: Bool
    /// Value expressed as an integer in the smallest unit (e.g. cents for
    /// USD/EUR, ¥ for JPY, fils for BHD). Computed from `value` by string
    /// concatenation — exact, no floating-point. `nil` when `value` is empty.
    /// Useful for payment APIs (Stripe, Adyen, ...) that take amounts as
    /// integers in minor units.
    let minorUnits: Int64?
  }

  /// Double is exactly representable for integers up to 2^53 ≈ 9×10^15.
  /// 15 digits is the largest width that is always exactly representable
  /// regardless of the digit values.
  private static let intDigitCap = 15

  /// Compute integer minor-units from a dot-canonical raw value and a
  /// decimal-places scale, by string concatenation. `"1234.56" / 2 → 123456`,
  /// `"1.5" / 2 → 150`, `"1234" / 2 → 123400`, `"" / * → nil`.
  private static func computeMinorUnits(rawValue: String, decimalPlaces: Int) -> Int64? {
    if rawValue.isEmpty { return nil }
    let parts = rawValue.split(separator: ".", maxSplits: 1, omittingEmptySubsequences: false)
    let intPart = String(parts[0])
    var fracPart = parts.count > 1 ? String(parts[1]) : ""
    if fracPart.count < decimalPlaces {
      fracPart += String(repeating: "0", count: decimalPlaces - fracPart.count)
    } else if fracPart.count > decimalPlaces {
      fracPart = String(fracPart.prefix(decimalPlaces))
    }
    return Int64(intPart + fracPart)
  }

  static func apply(
    text: String,
    caretPosition: Int,
    locale: String? = nil,
    currency: String? = nil,
    groupingSeparator: String? = nil,
    decimalSeparator: String? = nil,
    decimalPlaces: Int? = nil,
    fixedDecimalPlaces: Bool = false,
    min: Double? = nil,
    max: Double? = nil
  ) -> Result {
    let resolvedLocale: Locale
    if let localeId = locale {
      resolvedLocale = Locale(identifier: localeId)
    } else {
      resolvedLocale = Locale.current
    }

    let formatter = NumberFormatter()
    formatter.locale = resolvedLocale
    if let currencyCode = currency {
      formatter.numberStyle = .currency
      formatter.currencyCode = currencyCode
    } else {
      formatter.numberStyle = .decimal
    }

    let effectiveDecimalSeparator = decimalSeparator ?? formatter.decimalSeparator ?? "."
    // When no explicit decimalPlaces prop, NumberFormatter's maximumFractionDigits
    // reflects the currency default for .currency style and a sensible default (~3)
    // for .decimal style.
    let maxFractionDigits = decimalPlaces ?? formatter.maximumFractionDigits

    // Walk the input keeping digits + one decimal separator. Integer digits
    // past `intDigitCap` are silently dropped (Double exact-integer limit).
    let clampedCaret = Swift.max(0, Swift.min(caretPosition, text.count))
    var digits = ""
    var hasDecimal = false
    var fractionCount = 0
    var integerCount = 0
    var contentCharsBeforeCaret = 0

    for (i, char) in text.enumerated() {
      if char.isNumber {
        if hasDecimal {
          if fractionCount >= maxFractionDigits { continue }
          fractionCount += 1
        } else {
          if integerCount >= intDigitCap { continue }
          integerCount += 1
        }
        digits.append(char)
        if i < clampedCaret {
          contentCharsBeforeCaret += 1
        }
      } else if String(char) == effectiveDecimalSeparator && !hasDecimal && maxFractionDigits > 0 {
        hasDecimal = true
        digits.append(".")
        if i < clampedCaret {
          contentCharsBeforeCaret += 1
        }
      }
    }

    // Implicit leading zero so ".5" parses as 0.5 and renders as "0.5".
    if digits.hasPrefix(".") {
      digits = "0" + digits
      contentCharsBeforeCaret += 1
    }

    let numericValue: Double? = digits.isEmpty ? nil : Double(digits)

    // Reject values above max — caller decides whether to revert or display an error.
    if let val = numericValue, let maxVal = max, val > maxVal {
      return Result(
        formattedText: "",
        value: "",
        complete: false,
        caretPosition: 0,
        exceeded: true,
        minorUnits: nil
      )
    }

    // Apply separator overrides before final formatting.
    if let gs = groupingSeparator {
      formatter.groupingSeparator = gs
      formatter.currencyGroupingSeparator = gs
    }
    if let ds = decimalSeparator {
      formatter.decimalSeparator = ds
      formatter.currencyDecimalSeparator = ds
    }

    formatter.maximumFractionDigits = maxFractionDigits
    if fixedDecimalPlaces {
      formatter.minimumFractionDigits = maxFractionDigits
    } else {
      formatter.minimumFractionDigits = hasDecimal ? Swift.min(fractionCount, maxFractionDigits) : 0
    }

    var formattedText: String
    if let val = numericValue {
      formattedText = formatter.string(from: NSNumber(value: val)) ?? digits
    } else {
      formattedText = ""
    }

    // Trailing decimal separator: render with one fraction digit, strip it so
    // the currency suffix (e.g. " €" in de-DE) stays in the correct place.
    if hasDecimal && fractionCount == 0 && !fixedDecimalPlaces,
       let val = numericValue, !formattedText.isEmpty {
      formatter.minimumFractionDigits = 1
      formatter.maximumFractionDigits = 1
      let resolvedDecSep = formatter.decimalSeparator ?? "."
      if let oneFrac = formatter.string(from: NSNumber(value: val)),
         let sepRange = oneFrac.range(of: resolvedDecSep) {
        let afterSep = oneFrac.index(after: sepRange.lowerBound)
        if afterSep < oneFrac.endIndex {
          var mutable = oneFrac
          mutable.remove(at: afterSep)
          formattedText = mutable
        } else {
          formattedText = oneFrac
        }
      }
    }

    // Caret remap: walk formatted, count digit-or-decimal-separator characters.
    let resolvedDecSep = formatter.decimalSeparator ?? "."
    var newCaretPosition = formattedText.count
    var contentCount = 0
    for (i, char) in formattedText.enumerated() {
      if char.isNumber || String(char) == resolvedDecSep {
        contentCount += 1
      }
      if contentCount == contentCharsBeforeCaret {
        newCaretPosition = i + 1
        break
      }
    }

    let complete: Bool
    if let val = numericValue {
      let aboveMin = min == nil || val >= min!
      let belowMax = max == nil || val <= max!
      complete = aboveMin && belowMax
    } else {
      complete = min == nil || min! <= 0
    }

    return Result(
      formattedText: formattedText,
      value: digits,
      complete: complete,
      caretPosition: newCaretPosition,
      exceeded: false,
      minorUnits: computeMinorUnits(rawValue: digits, decimalPlaces: maxFractionDigits)
    )
  }

  /// Cents mode: append-only digit accumulation. Strips non-digits, treats
  /// the last `decimalPlaces` digits as the fractional part. The caret is
  /// always parked at end-of-text (no in-place editing in this mode).
  static func applyCents(
    text: String,
    decimalPlaces: Int,
    locale: String? = nil,
    currency: String? = nil,
    groupingSeparator: String? = nil,
    decimalSeparator: String? = nil,
    min: Double? = nil,
    max: Double? = nil
  ) -> Result {
    var digits = ""
    for char in text where char.isNumber {
      digits.append(char)
    }
    digits = String(digits.prefix(intDigitCap))

    let numericValue: Double?
    if digits.isEmpty {
      numericValue = nil
    } else {
      let intPart = Double(digits) ?? 0
      numericValue = intPart / pow(10.0, Double(decimalPlaces))
    }

    if let val = numericValue, let maxVal = max, val > maxVal {
      return Result(
        formattedText: "",
        value: "",
        complete: false,
        caretPosition: 0,
        exceeded: true,
        minorUnits: nil
      )
    }

    let resolvedLocale: Locale
    if let localeId = locale {
      resolvedLocale = Locale(identifier: localeId)
    } else {
      resolvedLocale = Locale.current
    }

    let formatter = NumberFormatter()
    formatter.locale = resolvedLocale
    if let currencyCode = currency {
      formatter.numberStyle = .currency
      formatter.currencyCode = currencyCode
    } else {
      formatter.numberStyle = .decimal
    }
    if let gs = groupingSeparator {
      formatter.groupingSeparator = gs
      formatter.currencyGroupingSeparator = gs
    }
    if let ds = decimalSeparator {
      formatter.decimalSeparator = ds
      formatter.currencyDecimalSeparator = ds
    }
    formatter.minimumFractionDigits = decimalPlaces
    formatter.maximumFractionDigits = decimalPlaces

    let formattedText: String
    if let val = numericValue {
      formattedText = formatter.string(from: NSNumber(value: val)) ?? digits
    } else {
      formattedText = ""
    }

    let rawValue: String
    if let val = numericValue {
      rawValue = String(format: "%.\(decimalPlaces)f", val)
    } else {
      rawValue = ""
    }

    let complete: Bool
    if let val = numericValue {
      let aboveMin = min == nil || val >= min!
      let belowMax = max == nil || val <= max!
      complete = aboveMin && belowMax
    } else {
      complete = min == nil || min! <= 0
    }

    return Result(
      formattedText: formattedText,
      value: rawValue,
      complete: complete,
      caretPosition: formattedText.count,
      exceeded: false,
      minorUnits: computeMinorUnits(rawValue: rawValue, decimalPlaces: decimalPlaces)
    )
  }
}
