# NumberInput Native View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `NumberInput` as a native Expo Module View backed by `UITextField` (iOS) / `EditText` (Android) with RedMadRobot's `NumberInputListener` handling all formatting and caret positioning natively.

**Architecture:** Each platform gets a native view file (`NumberInputView`) that subclasses/wraps the platform text field and attaches `NumberInputListener`. The module definition adds a `View` block alongside the existing `Function` blocks. The JS component becomes a thin wrapper around `requireNativeView`. All existing v1 JS caret/formatting logic is deleted.

**Tech Stack:** Swift + InputMask (iOS), Kotlin + input-mask-android (Android), Expo Modules View API, TypeScript.

**Spec:** `docs/superpowers/specs/2026-04-15-number-input-native-view-design.md`

---

### Task 1: Delete v1 JS NumberInput Implementation

**Files:**
- Modify: `src/NumberInput.tsx`
- Modify: `src/ExpoInputMask.types.ts`

The current `NumberInput.tsx` is ~170 lines of JS caret management that we're replacing entirely. Clean it out first so we're building fresh.

- [ ] **Step 1: Replace NumberInput.tsx with a placeholder**

Replace the entire content of `src/NumberInput.tsx` with:

```tsx
import React from 'react';
import { View, Text } from 'react-native';

// Placeholder — will be replaced with native view in subsequent tasks
export const NumberInput = React.forwardRef<View, any>((props, ref) => {
  return (
    <View ref={ref}>
      <Text>NumberInput: native view not yet implemented</Text>
    </View>
  );
});

NumberInput.displayName = 'NumberInput';
```

- [ ] **Step 2: Update NumberInputProps in types file**

Replace the `NumberInputProps` interface in `src/ExpoInputMask.types.ts` with:

```typescript
export interface NumberInputProps {
  // Text field props
  placeholder?: string;
  editable?: boolean;
  textAlign?: 'left' | 'center' | 'right';
  keyboardType?: 'default' | 'numeric' | 'decimal-pad' | 'number-pad';
  returnKeyType?: 'done' | 'go' | 'next' | 'search' | 'send' | 'default';

  // Number formatting
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  fixedDecimalPlaces?: boolean;

  // Constraints
  min?: number;
  max?: number;

  // Value (controlled mode)
  value?: string;

  // Callbacks
  onChangeText?: (value: string) => void;
  onNumberResult?: (result: {
    formattedText: string;
    value: number | null;
    complete: boolean;
  }) => void;
  onFocus?: () => void;
  onBlur?: () => void;

  // Layout
  style?: any;
}
```

- [ ] **Step 3: Verify build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build.

- [ ] **Step 4: Commit**

```bash
git add src/NumberInput.tsx src/ExpoInputMask.types.ts
git commit -m "refactor: strip v1 NumberInput JS implementation, update types for native view"
```

---

### Task 2: iOS Native View — NumberInputView

**Files:**
- Create: `ios/NumberInputView.swift`

- [ ] **Step 1: Create the native view file**

Create `ios/NumberInputView.swift`:

