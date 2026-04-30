# expo-input-mask

Native input masking and locale-aware number/currency formatting for Expo and React Native.

- **`<MaskedTextInput />`** — fixed-pattern masking (phone, date, credit card, ...) powered by RedMadRobot's [input-mask-ios](https://github.com/RedMadRobot/input-mask-ios) and [input-mask-android](https://github.com/RedMadRobot/input-mask-android).
- **`<NumberInput />`** — locale-aware number and currency formatting backed by `NumberFormatter` (iOS) / `DecimalFormat` (Android), with append-only "cents" mode and `min`/`max` constraints.

Both components are native views (Swift/Kotlin) on the Expo Modules API — no bridge overhead, formatting and caret management run on the UI thread.

**iOS and Android only.** Requires Expo SDK 52+.

## Installation

```bash
npx expo install expo-input-mask
```

For bare React Native projects, run `npx pod-install` after installing.

## Quick Start

### Masked text — phone

```tsx
import { MaskedTextInput } from 'expo-input-mask';

function PhoneInput() {
  const [phone, setPhone] = useState('');

  return (
    <MaskedTextInput
      mask="+1 ([000]) [000]-[00][00]"
      placeholder="+1 (___) ___-____"
      keyboardType="phone-pad"
      onChangeText={setPhone} // receives extracted value: "2345678900"
      onMaskResult={({ complete }) => {
        // complete is true when all required mask characters are filled
      }}
    />
  );
}
```

### Number / currency

```tsx
import { NumberInput } from 'expo-input-mask';

function PriceInput() {
  const [value, setValue] = useState<number | null>(null);

  return (
    <NumberInput
      currency="USD"
      locale="en-US"
      placeholder="$0.00"
      value={value}
      onValueChange={(r) => setValue(r.value)}
      // r.value     → parsed number (or null when empty)
      // r.formattedText → display text (e.g. "$1,234.56")
      // r.rawValue  → dot-canonical string (e.g. "1234.56")
      // r.complete  → true when within min/max
    />
  );
}
```

## API

### `<MaskedTextInput />`

A drop-in `TextInput` replacement that applies a mask on every keystroke. Accepts all standard `TextInput` props plus:

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `mask` | `string` | **required** | Mask format string |
| `affinityMasks` | `string[]` | — | Alternative masks for affinity-based selection |
| `affinityStrategy` | `'whole_string' \| 'prefix' \| 'capacity' \| 'extracted_value_capacity'` | `'whole_string'` | How to pick the best mask when using `affinityMasks` |
| `autocomplete` | `boolean` | `true` | Auto-insert mask literals as the user types |
| `autoskip` | `boolean` | `false` | Skip trailing mask literals on deletion |
| `customNotations` | `CustomNotation[]` | — | Define custom mask characters |
| `onMaskResult` | `(result) => void` | — | Callback with `{ formattedText, extractedValue, complete }` |

`onChangeText` receives the **extracted (unmasked) value**, not the formatted text. Use `onMaskResult` to get both.

### `applyMask(options)`

A synchronous function for applying a mask without a component. Useful for formatting values programmatically.

```ts
import { applyMask } from 'expo-input-mask';

const result = applyMask({
  primaryFormat: '+1 ([000]) [000]-[00][00]',
  text: '2345678900',
  caretPosition: 10,
});
// result.formattedText === '+1 (234) 567-8900'
// result.extractedValue === '2345678900'
// result.complete === true
```

Full options:

```ts
applyMask({
  primaryFormat: string,
  text: string,
  caretPosition: number,
  caretGravity?: 'forward' | 'backward',  // default: 'forward'
  autocomplete?: boolean,                   // default: true
  autoskip?: boolean,                       // default: false
  affinityFormats?: string[],
  affinityStrategy?: 'whole_string' | 'prefix' | 'capacity' | 'extracted_value_capacity',
  customNotations?: CustomNotation[],
})
```

### `<NumberInput />`

A native numeric input with locale-aware grouping/decimal separators, optional currency formatting, and `min`/`max` constraints. Inherits `View` props (style, layout, accessibility) but **not** the full `TextInput` surface — `value`, `onChangeText`, `onChange`, and `keyboardType` are re-declared below.

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `locale` | `string` | device locale | BCP-47 locale tag, e.g. `'en-US'`, `'de-DE'` |
| `currency` | `string` | — | ISO-4217 code, e.g. `'USD'`, `'EUR'`, `'JPY'`. Drives prefix/suffix and default fraction digits |
| `decimalPlaces` | `number` | currency default, else `2` | Max fractional digits |
| `groupingSeparator` | `string` | locale default | Override the thousands separator |
| `decimalSeparator` | `string` | locale default | Override the decimal separator |
| `mode` | `'decimal' \| 'cents'` | `'decimal'` | `'decimal'`: free-form digit + separator entry. `'cents'`: append-only — typing `123` with `decimalPlaces: 2` renders as `1.23` |
| `min` | `number` | — | Lower bound. Affects only `complete` flag |
| `max` | `number` | — | Upper bound. Keystrokes that would exceed it are rejected |
| `value` | `number \| null` | — | Controlled value. `null` clears; `undefined` (or omitted) leaves the field untouched. Updates while focused are ignored to avoid races with active typing |
| `onChangeText` | `(formatted: string) => void` | — | Fires with the **display-formatted** text (matches `<TextInput />`). For raw / parsed forms, use `onValueChange` |
| `onValueChange` | `(result) => void` | — | Fires with `{ value, formattedText, rawValue, complete }` on every change |
| `keyboardType` | `'decimal-pad' \| 'numeric' \| 'number-pad'` | `'decimal-pad'` | Narrowed for numeric input |

