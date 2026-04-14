# Number & Currency Input — Design Spec

## Summary

Add native number and currency formatting to expo-input-mask via a new `NumberInput` component and `applyNumberFormat` native function. Uses platform-native `NumberFormatter` (iOS) and `DecimalFormat` (Android) — no JS-layer formatting, no new dependencies.

## Approach

**Approach A: New native function + new component.** Number formatting is a fundamentally different problem from pattern masking (variable-length vs. fixed-length), so it gets its own native function (`applyNumberFormat`) and its own component (`NumberInput`). The existing `MaskedTextInput` and `applyMask` remain untouched.

Currency and plain number formatting share the same component — currency is just number formatting with a symbol. A single `NumberInput` with an optional `currency` prop handles both.

## API

### `NumberInput` Component

```tsx
interface NumberInputProps extends TextInputProps {
  // Locale & currency
  locale?: string;           // e.g. "en-US", "de-DE". Defaults to device locale
  currency?: string;         // ISO 4217 code: "USD", "EUR", "JPY". Omit for plain number

  // Formatting overrides (override locale defaults)
  groupingSeparator?: string;   // e.g. "," or "."
  decimalSeparator?: string;    // e.g. "." or ","
  decimalPlaces?: number;       // e.g. 2. Defaults to currency's standard (2 for USD, 0 for JPY)

  // Constraints
  min?: number;
  max?: number;

  // Callbacks
  onNumberResult?: (result: {
    formattedText: string;    // "$1,234.56"
    value: number | null;     // 1234.56 (null if empty)
    complete: boolean;        // true if within min/max and valid
  }) => void;
}
```

- `onChangeText` emits the raw numeric string (e.g. `"1234.56"`), consistent with how `MaskedTextInput` emits the extracted value.
- When `currency` is omitted, the input formats plain numbers (e.g. `1,234.56`).
- When `currency` is set, the symbol and placement are determined by locale (e.g. `$1,234.56` for `en-US` + `USD`, `1.234,56 €` for `de-DE` + `EUR`).

### `applyNumberFormat` Native Function

**Input:**

```typescript
interface ApplyNumberFormatOptions {
  text: string;              // raw input text
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
```

**Output:**

```typescript
interface NumberFormatResult {
  formattedText: string;
  value: string;             // raw numeric string, e.g. "1234.56"
  complete: boolean;         // within min/max bounds
  caretPosition: number;
}
```

## Native Implementation

Both platforms follow the same logic:

1. **Strip** everything except digits and the decimal separator from the input text.
2. **Configure** the platform's number formatter (`NumberFormatter` on iOS, `DecimalFormat` on Android) with locale, currency, and any overrides (grouping separator, decimal separator, decimal places).
3. **Format** the parsed number.
4. **Caret repositioning:** Count how many "content" characters (digits + decimal point) precede the old caret position in the input text. Walk the formatted output string to find where that same count of content characters lands. This keeps the cursor stable as grouping separators shift around during typing and deletion.
5. **Enforce constraints:**
   - `max`: If the new value would exceed `max`, reject the input — keep the previous formatted text and caret position.
   - `min`: Cannot be enforced on input (user typing "5" might be heading to "50" when min is 10). Only affects `complete` — set to false if the current value is below `min`.
   - `complete` is true when the value is within `[min, max]` bounds (or no bounds are set).

No third-party dependencies — both platforms use their built-in formatters.

## File Structure

All changes fit into the existing project structure:

**Native:**
- `ios/ExpoInputMaskModule.swift` — add `applyNumberFormat` function alongside existing `applyMask`
- `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt` — same

**JS/TS:**
- `src/ExpoInputMask.types.ts` — add `NumberInputProps`, `ApplyNumberFormatOptions`, `NumberFormatResult`
- `src/ExpoInputMaskModule.ts` — export `applyNumberFormat` bridge call
- `src/NumberInput.tsx` — new component, same controlled-input pattern as `MaskedTextInput.tsx`
- `src/index.ts` — add exports for `NumberInput`, `applyNumberFormat`, and new types

**Example:**
- `example/App.tsx` — add demo inputs for plain number, USD currency, EUR currency

## What This Does NOT Change

- `MaskedTextInput` component — untouched
- `applyMask` function — untouched
- RedMadRobot InputMask dependency — not involved in number formatting
- Existing types (`MaskedTextInputProps`, `ApplyMaskOptions`, `MaskResult`, `CustomNotation`) — untouched