```swift
import ExpoModulesCore
import InputMask
import UIKit

class NumberInputView: UITextField, UITextFieldDelegate {
  let onChangeText = EventDispatcher()
  let onNumberResult = EventDispatcher()
  let onFocusEvent = EventDispatcher()
  let onBlurEvent = EventDispatcher()

  private var numberListener: NumberInputListener!
  private var previousValue: String = ""
  private var minValue: Double?
  private var maxValue: Double?

  required init(appContext: AppContext? = nil) {
    super.init(frame: .zero)
    setupListener()
    self.keyboardType = .decimalPad
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    setupListener()
  }

  private func setupListener() {
    numberListener = NumberInputListener(
      primaryFormat: "",
      autocomplete: false,
      autocompleteOnFocus: false,
      autoskip: false,
      rightToLeft: false,
      affineFormats: [],
      affinityCalculationStrategy: .wholeString,
      customNotations: [],
      onMaskedTextChangedCallback: { [weak self] textInput, extractedValue, complete, _ in
        guard let self = self else { return }

        // Parse numeric value from extracted digits
        let numericValue = self.parseNumericValue(from: extractedValue)

        // Enforce max constraint
        if let val = numericValue, let maxVal = self.maxValue, val > maxVal {
          // Revert to previous text
          self.text = self.previousValue
          if let pos = self.position(from: self.endOfDocument, offset: 0) {
            self.selectedTextRange = self.textRange(from: pos, to: pos)
          }
          return
        }

        let formattedText = (textInput as? UITextField)?.text ?? ""
        self.previousValue = formattedText

        // Check completeness
        let isComplete: Bool
        if let val = numericValue {
          let aboveMin = self.minValue == nil || val >= self.minValue!
          let belowMax = self.maxValue == nil || val <= self.maxValue!
          isComplete = aboveMin && belowMax
        } else {
          isComplete = self.minValue == nil || self.minValue! <= 0
        }

        self.onChangeText([
          "text": extractedValue
        ])
        self.onNumberResult([
          "formattedText": formattedText,
          "value": numericValue as Any,
          "complete": isComplete
        ])
      },
      allowSuggestions: false
    )
    numberListener.atomicCaretMovement = true
    self.delegate = numberListener
  }

  private func parseNumericValue(from extractedValue: String) -> Double? {
    guard !extractedValue.isEmpty else { return nil }
    // extractedValue contains only digits from the mask — we need to reconstruct
    // the number from the formatted text
    let text = self.text ?? ""
    let decSep = numberListener.formatter?.decimalSeparator ?? "."
    let filtered = text.filter { $0.isNumber || String($0) == decSep }
    let normalized = filtered.replacingOccurrences(of: decSep, with: ".")
    return Double(normalized)
  }

  func updateFormatter(
    locale: String?,
    currency: String?,
    groupingSeparator: String?,
    decimalSeparator: String?,
    decimalPlaces: Int?,
    fixedDecimalPlaces: Bool?
  ) {
    let formatter = NumberFormatter()
    let resolvedLocale = locale != nil ? Locale(identifier: locale!) : Locale.current
    formatter.locale = resolvedLocale

    if let currencyCode = currency {
      formatter.numberStyle = .currency
      formatter.currencyCode = currencyCode
    } else {
      formatter.numberStyle = .decimal
    }

    if let gs = groupingSeparator {
      formatter.groupingSeparator = gs
    }
    if let ds = decimalSeparator {
      formatter.decimalSeparator = ds
    }
    if let dp = decimalPlaces {
      formatter.maximumFractionDigits = dp
    }
    if fixedDecimalPlaces == true {
      formatter.minimumFractionDigits = formatter.maximumFractionDigits
    }
    formatter.roundingMode = .floor

    numberListener.formatter = formatter
  }

  func setMinMax(min: Double?, max: Double?) {
    self.minValue = min
    self.maxValue = max
  }

  func setExternalValue(_ value: String) {
    numberListener.put(text: value, into: self, autocomplete: false)
  }

  // MARK: - Focus events (forwarded from delegate)

  override func becomeFirstResponder() -> Bool {
    let result = super.becomeFirstResponder()
    if result {
      onFocusEvent([:])
    }
    return result
  }

  override func resignFirstResponder() -> Bool {
    let result = super.resignFirstResponder()
    if result {
      onBlurEvent([:])
    }
    return result
  }
}
```

- [ ] **Step 2: Verify iOS build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/ios && pod install && xcodebuild -workspace ExpoInputMaskExample.xcworkspace -scheme expoinputmaskexample -configuration Debug -sdk iphonesimulator -arch arm64 build 2>&1 | tail -3`
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add ios/NumberInputView.swift
git commit -m "feat(ios): add NumberInputView backed by UITextField + NumberInputListener"
```