The component also exposes an imperative ref:

```tsx
import { NumberInput, type NumberInputRef } from 'expo-input-mask';

const ref = useRef<NumberInputRef>(null);
// ref.current?.focus();
// ref.current?.blur();
// ref.current?.clear();
```

**Notes**

- Integer digits are capped at 15 (Double exact-integer precision). Excess input is silently dropped.
- Typing a leading `.` (or `,` in de-DE) auto-prepends `0`: `.5` renders as `0.5`.
- A trailing decimal separator is preserved with the currency suffix in place: typing `123,` in de-DE EUR renders as `123, €`.
- An empty field is reported as `complete: true` when `min` is omitted or `≤ 0` (i.e. zero satisfies the bound). With `min > 0`, an empty field is `complete: false`.

### `applyNumberFormat(options)`

Standalone formatter for one-off use, mirroring `applyMask`. Useful when you need to render a number in a row, label, or non-input context.

```ts
import { applyNumberFormat } from 'expo-input-mask';

const result = applyNumberFormat({
  text: '1234.56',
  caretPosition: 7,
  locale: 'de-DE',
  currency: 'EUR',
});
// result.formattedText === '1.234,56 €'
// result.value === '1234.56'   // dot-canonical
// result.complete === true
```

Full options:

```ts
applyNumberFormat({
  text: string,
  caretPosition: number,
  locale?: string,
  currency?: string,
  groupingSeparator?: string,
  decimalSeparator?: string,
  decimalPlaces?: number,
  fixedDecimalPlaces?: boolean, // pad with trailing zeros
  min?: number,
  max?: number,
})
```

## Mask Format Syntax

This library uses RedMadRobot's mask notation. The format string defines fixed literals and variable character slots inside `[]` brackets.

### Built-in characters (inside `[]`)

| Character | Accepts | Required |
|-----------|---------|----------|
| `0` | Digit (0-9) | Yes |
| `9` | Digit (0-9) | No (optional) |
| `A` | Letter (a-z, A-Z) | Yes |
| `a` | Letter (a-z, A-Z) | No (optional) |
| `_` | Alphanumeric | Yes |
| `-` | Alphanumeric | No (optional) |

### Fixed literals

Characters outside `[]` are literal — inserted automatically:

- `+1 ([000]) [000]-[00][00]` — the `+1 (`, `) `, `-` are literal
- `{/}` — curly braces escape a literal inside brackets: `[00]{/}[00]{/}[0000]` produces `12/25/2026`

### Examples

| Use Case | Mask | Input | Output |
|----------|------|-------|--------|
| US Phone | `+1 ([000]) [000]-[00][00]` | `2345678900` | `+1 (234) 567-8900` |
| Date | `[00]{/}[00]{/}[0000]` | `12252026` | `12/25/2026` |
| Credit Card | `[0000] [0000] [0000] [0000]` | `4111111111111111` | `4111 1111 1111 1111` |
| Time | `[00]:[00]` | `1430` | `14:30` |

### Affinity (multiple masks)

Use `affinityMasks` to automatically pick the best mask for the input:

```tsx
<MaskedTextInput
  mask="[0000] [0000] [0000] [0000]"           // Visa (16 digits)
  affinityMasks={['[0000] [000000] [00000]']}  // Amex (15 digits)
  affinityStrategy="whole_string"
  keyboardType="numeric"
/>
```

### Custom notations

Define your own mask characters:

```tsx
<MaskedTextInput
  mask="[HH]:[HH]:[HH]"
  customNotations={[{
    character: 'H',
    characterSet: '0123456789ABCDEFabcdef',
    isOptional: false,
  }]}
/>
// Accepts hex input like "FF:00:AA"
```

## Credits

Mask parsing and formatting powered by RedMadRobot's excellent libraries:
- [input-mask-ios](https://github.com/RedMadRobot/input-mask-ios) (Swift)
- [input-mask-android](https://github.com/RedMadRobot/input-mask-android) (Kotlin)

Number formatting backed by Foundation's `NumberFormatter` (iOS) and `java.text.DecimalFormat` (Android).

## License

MIT
