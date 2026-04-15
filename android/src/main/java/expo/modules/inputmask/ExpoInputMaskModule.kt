package expo.modules.inputmask

import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import com.redmadrobot.inputmask.helper.Mask
import com.redmadrobot.inputmask.model.CaretString
import com.redmadrobot.inputmask.model.Notation
import com.redmadrobot.inputmask.helper.AffinityCalculationStrategy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale as JavaLocale

class CustomNotationRecord : Record {
  @Field val character: String = ""
  @Field val characterSet: String = ""
  @Field val isOptional: Boolean = false
}

class ApplyMaskOptions : Record {
  @Field val primaryFormat: String = ""
  @Field val text: String = ""
  @Field val caretPosition: Int = 0
  @Field val caretGravity: String = "forward"
  @Field val autocomplete: Boolean = true
  @Field val autoskip: Boolean = false
  @Field val affinityFormats: List<String>? = null
  @Field val affinityStrategy: String? = null
  @Field val customNotations: List<CustomNotationRecord>? = null
}

class ApplyNumberFormatOptions : Record {
  @Field val text: String = ""
  @Field val caretPosition: Int = 0
  @Field val caretGravity: String = "forward"
  @Field val locale: String? = null
  @Field val currency: String? = null
  @Field val groupingSeparator: String? = null
  @Field val decimalSeparator: String? = null
  @Field val decimalPlaces: Int? = null
  @Field val min: Double? = null
  @Field val max: Double? = null
  @Field val fixedDecimalPlaces: Boolean? = null
}

class ExpoInputMaskModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { options: ApplyMaskOptions ->
      val notations = (options.customNotations ?: emptyList()).map { record ->
        if (record.character.isEmpty()) {
          throw CodedException("ERR_INVALID_NOTATION", "customNotation.character must be a single character", null)
        }
        Notation(character = record.character[0], characterSet = record.characterSet, isOptional = record.isOptional)
      }

      val primaryMask = try {
        Mask.getOrCreate(options.primaryFormat, notations)
      } catch (e: Exception) {
        throw CodedException("ERR_INVALID_MASK", "Invalid mask format '${options.primaryFormat}': ${e.message}", e)
      }

      val gravity: CaretString.CaretGravity =
        if (options.caretGravity == "backward") {
          CaretString.CaretGravity.BACKWARD(options.autoskip)
        } else {
          CaretString.CaretGravity.FORWARD(options.autocomplete)
        }

      val caretPos = options.caretPosition.coerceIn(0, options.text.length)
      val caretString = CaretString(options.text, caretPos, gravity)

      val affinityFormats = options.affinityFormats
      if (!affinityFormats.isNullOrEmpty()) {
        val strategy = parseStrategy(options.affinityStrategy)
        val primaryResult = primaryMask.apply(caretString)
        val primaryAffinity = strategy.calculateAffinityOfMask(primaryMask, caretString)

        var bestResult = primaryResult
        var bestAffinity = primaryAffinity

        for (format in affinityFormats) {
          val mask = try {
            Mask.getOrCreate(format, notations)
          } catch (e: Exception) {
            continue
          }
          val result = mask.apply(caretString)
          val affinity = strategy.calculateAffinityOfMask(mask, caretString)
          if (affinity > bestAffinity) {
            bestAffinity = affinity
            bestResult = result
          }
        }

        return@Function mapOf(
          "formattedText" to bestResult.formattedText.string,
          "extractedValue" to bestResult.extractedValue,
          "complete" to bestResult.complete,
          "caretPosition" to bestResult.formattedText.caretPosition,
          "affinityOfPrimaryFormat" to primaryAffinity
        )
      }

      val result = primaryMask.apply(caretString)

