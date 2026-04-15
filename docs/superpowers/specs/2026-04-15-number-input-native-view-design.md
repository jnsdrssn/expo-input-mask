# NumberInput Native View — Design Spec (v2)

Supersedes: `2026-04-14-number-currency-input-design.md`

## Summary

Rebuild `NumberInput` as a native view using Expo Modules' View API. The native view IS a `UITextField` (iOS) / `EditText` (Android) with RedMadRobot's `NumberInputListener` attached as delegate/TextWatcher. All formatting and caret positioning happens natively in the same frame — no JS round-trip, no React Native `selection` prop.

## Why v2

The v1 approach used a React Native `<TextInput>` wrapper with a native `applyNumberFormat` function called from JS. Formatting worked, but caret positioning required setting the `selection` prop from JS, which races with React Native's internal cursor management. This produced unreliable cursor behavior, especially when formatting causes text length changes (commas shifting, fixed decimal places, currency symbols).

The fix is architectural: the native view owns the text field directly, so formatting + caret repositioning happen in a single native frame, exactly like RedMadRobot's mask listener does for `MaskedTextInput`.

## Architecture

### How RedMadRobot's NumberInputListener Works

`NumberInputListener` is a subclass of `MaskedTextInputListener`. On every keystroke it:

1. Strips the input to digits + decimal separator
2. Formats the integer part with `NumberFormatter` / `LocalizedNumberFormatter` to get grouping (e.g., `12,345`)
3. Converts the formatted result into a **dynamic mask pattern** (e.g., `[1][0]{,}[0][0][0]`)
4. Sets that as `primaryMaskFormat` and delegates to the parent class
5. The parent class applies the mask, computes the caret position, and sets it on the text field — all natively

This means we get caret handling for free from the mask infrastructure. We just need to configure the `NumberFormatter` / `LocalizedNumberFormatter` from our props.

### Native View (iOS)

The Expo Module defines a `View` using a custom `UITextField` subclass. A `NumberInputListener` instance is set as the text field's delegate. The listener's `formatter` property is configured from props (`locale`, `currency`, `groupingSeparator`, `decimalSeparator`, `decimalPlaces`).

The listener's `onMaskedTextChangedCallback` fires an Expo event to JS with `{ formattedText, extractedValue, complete }`.

`min`/`max` enforcement: after the listener formats, check the numeric value against bounds. If `max` is exceeded, revert the text field to its previous value. `min` only affects the `complete` flag.

`fixedDecimalPlaces`: configure `formatter.minimumFractionDigits = formatter.maximumFractionDigits`.

### Native View (Android)

Same pattern. Custom `EditText` subclass with `NumberInputListener` attached. The listener's `formatter` (`LocalizedNumberFormatter`) is configured from props. The `ValueListener` callback fires an Expo event to JS.

### JS Component

A thin wrapper around `requireNativeViewManager`. Maps props, forwards events. Zero formatting logic, zero caret logic.

```tsx
import { requireNativeView } from 'expo-modules-core';

const NativeNumberInput = requireNativeView('ExpoNumberInput');

export function NumberInput(props) {
  // Map onChangeText/onNumberResult to native events
  // Forward all other props directly
  return <NativeNumberInput {...mappedProps} />;
}
```

## API

### `NumberInput` Component

```tsx
interface NumberInputProps {
  // Text field props (mapped to native UITextField/EditText properties)
  placeholder?: string;
  placeholderTextColor?: ColorValue;
  keyboardType?: KeyboardTypeOptions;
  returnKeyType?: ReturnKeyTypeOptions;
  editable?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  autoCorrect?: boolean;
  secureTextEntry?: boolean;
  textAlign?: 'left' | 'center' | 'right';
  style?: StyleProp<TextStyle>;  // font size, color, padding, etc.

  // Focus events
  onFocus?: () => void;
  onBlur?: () => void;

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

  // Value
  value?: string;               // raw numeric string for controlled mode

  // Callbacks
  onChangeText?: (value: string) => void;  // raw numeric string
  onNumberResult?: (result: {
    formattedText: string;
    value: number | null;
    complete: boolean;
  }) => void;
}
```

No longer extends `TextInputProps` — the supported props are explicitly listed. This is clearer than inheriting 100+ props and only supporting a subset.

### `applyNumberFormat` Standalone Function

Stays as-is from v1 for programmatic formatting use. Not used by the component.

## File Structure

**Native:**
- `ios/ExpoInputMaskModule.swift` — add `View` definition for the native number input, keep existing `applyMask` and `applyNumberFormat` functions
- `ios/NumberInputView.swift` — new: `UITextField` subclass with `NumberInputListener` setup
- `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt` — add `View` definition, keep existing functions
- `android/src/main/java/expo/modules/inputmask/NumberInputView.kt` — new: `EditText` subclass with `NumberInputListener` setup

**JS/TS:**
- `src/NumberInput.tsx` — rewrite: thin native view wrapper (delete all current JS caret/formatting logic)
- `src/ExpoInputMask.types.ts` — update `NumberInputProps` (no longer extends TextInputProps)
- `src/index.ts` — exports stay the same

**Example:**
- `example/App.tsx` — update demos

## What This Does NOT Change

- `MaskedTextInput` component — untouched (still wraps RN TextInput, calls applyMask from JS)
- `applyMask` function — untouched
- `applyNumberFormat` standalone function — untouched (useful for programmatic use)
- RedMadRobot InputMask dependency — now used directly for NumberInput (was already a dependency)
