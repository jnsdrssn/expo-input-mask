# expo-input-mask

Native input masking for Expo and React Native, powered by RedMadRobot's [input-mask-ios](https://github.com/RedMadRobot/input-mask-ios) and [input-mask-android](https://github.com/RedMadRobot/input-mask-android).

Built with the Expo Modules API (Swift/Kotlin) — no bridge overhead, synchronous mask application on every keystroke.

**iOS and Android only.** Requires Expo SDK 52+.

## Installation

```bash
npx expo install expo-input-mask
```

For bare React Native projects, run `npx pod-install` after installing.

## Quick Start

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

## License

MIT