---

### Task 3: iOS Module — Register the View

**Files:**
- Modify: `ios/ExpoInputMaskModule.swift`

- [ ] **Step 1: Add the View definition to the module**

Inside the `definition()` method of `ExpoInputMaskModule`, after the last `Function(...)` block (after `applyNumberFormat`), add:

```swift
View(NumberInputView.self) {
  Events(
    "onChangeText",
    "onNumberResult",
    "onFocusEvent",
    "onBlurEvent"
  )

  Prop("placeholder") { (view: NumberInputView, value: String?) in
    view.placeholder = value
  }

  Prop("editable") { (view: NumberInputView, value: Bool?) in
    view.isUserInteractionEnabled = value ?? true
  }

  Prop("textAlign") { (view: NumberInputView, value: String?) in
    switch value {
    case "center":
      view.textAlignment = .center
    case "right":
      view.textAlignment = .right
    default:
      view.textAlignment = .natural
    }
  }

  Prop("keyboardType") { (view: NumberInputView, value: String?) in
    switch value {
    case "numeric", "number-pad":
      view.keyboardType = .numberPad
    case "decimal-pad":
      view.keyboardType = .decimalPad
    default:
      view.keyboardType = .decimalPad
    }
  }

  Prop("returnKeyType") { (view: NumberInputView, value: String?) in
    switch value {
    case "go": view.returnKeyType = .go
    case "next": view.returnKeyType = .next
    case "search": view.returnKeyType = .search
    case "send": view.returnKeyType = .send
    case "done": view.returnKeyType = .done
    default: view.returnKeyType = .default
    }
  }

  Prop("locale") { (view: NumberInputView, value: String?) in
    view.updateFormatter(
      locale: value,
      currency: nil, groupingSeparator: nil,
      decimalSeparator: nil, decimalPlaces: nil,
      fixedDecimalPlaces: nil
    )
  }

  PropGroup("locale", "currency", "groupingSeparator", "decimalSeparator", "decimalPlaces", "fixedDecimalPlaces") { (view: NumberInputView, values: [String: Any]) in
    view.updateFormatter(
      locale: values["locale"] as? String,
      currency: values["currency"] as? String,
      groupingSeparator: values["groupingSeparator"] as? String,
      decimalSeparator: values["decimalSeparator"] as? String,
      decimalPlaces: values["decimalPlaces"] as? Int,
      fixedDecimalPlaces: values["fixedDecimalPlaces"] as? Bool
    )
  }

  Prop("min") { (view: NumberInputView, value: Double?) in
    view.setMinMax(min: value, max: nil)
  }

  Prop("max") { (view: NumberInputView, value: Double?) in
    view.setMinMax(min: nil, max: value)
  }

  PropGroup("min", "max") { (view: NumberInputView, values: [String: Any]) in
    view.setMinMax(
      min: values["min"] as? Double,
      max: values["max"] as? Double
    )
  }

  Prop("value") { (view: NumberInputView, value: String?) in
    if let v = value {
      view.setExternalValue(v)
    }
  }
}
```

**Note:** The `PropGroup` approach may not be available in the Expo Modules version used. If `PropGroup` is not available, use individual `Prop` handlers that store values on the view and call `updateFormatter` in `OnViewDidUpdateProps`. Adjust based on what compiles.

- [ ] **Step 2: Verify iOS build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/ios && xcodebuild -workspace ExpoInputMaskExample.xcworkspace -scheme expoinputmaskexample -configuration Debug -sdk iphonesimulator -arch arm64 build 2>&1 | tail -3`
Expected: `** BUILD SUCCEEDED **`

If `PropGroup` does not compile, refactor to individual `Prop` handlers that store values on the view, and add `OnViewDidUpdateProps` to call `updateFormatter` with all stored values.

- [ ] **Step 3: Commit**

```bash
git add ios/ExpoInputMaskModule.swift
git commit -m "feat(ios): register NumberInputView with props and events in module definition"
```

---

### Task 4: Android Native View — NumberInputView

**Files:**
- Create: `android/src/main/java/expo/modules/inputmask/NumberInputView.kt`

- [ ] **Step 1: Create the native view file**

Create `android/src/main/java/expo/modules/inputmask/NumberInputView.kt`:

```kotlin
package expo.modules.inputmask

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import com.redmadrobot.inputmask.MaskedTextChangedListener
import com.redmadrobot.inputmask.NumberInputListener
import android.widget.EditText