      mapOf(
        "formattedText" to result.formattedText.string,
        "extractedValue" to result.extractedValue,
        "complete" to result.complete,
        "caretPosition" to result.formattedText.caretPosition
      )
    }

    Function("applyNumberFormat") { options: ApplyNumberFormatOptions ->
      val resolvedLocale: JavaLocale = if (options.locale != null) {
        JavaLocale.forLanguageTag(options.locale!!.replace("_", "-"))
      } else {
        JavaLocale.getDefault()
      }

      // Build symbols once — reused for both parsing and formatting
      val symbols = DecimalFormatSymbols.getInstance(resolvedLocale)
      val effectiveDecimalSeparator: Char = if (options.decimalSeparator != null) {
        options.decimalSeparator!!.first()
      } else {
        symbols.decimalSeparator
      }

      // Determine max fractional digits
      val maxFractionDigits: Int = if (options.decimalPlaces != null) {
        options.decimalPlaces!!
      } else if (options.currency != null) {
        try {
          Currency.getInstance(options.currency).defaultFractionDigits
        } catch (e: Exception) {
          2
        }
      } else {
        2
      }

      // Strip input to digits and decimal separator only
      val inputText = options.text
      val digitsBuilder = StringBuilder()
      var hasDecimal = false
      var fractionCount = 0
      val clampedCaret = options.caretPosition.coerceIn(0, inputText.length)
      var contentCharsBeforeCaret = 0

      for ((i, char) in inputText.withIndex()) {
        if (char.isDigit()) {
          if (hasDecimal) {
            if (fractionCount >= maxFractionDigits) continue
            fractionCount++
          }
          digitsBuilder.append(char)
          if (i < clampedCaret) contentCharsBeforeCaret++
        } else if (char == effectiveDecimalSeparator && !hasDecimal && maxFractionDigits > 0) {
          hasDecimal = true
          digitsBuilder.append('.')
          if (i < clampedCaret) contentCharsBeforeCaret++
        }
      }

      val digits = digitsBuilder.toString()

      // Parse the numeric value
      val numericValue: Double? = if (digits.isEmpty()) null else digits.toDoubleOrNull()

      // Enforce max constraint
      if (numericValue != null && options.max != null && numericValue > options.max!!) {
        return@Function mapOf(
          "formattedText" to "",
          "value" to "",
          "complete" to false,
          "caretPosition" to 0,
          "exceeded" to true
        )
      }

      // Apply separator overrides before building the formatter
      if (options.groupingSeparator != null) {
        symbols.groupingSeparator = options.groupingSeparator!!.first()
      }
      if (options.decimalSeparator != null) {
        symbols.decimalSeparator = options.decimalSeparator!!.first()
      }

      val formatter: DecimalFormat
      if (options.currency != null) {
        formatter = DecimalFormat.getCurrencyInstance(resolvedLocale) as DecimalFormat
        try {
          formatter.currency = Currency.getInstance(options.currency)
        } catch (_: Exception) {}
      } else {
        formatter = DecimalFormat.getInstance(resolvedLocale) as DecimalFormat
      }
      formatter.decimalFormatSymbols = symbols
      formatter.isGroupingUsed = true
      val isFixedDecimal = options.fixedDecimalPlaces == true
      if (isFixedDecimal) {
        formatter.minimumFractionDigits = maxFractionDigits
      } else {
        formatter.minimumFractionDigits = if (hasDecimal) minOf(fractionCount, maxFractionDigits) else 0
      }
      formatter.maximumFractionDigits = maxFractionDigits

      // Format
      var formattedText: String = if (numericValue != null) {
        formatter.format(numericValue)
      } else {
        ""
      }

      // When user typed a decimal point but no fraction digits yet, append the separator
      if (hasDecimal && fractionCount == 0 && !isFixedDecimal && formattedText.isNotEmpty()) {
        formattedText += if (options.decimalSeparator != null) options.decimalSeparator!! else symbols.decimalSeparator.toString()
      }

      // Caret repositioning
      val resolvedDecSep = symbols.decimalSeparator
      var newCaretPosition = formattedText.length
      var contentCount = 0
      for ((i, char) in formattedText.withIndex()) {
        if (char.isDigit() || char == resolvedDecSep) {
          contentCount++
        }
        if (contentCount == contentCharsBeforeCaret) {
          newCaretPosition = i + 1
          break
        }
      }

      // Completeness
      val complete: Boolean = if (numericValue != null) {
        val aboveMin = options.min == null || numericValue >= options.min!!
        val belowMax = options.max == null || numericValue <= options.max!!
        aboveMin && belowMax
      } else {
        options.min == null || options.min!! <= 0.0
      }

      mapOf(
        "formattedText" to formattedText,
        "value" to digits,
        "complete" to complete,
        "caretPosition" to newCaretPosition,
        "exceeded" to false
      )
    }
  }

  private fun parseStrategy(strategy: String?): AffinityCalculationStrategy {
    return when (strategy) {
      "prefix" -> AffinityCalculationStrategy.PREFIX
      "capacity" -> AffinityCalculationStrategy.CAPACITY
      "extracted_value_capacity" -> AffinityCalculationStrategy.EXTRACTED_VALUE_CAPACITY
      else -> AffinityCalculationStrategy.WHOLE_STRING
    }
  }
}
