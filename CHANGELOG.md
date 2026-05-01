# Changelog

## 0.3.0

Splits `<NumberInput />` into two focused components. `<NumberInput />` is now strictly a non-currency numeric input; `<CurrencyInput />` is the new currency-formatted variant with required `currency` and minor-units payload. The native primitive is unchanged — both JS components route to the same view; the split is type-level.

### Breaking changes

- **`<NumberInput />` no longer accepts `currency` or `mode`.** Use `<CurrencyInput />` for currency formatting.
- **`NumberValueResult` no longer includes `minorUnits`.** It only ships on `CurrencyValueResult` (the `<CurrencyInput />` payload).
- **`applyNumberFormat()` no longer accepts `currency`** and its return type (`NumberFormatResult`) no longer includes `minorUnits`. Use `applyCurrencyFormat()` for currency formatting. (Note: passing `currency` at runtime via `as any` is also defensively stripped — `applyNumberFormat` will produce plain decimal output even if you bypass TypeScript.)
- **Migration:** if you used `<NumberInput currency="USD" />`, change the import to `<CurrencyInput currency="USD" />` and the result type from `NumberValueResult` to `CurrencyValueResult`. Same for `applyNumberFormat({ currency: 'USD', ... })` → `applyCurrencyFormat({ currency: 'USD', ... })`.

### Added

- **`<CurrencyInput />`** component. Required `currency: string`. Supports `mode: 'decimal' | 'cents'`. Emits `CurrencyValueResult` (extends `NumberValueResult` with `minorUnits: number | null`). Same imperative ref shape (`focus()` / `blur()` / `clear()`) as `<NumberInput />`.
- **`applyCurrencyFormat(options)`** — required `currency`, returns `CurrencyFormatResult` (with `minorUnits`).
- New types exported: `CurrencyInputProps`, `CurrencyValueResult`, `CurrencyFormatResult`, `ApplyCurrencyFormatOptions`.

## 0.2.2

### Added

- `NumberValueResult.minorUnits` and `NumberFormatResult.minorUnits` — the value as an integer in the smallest unit (cents for USD/EUR, ¥ for JPY, fils for BHD). Computed natively from the dot-canonical raw value via string concatenation, so no floating-point error. Useful for posting to payment APIs (Stripe, Adyen, Braintree) that take amounts in minor units. `null` when the field is empty or the input was rejected for exceeding `max`.

## 0.2.1

### Fixed

- `NumberInput` no longer applies opinionated 12pt/dp horizontal padding inside the native text field. Consumers now control all spacing via `<NumberInput style={...} />` and any wrapping `View`, matching `<TextInput />`'s behavior. If you relied on the implicit padding, add `paddingHorizontal: 12` (or your preferred value) to the component's style.

## 0.2.0

Breaking redesign of `NumberInput`. The component is now a native view (Swift on iOS, Kotlin on Android) instead of a JS wrapper around `<TextInput />`. All formatting and caret management happen natively for parity and to remove cross-thread races.

### Breaking changes

- `NumberInput` is a native view. The exported component shape and prop names have changed; v0.1 imports of `NumberInput` will not type-check.
- `value` is now `number | null` (was `string`). Pass `null` (or omit) to clear.
- `onChangeText` payload changed: in v0.1 it received the raw numeric string (e.g. `"1234.56"`); in v0.2 it receives the **display-formatted** text (e.g. `"$1,234.56"`, `"1.234,56 €"`). This matches the `<TextInput />` convention. **Migration:** if you were treating `onChangeText`'s arg as a parseable number, switch to `onValueChange` and read `result.rawValue` (dot-canonical) or `result.value` (parsed `number | null`).
- `onNumberResult` is renamed to `onValueChange`. The payload now includes `rawValue` (dot-canonical string) and `value` (`number | null`) alongside `formattedText` and `complete`.
- `fixedDecimalPlaces?: boolean` (component prop) is replaced by `mode: 'decimal' | 'cents'`. `'cents'` is the new append-only digit-entry mode (typing `123` with `decimalPlaces: 2` → `1.23`). `fixedDecimalPlaces` survives only on the standalone `applyNumberFormat` function.
- `keyboardType` is narrowed to `'decimal-pad' | 'numeric' | 'number-pad'`.
- `NumberInputProps` no longer extends `TextInputProps` wholesale — `value`, `onChangeText`, `onChange`, and `keyboardType` are omitted from the inherited surface and re-declared.

### Added

- `NumberInputRef` with `focus()`, `blur()`, `clear()` imperative methods.
- `mode: 'cents'` for fixed-fraction append-only entry.
- `min` / `max` constraint props with `complete` flag in the value payload.
- Locale-aware grouping/decimal separators with `groupingSeparator` / `decimalSeparator` overrides.
- 15-digit integer cap (Double exact-integer precision limit). Excess digits past the cap are silently dropped.
- Implicit leading zero: typing `.5` renders as `0.5`.
- Trailing decimal-separator parity: `123,` in de-DE EUR renders as `123, €` (currency suffix stays in place).
- Algorithm extracted to `NumberFormattingAlgorithm` on both platforms with shared test matrix.

### Removed

- v1 JS-layer `NumberInput` implementation.