class NumberInputView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

  val onChangeText by EventDispatcher()
  val onNumberResult by EventDispatcher()
  val onFocusEvent by EventDispatcher()
  val onBlurEvent by EventDispatcher()

  val editText: EditText = EditText(context).apply {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    background = null
    setPadding(0, 0, 0, 0)
  }

  private var numberListener: NumberInputListener
  private var previousValue: String = ""
  private var minValue: Double? = null
  private var maxValue: Double? = null

  // Store prop values for reconfiguration
  private var propLocale: String? = null
  private var propCurrency: String? = null
  private var propGroupingSeparator: String? = null
  private var propDecimalSeparator: String? = null
  private var propDecimalPlaces: Int? = null
  private var propFixedDecimalPlaces: Boolean? = null

  init {
    addView(editText)

    val valueListener = object : MaskedTextChangedListener.ValueListener {
      override fun onTextChanged(
        complete: Boolean,
        extractedValue: String,
        formattedText: String,
        tailPlaceholder: String
      ) {
        val numericValue = parseNumericValue(formattedText)

        // Enforce max constraint
        if (numericValue != null && maxValue != null && numericValue > maxValue!!) {
          editText.setText(previousValue)
          editText.setSelection(previousValue.length)
          return
        }

        previousValue = formattedText

        val isComplete = if (numericValue != null) {
          val aboveMin = minValue == null || numericValue >= minValue!!
          val belowMax = maxValue == null || numericValue <= maxValue!!
          aboveMin && belowMax
        } else {
          minValue == null || minValue!! <= 0.0
        }

        onChangeText(mapOf("text" to extractedValue))
        onNumberResult(mapOf(
          "formattedText" to formattedText,
          "value" to numericValue,
          "complete" to isComplete
        ))
      }
    }

    numberListener = NumberInputListener(editText, valueListener)
    editText.addTextChangedListener(numberListener)

    editText.setOnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        onFocusEvent(mapOf<String, Any>())
      } else {
        onBlurEvent(mapOf<String, Any>())
      }
    }
  }

  private fun parseNumericValue(formattedText: String): Double? {
    if (formattedText.isEmpty()) return null
    val decSep = numberListener.formatter?.decimalFormatSymbols?.decimalSeparator ?: '.'
    val filtered = formattedText.filter { it.isDigit() || it == decSep }
    val normalized = filtered.replace(decSep, '.')
    return normalized.toDoubleOrNull()
  }

  fun updateFormatter() {
    val resolvedLocale = if (propLocale != null) {
      java.util.Locale.forLanguageTag(propLocale!!.replace("_", "-"))
    } else {
      java.util.Locale.getDefault()
    }

    val formatter = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
      val nb = android.icu.number.NumberFormatter.withLocale(android.icu.util.ULocale.forLocale(resolvedLocale))
      if (propCurrency != null) {
        nb.unit(android.icu.util.Currency.getInstance(propCurrency))
      } else {
        nb
      }
    } else {
      android.icu.number.NumberFormatter.withLocale(android.icu.util.ULocale.forLocale(resolvedLocale))
    }

    numberListener.formatter = formatter
  }

  fun setExternalValue(value: String) {
    numberListener.setText(value)
  }
}
```

- [ ] **Step 2: Verify Android build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/android && ./gradlew :expo-input-mask:compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

This is the trickiest task — the Android `NumberInputListener` uses `android.icu.number.LocalizedNumberFormatter` which has a different API than iOS's `NumberFormatter`. The `formatter` property on Android's `NumberInputListener` is a `LocalizedNumberFormatter`, not `DecimalFormat`. The implementation may need adjustment based on compilation results.

If `LocalizedNumberFormatter` configuration for `groupingSeparator`/`decimalSeparator`/`decimalPlaces`/`fixedDecimalPlaces` overrides is problematic, an alternative is to subclass `NumberInputListener` and override `pickMask` to use `DecimalFormat` directly (similar to how iOS's `NumberInputListener` uses `NumberFormatter`).

- [ ] **Step 3: Commit**

```bash
git add android/src/main/java/expo/modules/inputmask/NumberInputView.kt
git commit -m "feat(android): add NumberInputView backed by EditText + NumberInputListener"
```

---

### Task 5: Android Module — Register the View

**Files:**
- Modify: `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt`

- [ ] **Step 1: Add the View definition to the module**

Inside the `definition()` block of `ExpoInputMaskModule`, after the last `Function(...)` block, add:

```kotlin
View(NumberInputView::class) {
  Events(
    "onChangeText",
    "onNumberResult",
    "onFocusEvent",
    "onBlurEvent"
  )

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
      else -> Gravity.START or Gravity.CENTER_VERTICAL
    }
  }

  Prop("keyboardType") { view: NumberInputView, value: String? ->
    view.editText.inputType = when (value) {
      "numeric", "number-pad" -> InputType.TYPE_CLASS_NUMBER
      else -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
  }

  Prop("returnKeyType") { view: NumberInputView, value: String? ->
    view.editText.imeOptions = when (value) {
      "go" -> EditorInfo.IME_ACTION_GO
      "next" -> EditorInfo.IME_ACTION_NEXT
      "search" -> EditorInfo.IME_ACTION_SEARCH
      "send" -> EditorInfo.IME_ACTION_SEND
      "done" -> EditorInfo.IME_ACTION_DONE
      else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }
  }

  Prop("locale") { view: NumberInputView, value: String? ->
    view.propLocale = value
    view.updateFormatter()
  }

  Prop("currency") { view: NumberInputView, value: String? ->
    view.propCurrency = value
    view.updateFormatter()
  }

  Prop("min") { view: NumberInputView, value: Double? ->
    view.minValue = value
  }

  Prop("max") { view: NumberInputView, value: Double? ->
    view.maxValue = value
  }

  Prop("value") { view: NumberInputView, value: String? ->
    if (value != null) {
      view.setExternalValue(value)
    }
  }
}
```

Note: This requires making `propLocale`, `propCurrency`, `minValue`, `maxValue` public on `NumberInputView`. Adjust visibility modifiers as needed.

- [ ] **Step 2: Add missing imports to ExpoInputMaskModule.kt**

Add at the top of the file:

```kotlin
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
```

- [ ] **Step 3: Verify Android build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/android && ./gradlew :expo-input-mask:compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt android/src/main/java/expo/modules/inputmask/NumberInputView.kt
git commit -m "feat(android): register NumberInputView with props and events in module definition"
```

