# Changelog

## 0.2.0

Breaking redesign of `NumberInput`. The component is now a native view (Swift on iOS, Kotlin on Android) instead of a JS wrapper around `<TextInput />`. All formatting and caret management happen natively for parity and to remove cross-thread races.

### Breaking changes

- `NumberInput` is a native view. The exported component shape and prop names have changed; v0.1 imports of `NumberInput` will not type-check.
- `value` is now `number | null` (was `string`). Pass `null` (or omit) to clear.
- `onChangeText` fires with the **display-formatted** text, not the raw numeric string. This matches the `<TextInput />` convention. For the dot-canonical raw string or the parsed number, use `onValueChange`.
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
