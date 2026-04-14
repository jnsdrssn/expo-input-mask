# NumberInput Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native number and currency formatting via a new `NumberInput` component and `applyNumberFormat` function, using platform-native formatters.

**Architecture:** New `applyNumberFormat` synchronous function in the existing Swift/Kotlin native modules, using `NumberFormatter` (iOS) and `DecimalFormat` (Android). New `NumberInput` React component following the same controlled-input pattern as `MaskedTextInput`. Existing mask functionality untouched.

**Tech Stack:** Swift (iOS), Kotlin (Android), TypeScript, Expo Modules API, React Native TextInput.

**Spec:** `docs/superpowers/specs/2026-04-14-number-currency-input-design.md`

---

### Task 1: Add TypeScript Types

**Files:**
- Modify: `src/ExpoInputMask.types.ts`

- [ ] **Step 1: Add the new interfaces to the types file**

Append these interfaces after the existing `MaskedTextInputProps` interface in `src/ExpoInputMask.types.ts`:

```typescript
export interface ApplyNumberFormatOptions {
  text: string;
  caretPosition: number;
  caretGravity?: 'forward' | 'backward';
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  min?: number;
  max?: number;
}

export interface NumberFormatResult {
  formattedText: string;
  value: string;
  complete: boolean;
  caretPosition: number;
}

export interface NumberInputProps extends TextInputProps {
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  min?: number;
  max?: number;
  onNumberResult?: (result: {
    formattedText: string;
    value: number | null;
    complete: boolean;
  }) => void;
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build, no errors.

- [ ] **Step 3: Commit**

```bash
git add src/ExpoInputMask.types.ts
git commit -m "feat: add TypeScript types for NumberInput and applyNumberFormat"
```

---

### Task 2: Implement iOS Native `applyNumberFormat`

**Files:**
- Modify: `ios/ExpoInputMaskModule.swift`

- [ ] **Step 1: Add the `ApplyNumberFormatOptions` record struct**

Add this after the existing `ApplyMaskOptions` struct in `ios/ExpoInputMaskModule.swift`:

```swift
struct ApplyNumberFormatOptions: Record {
  @Field var text: String = ""
  @Field var caretPosition: Int = 0
  @Field var caretGravity: String = "forward"
  @Field var locale: String? = nil
  @Field var currency: String? = nil
  @Field var groupingSeparator: String? = nil
  @Field var decimalSeparator: String? = nil
  @Field var decimalPlaces: Int? = nil
  @Field var min: Double? = nil
  @Field var max: Double? = nil
}
```

- [ ] **Step 2: Add the `applyNumberFormat` function**

Add this function inside the `definition()` method of `ExpoInputMaskModule`, after the existing `applyMask` function:

```swift
Function("applyNumberFormat") { (options: ApplyNumberFormatOptions) -> [String: Any] in
  let resolvedLocale: Locale
  if let localeId = options.locale {
    resolvedLocale = Locale(identifier: localeId)
  } else {
    resolvedLocale = Locale.current
  }

  // Determine the effective decimal separator for parsing input
  let effectiveDecimalSeparator: String
  if let ds = options.decimalSeparator {
    effectiveDecimalSeparator = ds
  } else if options.currency != nil {
    let tmpFormatter = NumberFormatter()
    tmpFormatter.locale = resolvedLocale
    tmpFormatter.numberStyle = .currency
    effectiveDecimalSeparator = tmpFormatter.decimalSeparator ?? "."
  } else {
    let tmpFormatter = NumberFormatter()
    tmpFormatter.locale = resolvedLocale
    tmpFormatter.numberStyle = .decimal
    effectiveDecimalSeparator = tmpFormatter.decimalSeparator ?? "."
  }

  // Determine max fractional digits
  let maxFractionDigits: Int
  if let dp = options.decimalPlaces {
    maxFractionDigits = dp
  } else if let currencyCode = options.currency {
    let tmpFormatter = NumberFormatter()
    tmpFormatter.locale = resolvedLocale
    tmpFormatter.numberStyle = .currency
    tmpFormatter.currencyCode = currencyCode
    maxFractionDigits = tmpFormatter.maximumFractionDigits
  } else {
    maxFractionDigits = 2
  }

  // Strip input to digits and decimal separator only
  let inputText = options.text
  var digits = ""
  var hasDecimal = false
  var fractionCount = 0
  let clampedCaret = max(0, min(options.caretPosition, inputText.count))
  var contentCharsBeforeCaret = 0

  for (i, char) in inputText.enumerated() {
    if char.isNumber {
      if hasDecimal {
        if fractionCount >= maxFractionDigits {
          continue
        }
        fractionCount += 1
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

  // Parse the numeric value
  let numericValue: Double?
  if digits.isEmpty {
    numericValue = nil
  } else {
    numericValue = Double(digits)
  }

  // Enforce max constraint: reject input if value exceeds max
  if let val = numericValue, let maxVal = options.max, val > maxVal {
    // Return empty/zero state — caller should keep previous state
    return [
      "formattedText": "",
      "value": "",
      "complete": false,
      "caretPosition": 0,
      "exceeded": true
    ]
  }

  // Configure the formatter
  let formatter = NumberFormatter()
  formatter.locale = resolvedLocale

  if let currencyCode = options.currency {
    formatter.numberStyle = .currency
    formatter.currencyCode = currencyCode
  } else {
    formatter.numberStyle = .decimal
  }

  if let gs = options.groupingSeparator {
    formatter.groupingSeparator = gs
  }
  if let ds = options.decimalSeparator {
    formatter.decimalSeparator = ds
  }

  formatter.minimumFractionDigits = hasDecimal ? min(fractionCount, maxFractionDigits) : 0
  formatter.maximumFractionDigits = maxFractionDigits

  // Format the number
  let formattedText: String
  if let val = numericValue {
    formattedText = formatter.string(from: NSNumber(value: val)) ?? digits
  } else {
    formattedText = ""
  }

  // Caret repositioning: walk the formatted string counting content chars
  var newCaretPosition = formattedText.count
  var contentCount = 0
  for (i, char) in formattedText.enumerated() {
    if char.isNumber || String(char) == (options.decimalSeparator ?? formatter.decimalSeparator ?? ".") {
      contentCount += 1
    }
    if contentCount == contentCharsBeforeCaret {
      newCaretPosition = i + 1
      break
    }
  }

  // Determine completeness based on min/max
  let complete: Bool
  if let val = numericValue {
    let aboveMin = options.min == nil || val >= options.min!
    let belowMax = options.max == nil || val <= options.max!
    complete = aboveMin && belowMax
  } else {
    complete = options.min == nil || options.min! <= 0
  }

  // Raw value string (always uses "." as decimal, no grouping)
  let rawValue: String
  if digits.isEmpty {
    rawValue = ""
  } else {
    rawValue = digits
  }

  return [
    "formattedText": formattedText,
    "value": rawValue,
    "complete": complete,
    "caretPosition": newCaretPosition,
    "exceeded": false
  ]
}
```

- [ ] **Step 3: Build iOS to verify compilation**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/ios && pod install && xcodebuild -workspace ExpoInputMaskExample.xcworkspace -scheme ExpoInputMaskExample -configuration Debug -sdk iphonesimulator -arch arm64 build 2>&1 | tail -5`
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add ios/ExpoInputMaskModule.swift
git commit -m "feat(ios): implement applyNumberFormat using NumberFormatter"
```

---

### Task 3: Implement Android Native `applyNumberFormat`

**Files:**
- Modify: `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt`

- [ ] **Step 1: Add the `ApplyNumberFormatOptions` record class**

Add this after the existing `ApplyMaskOptions` class in the Kotlin file:

```kotlin
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
}
```

- [ ] **Step 2: Add required imports**

Add these imports at the top of the file:

```kotlin
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale as JavaLocale
```

- [ ] **Step 3: Add the `applyNumberFormat` function**

Add this function inside the `definition()` block of `ExpoInputMaskModule`, after the existing `applyMask` function:

```kotlin
Function("applyNumberFormat") { options: ApplyNumberFormatOptions ->
  val resolvedLocale: JavaLocale = if (options.locale != null) {
    JavaLocale.forLanguageTag(options.locale!!.replace("_", "-"))
  } else {
    JavaLocale.getDefault()
  }

  // Determine the effective decimal separator for parsing input
  val baseSymbols = DecimalFormatSymbols.getInstance(resolvedLocale)
  val effectiveDecimalSeparator: Char = if (options.decimalSeparator != null) {
    options.decimalSeparator!!.first()
  } else {
    baseSymbols.decimalSeparator
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

  // Configure the formatter
  val symbols = DecimalFormatSymbols.getInstance(resolvedLocale)
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
  formatter.minimumFractionDigits = if (hasDecimal) minOf(fractionCount, maxFractionDigits) else 0
  formatter.maximumFractionDigits = maxFractionDigits

  // Format
  val formattedText: String = if (numericValue != null) {
    formatter.format(numericValue)
  } else {
    ""
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
```

- [ ] **Step 4: Build Android to verify compilation**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example/android && ./gradlew :expo-input-mask:compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt
git commit -m "feat(android): implement applyNumberFormat using DecimalFormat"
```

---

### Task 4: Add JS Bridge Function

**Files:**
- Modify: `src/ExpoInputMaskModule.ts`

- [ ] **Step 1: Add the `applyNumberFormat` bridge function**

Add this after the existing `applyMask` function in `src/ExpoInputMaskModule.ts`:

```typescript
import type {
  ApplyMaskOptions,
  MaskResult,
  ApplyNumberFormatOptions,
  NumberFormatResult,
} from './ExpoInputMask.types';
```

Update the existing import to include the new types (replacing the old import), then add the function:

```typescript
export function applyNumberFormat(
  options: ApplyNumberFormatOptions
): NumberFormatResult & { exceeded: boolean } {
  const caretPosition = Math.max(
    0,
    Math.min(options.caretPosition, options.text.length)
  );

  return ExpoInputMaskNative.applyNumberFormat({ ...options, caretPosition });
}
```

- [ ] **Step 2: Verify the build compiles**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build, no errors.

- [ ] **Step 3: Commit**

```bash
git add src/ExpoInputMaskModule.ts
git commit -m "feat: add applyNumberFormat JS bridge function"
```

---

### Task 5: Create `NumberInput` Component

**Files:**
- Create: `src/NumberInput.tsx`

- [ ] **Step 1: Create the component file**

Create `src/NumberInput.tsx` following the same controlled-input pattern as `MaskedTextInput.tsx`:

```tsx
import React, {
  useState,
  useRef,
  useCallback,
  useEffect,
  forwardRef,
} from 'react';
import {
  TextInput,
  NativeSyntheticEvent,
  TextInputSelectionChangeEventData,
} from 'react-native';
import { applyNumberFormat } from './ExpoInputMaskModule';
import type { NumberInputProps } from './ExpoInputMask.types';

export const NumberInput = forwardRef<TextInput, NumberInputProps>(
  (
    {
      locale,
      currency,
      groupingSeparator,
      decimalSeparator,
      decimalPlaces,
      min,
      max,
      onNumberResult,
      onChangeText,
      onSelectionChange,
      value,
      ...rest
    },
    ref
  ) => {
    const [formattedText, setFormattedText] = useState('');
    const selectionRef = useRef({ start: 0, end: 0 });
    const previousTextRef = useRef('');
    const previousFormattedRef = useRef('');
    const previousCaretRef = useRef(0);
    const lastRawValueRef = useRef('');
    const innerRef = useRef<TextInput>(null);

    const setRefs = useCallback(
      (instance: TextInput | null) => {
        innerRef.current = instance;
        if (typeof ref === 'function') {
          ref(instance);
        } else if (ref) {
          (ref as React.MutableRefObject<TextInput | null>).current = instance;
        }
      },
      [ref]
    );

    const runFormat = useCallback(
      (text: string, caretPos: number, gravity: 'forward' | 'backward') => {
        return applyNumberFormat({
          text,
          caretPosition: caretPos,
          caretGravity: gravity,
          locale,
          currency,
          groupingSeparator,
          decimalSeparator,
          decimalPlaces,
          min,
          max,
        });
      },
      [locale, currency, groupingSeparator, decimalSeparator, decimalPlaces, min, max]
    );

    // Handle external value prop changes (value = raw numeric string)
    useEffect(() => {
      if (value !== undefined && value !== lastRawValueRef.current) {
        const result = runFormat(value, value.length, 'forward');
        if (!result.exceeded) {
          setFormattedText(result.formattedText);
          previousFormattedRef.current = result.formattedText;
          lastRawValueRef.current = result.value;
        }
      }
    }, [value, runFormat]);

    const handleChangeText = useCallback(
      (text: string) => {
        const gravity: 'forward' | 'backward' =
          text.length < previousTextRef.current.length ? 'backward' : 'forward';
        const caretPos = Math.max(
          0,
          selectionRef.current.start +
            (text.length - previousTextRef.current.length)
        );

        const result = runFormat(text, caretPos, gravity);

        // If max was exceeded, revert to previous state
        if (result.exceeded) {
          setFormattedText(previousFormattedRef.current);
          return;
        }

        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        previousFormattedRef.current = result.formattedText;
        previousCaretRef.current = result.caretPosition;
        lastRawValueRef.current = result.value;

        onChangeText?.(result.value);
        onNumberResult?.({
          formattedText: result.formattedText,
          value: result.value ? parseFloat(result.value) : null,
          complete: result.complete,
        });
      },
      [runFormat, onChangeText, onNumberResult]
    );

    const handleSelectionChange = useCallback(
      (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
        selectionRef.current = event.nativeEvent.selection;
        onSelectionChange?.(event);
      },
      [onSelectionChange]
    );

    return (
      <TextInput
        ref={setRefs}
        {...rest}
        keyboardType={rest.keyboardType ?? 'decimal-pad'}
        value={formattedText}
        onChangeText={handleChangeText}
        onSelectionChange={handleSelectionChange}
      />
    );
  }
);

NumberInput.displayName = 'NumberInput';
```

- [ ] **Step 2: Verify the build compiles**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build, no errors.

- [ ] **Step 3: Commit**

```bash
git add src/NumberInput.tsx
git commit -m "feat: add NumberInput component for number and currency formatting"
```

---

### Task 6: Update Exports

**Files:**
- Modify: `src/index.ts`

- [ ] **Step 1: Add new exports**

Update `src/index.ts` to export the new function, component, and types:

```typescript
export { applyMask, applyNumberFormat } from './ExpoInputMaskModule';
export { MaskedTextInput } from './MaskedTextInput';
export { NumberInput } from './NumberInput';
export type {
  ApplyMaskOptions,
  MaskResult,
  MaskedTextInputProps,
  CustomNotation,
  ApplyNumberFormatOptions,
  NumberFormatResult,
  NumberInputProps,
} from './ExpoInputMask.types';
```

- [ ] **Step 2: Verify the build compiles**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask && npm run build`
Expected: Clean build, no errors.

- [ ] **Step 3: Commit**

```bash
git add src/index.ts
git commit -m "feat: export NumberInput, applyNumberFormat, and new types"
```

---

### Task 7: Add Example Demos

**Files:**
- Modify: `example/App.tsx`

- [ ] **Step 1: Add NumberInput demo inputs**

In `example/App.tsx`, add the import for `NumberInput`:

```typescript
import { MaskedTextInput, NumberInput } from 'expo-input-mask';
```

Add a `NumberDemoInput` component alongside the existing `DemoInput`:

```tsx
function NumberDemoInput({
  label,
  locale,
  currency,
  groupingSeparator,
  decimalSeparator,
  decimalPlaces,
  min,
  max,
  placeholder,
}: {
  label: string;
  locale?: string;
  currency?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  min?: number;
  max?: number;
  placeholder: string;
}) {
  const [rawValue, setRawValue] = useState('');
  const [complete, setComplete] = useState(false);
  const [formatted, setFormatted] = useState('');
  const [numericValue, setNumericValue] = useState<number | null>(null);

  return (
    <View style={styles.card}>
      <Text style={styles.label}>{label}</Text>
      {currency && <Text style={styles.maskLabel}>Currency: {currency}</Text>}
      {locale && <Text style={styles.maskLabel}>Locale: {locale}</Text>}
      <NumberInput
        locale={locale}
        currency={currency}
        groupingSeparator={groupingSeparator}
        decimalSeparator={decimalSeparator}
        decimalPlaces={decimalPlaces}
        min={min}
        max={max}
        placeholder={placeholder}
        style={styles.input}
        onChangeText={setRawValue}
        onNumberResult={(result) => {
          setComplete(result.complete);
          setFormatted(result.formattedText);
          setNumericValue(result.value);
        }}
      />
      <Text style={styles.info}>Formatted: {formatted}</Text>
      <Text style={styles.info}>Raw: {rawValue}</Text>
      <Text style={styles.info}>Value: {numericValue !== null ? numericValue : 'null'}</Text>
      <Text style={[styles.info, complete ? styles.complete : styles.incomplete]}>
        {complete ? 'Complete' : 'Incomplete'}
      </Text>
    </View>
  );
}
```

Add these demo entries after the existing `DemoInput` entries inside the `ScrollView`:

```tsx
<Text style={[styles.title, { marginTop: 20 }]}>NumberInput Demos</Text>

<NumberDemoInput
  label="Plain Number"
  placeholder="1,234,567"
/>

<NumberDemoInput
  label="USD Currency"
  currency="USD"
  locale="en-US"
  placeholder="$0.00"
/>

<NumberDemoInput
  label="EUR Currency (German)"
  currency="EUR"
  locale="de-DE"
  placeholder="0,00 €"
/>

<NumberDemoInput
  label="With Min/Max (0 - 10,000)"
  min={0}
  max={10000}
  placeholder="0 - 10,000"
/>

<NumberDemoInput
  label="Custom: 4 decimal places"
  decimalPlaces={4}
  placeholder="0.0000"
/>
```

- [ ] **Step 2: Test on iOS simulator**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example && npx expo run:ios`
Test:
1. Plain number: type `1234567`, expect `1,234,567`
2. USD: type `1234.56`, expect `$1,234.56`
3. EUR (German): type `1234,56`, expect `1.234,56 €`
4. Min/Max: type `99999`, expect input rejected after `10000`
5. Decimals: type `1.2345`, expect `1.2345`; type 5th decimal digit, expect it rejected
6. Delete digits and verify caret stays in correct position

- [ ] **Step 3: Test on Android emulator**

Run: `cd /Users/jonasderissen/Developer/expo-input-mask/example && npx expo run:android`
Repeat the same tests as Step 2.

- [ ] **Step 4: Commit**

```bash
git add example/App.tsx
git commit -m "feat: add NumberInput demos to example app"
```

---

### Task 8: Final Verification

- [ ] **Step 1: Clean build from scratch**

```bash
cd /Users/jonasderissen/Developer/expo-input-mask && npm run clean && npm run build
```
Expected: Clean build, no errors.

- [ ] **Step 2: Verify all existing mask functionality still works**

Run the example app and test all existing demos (phone, date, credit card, hex color) to confirm no regressions.

- [ ] **Step 3: Verify exports are correct**

Check that the built output in `build/` contains all expected exports:
```bash
grep -E "applyNumberFormat|NumberInput|NumberFormatResult|NumberInputProps|ApplyNumberFormatOptions" build/index.d.ts
```
Expected: All five names appear in the declaration file.