---

### Task 6: JS Native View Wrapper

**Files:**
- Modify: `src/NumberInput.tsx`

- [ ] **Step 1: Replace placeholder with native view wrapper**

Replace the entire content of `src/NumberInput.tsx` with:

```tsx
import React, { useCallback } from 'react';
import { requireNativeView, NativeModule } from 'expo-modules-core';
import type { NumberInputProps } from './ExpoInputMask.types';
import type { ViewProps } from 'react-native';

interface NativeNumberInputProps extends ViewProps {
  placeholder?: string;
  editable?: boolean;
  textAlign?: string;
  keyboardType?: string;
  returnKeyType?: string;
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  fixedDecimalPlaces?: boolean;
  min?: number;
  max?: number;
  value?: string;
  onChangeText?: (event: { nativeEvent: { text: string } }) => void;
  onNumberResult?: (event: {
    nativeEvent: { formattedText: string; value: number | null; complete: boolean };
  }) => void;
  onFocusEvent?: () => void;
  onBlurEvent?: () => void;
}

const NativeNumberInput =
  requireNativeView<NativeNumberInputProps>('ExpoInputMask');

export const NumberInput = React.forwardRef<
  React.ComponentRef<typeof NativeNumberInput>,
  NumberInputProps
>(
  (
    {
      onChangeText,
      onNumberResult,
      onFocus,
      onBlur,
      ...rest
    },
    ref
  ) => {
    const handleChangeText = useCallback(
      (event: { nativeEvent: { text: string } }) => {
        onChangeText?.(event.nativeEvent.text);
      },
      [onChangeText]
    );

    const handleNumberResult = useCallback(
      (event: {
        nativeEvent: {
          formattedText: string;
          value: number | null;
          complete: boolean;
        };
      }) => {
        onNumberResult?.(event.nativeEvent);
      },
      [onNumberResult]
    );

    return (
      <NativeNumberInput
        ref={ref}
        {...rest}
        onChangeText={handleChangeText}
        onNumberResult={handleNumberResult}
        onFocusEvent={onFocus}
        onBlurEvent={onBlur}
      />
    );
  }
);

NumberInput.displayName = 'NumberInput';
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build.

- [ ] **Step 3: Commit**

```bash
git add src/NumberInput.tsx
git commit -m "feat: replace NumberInput with native view wrapper"
```

---

### Task 7: Update Example App

**Files:**
- Modify: `example/App.tsx`

- [ ] **Step 1: Update NumberDemoInput to use the new props**

The `NumberInput` no longer extends `TextInputProps`, so the example's `NumberDemoInput` needs minor adjustments. The `style` prop still works (passed through to the native view via `ViewProps`). Remove the `fixedDecimalPlaces` prop passthrough from the component and update demos.

Update the `NumberDemoInput` component — replace the `NumberInput` usage to remove any `TextInput`-specific props that are no longer supported. The core props (`locale`, `currency`, `decimalPlaces`, `fixedDecimalPlaces`, `min`, `max`, `placeholder`, `onChangeText`, `onNumberResult`, `style`) all still work.

Also add a `height` to the style since the native view doesn't auto-size like RN's TextInput:

```tsx
<NumberInput
  // ... props
  style={[styles.input, { height: 44 }]}
