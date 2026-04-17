package expo.modules.inputmask

import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import com.redmadrobot.inputmask.helper.Mask
import com.redmadrobot.inputmask.model.CaretString
import com.redmadrobot.inputmask.model.Notation
import com.redmadrobot.inputmask.helper.AffinityCalculationStrategy

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
      val r = NumberFormattingAlgorithm.apply(
        text = options.text,
        caretPosition = options.caretPosition,
        locale = options.locale,
        currency = options.currency,
        groupingSeparator = options.groupingSeparator,
        decimalSeparator = options.decimalSeparator,
        decimalPlaces = options.decimalPlaces,
        fixedDecimalPlaces = options.fixedDecimalPlaces == true,
        min = options.min,
        max = options.max
      )
      mapOf(
        "formattedText" to r.formattedText,
        "value" to r.value,
        "complete" to r.complete,
        "caretPosition" to r.caretPosition,
        "exceeded" to r.exceeded
      )
    }

    View(NumberInputView::class) {
      // Note: events are registered under `onFocusEvent` / `onBlurEvent` rather
      // than `onFocus` / `onBlur` because Fabric reserves the latter names for
      // its own focus-handling pipeline. The JS wrapper (NumberInput.tsx)
      // transparently re-exposes them as `onFocus` / `onBlur` to users.
      Events(
        "onValueChange",
        "onFocusEvent",
        "onBlurEvent"
      )

      AsyncFunction("focus") { view: NumberInputView ->
        view.focusField()
      }

      AsyncFunction("blur") { view: NumberInputView ->
        view.blurField()
      }

      AsyncFunction("clear") { view: NumberInputView ->
        view.clearField()
      }

      Prop("placeholder") { view: NumberInputView, value: String? ->
        view.editText.hint = value
      }

      Prop("editable") { view: NumberInputView, value: Boolean? ->
        view.editText.isEnabled = value ?: true
      }

      Prop("textAlign") { view: NumberInputView, value: String? ->
        view.editText.gravity = when (value) {
          "center" -> Gravity.CENTER
          "right" -> Gravity.END or Gravity.CENTER_VERTICAL
          "left" -> Gravity.LEFT or Gravity.CENTER_VERTICAL
          else -> Gravity.START or Gravity.CENTER_VERTICAL
        }
      }

      Prop("keyboardType") { view: NumberInputView, value: String? ->
        view.editText.inputType = when (value) {
          "numeric", "number-pad" -> InputType.TYPE_CLASS_NUMBER
          else -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        // setInputType resets the keyListener; reapply ours so both "." and ","
        // stay accepted regardless of the device locale's decimal separator.
        view.editText.keyListener = DigitsKeyListener.getInstance("0123456789.,")
      }

      Prop("returnKeyType") { view: NumberInputView, value: String? ->
        view.editText.imeOptions = when (value) {
          "go" -> android.view.inputmethod.EditorInfo.IME_ACTION_GO
          "next" -> android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
          "search" -> android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
          "send" -> android.view.inputmethod.EditorInfo.IME_ACTION_SEND
          "done" -> android.view.inputmethod.EditorInfo.IME_ACTION_DONE
          else -> android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED
        }
      }

      Prop("locale") { view: NumberInputView, value: String? ->
        view.propLocale = value
      }

      Prop("currency") { view: NumberInputView, value: String? ->
        view.propCurrency = value
      }

      Prop("groupingSeparator") { view: NumberInputView, value: String? ->
        view.propGroupingSeparator = value
      }

      Prop("decimalSeparator") { view: NumberInputView, value: String? ->
        view.propDecimalSeparator = value
      }

      Prop("decimalPlaces") { view: NumberInputView, value: Int? ->
        view.propDecimalPlaces = value
      }

      Prop("mode") { view: NumberInputView, value: String? ->
        view.propMode = value
      }

      Prop("min") { view: NumberInputView, value: Double? ->
        view.minValue = value
      }

      Prop("max") { view: NumberInputView, value: Double? ->
        view.maxValue = value
      }

      Prop("value") { view: NumberInputView, value: Double? ->
        view.setExternalValue(value)
      }

      OnViewDidUpdateProps { view: NumberInputView ->
        view.updateFormatter()
      }
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