/>
```

- [ ] **Step 2: Test on iOS simulator**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example && npx expo run:ios`
Test:
1. Plain number: type `1234567`, expect `1,234,567` with correct caret
2. USD: type `1234.56`, expect `$1,234.56`
3. USD fixed decimals: type `1`, expect `$1.00` with cursor after `1`
4. EUR German: type `1234`, verify locale-correct formatting
5. Min/Max: type past 10000, verify rejection
6. Move cursor behind comma, press backspace — verify digit deletion
7. All existing mask demos still work (no regressions)

- [ ] **Step 3: Test on Android emulator**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example && npx expo run:android`
Repeat same tests as Step 2.

- [ ] **Step 4: Commit**

```bash
git add example/App.tsx
git commit -m "feat: update example app for native view NumberInput"
```

---

### Task 8: Final Verification

- [ ] **Step 1: Clean build from scratch**

```bash
cd /Users/jonasderissen/Developer/expo-input-mask && npm run clean && npm run build
```
Expected: Clean build, no errors.

- [ ] **Step 2: Verify exports**

```bash
grep -E "NumberInput|NumberFormatResult|NumberInputProps|ApplyNumberFormatOptions|applyNumberFormat" build/index.d.ts
```
Expected: All names present. `applyNumberFormat` standalone function still exported alongside the new native view component.

- [ ] **Step 3: Verify no regressions on existing mask functionality**

Run the example app and test all mask demos (phone, date, credit card, hex color).
