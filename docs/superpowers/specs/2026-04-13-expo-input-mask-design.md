# expo-input-mask — Design Spec

## Overview

An Expo native module wrapping RedMadRobot's input-mask libraries (iOS and Android). Exposes a synchronous `applyMask` function and a `MaskedTextInput` React component. iOS and Android only — no web support.

## Architecture: Synchronous Native Function + JS Component

- `applyMask` is a **synchronous** native call via expo-modules-core's `Function`
- `MaskedTextInput` is a JS component wrapping RN's `TextInput`, calling `applyMask` on each keystroke
- No native view components — the component is pure JS leveraging the native function

## Native API Surface (Source of Truth)

### iOS (InputMask pod)

```swift
// Mask
Mask.getOrCreate(withFormat: String, customNotations: [Notation] = []) throws -> Mask
Mask.apply(toText: CaretString) -> Mask.Result

// Result
struct Result {
    let formattedText: CaretString
    let extractedValue: String
    let affinity: Int
    let complete: Bool
    let tailPlaceholder: String
}

// CaretString
struct CaretString {
    let string: String
    let caretPosition: String.Index
    let caretGravity: CaretGravity
}

// CaretGravity
enum CaretGravity {
    case forward(autocomplete: Bool)
    case backward(autoskip: Bool)
}

// Notation
struct Notation {
    let character: Character
    let characterSet: CharacterSet  // Foundation CharacterSet
    let isOptional: Bool
}

// AffinityCalculationStrategy
enum AffinityCalculationStrategy {
    case wholeString, prefix, capacity, extractedValueCapacity
}
```

### Android (com.redmadrobot:input-mask-android)

```kotlin
// Mask
Mask.getOrCreate(format: String, customNotations: List<Notation>): Mask
Mask.apply(text: CaretString): Result

// Result
data class Result(
    val formattedText: CaretString,
    val extractedValue: String,
    val affinity: Int,
    val complete: Boolean,
    val tailPlaceholder: String
)

// CaretString
data class CaretString(
    val string: String,
    val caretPosition: Int,
    val caretGravity: CaretGravity
)

// CaretGravity
sealed class CaretGravity {
    class FORWARD(val autocompleteValue: Boolean) : CaretGravity()
    class BACKWARD(val autoskipValue: Boolean) : CaretGravity()
}

// Notation
data class Notation(
    val character: Char,
    val characterSet: String,  // plain string of acceptable chars
    val isOptional: Boolean
)

// AffinityCalculationStrategy
enum class AffinityCalculationStrategy {
    WHOLE_STRING, PREFIX, CAPACITY, EXTRACTED_VALUE_CAPACITY
}
```

### Key Platform Differences

| Aspect | iOS | Android |
|--------|-----|---------|
| Caret position type | `String.Index` | `Int` |
| Notation characterSet | `CharacterSet` | `String` |
| CaretGravity | enum with associated values | sealed class |
| Mask creation | throws | does not throw |

## Module: `applyMask` Function

### TypeScript Signature

```ts
applyMask(options: {
  primaryFormat: string;
  text: string;
  caretPosition: number;
  caretGravity?: 'forward' | 'backward';  // default 'forward'
  autocomplete?: boolean;                   // default true
  autoskip?: boolean;                       // default false
  affinityFormats?: string[];
  affinityStrategy?: 'whole_string' | 'prefix' | 'capacity' | 'extracted_value_capacity';
  customNotations?: Array<{
    character: string;
    characterSet: string;
    isOptional?: boolean;
  }>;
}): {
  formattedText: string;
  extractedValue: string;
  complete: boolean;
  caretPosition: number;
  affinityOfPrimaryFormat?: number;
}
```

### Native Implementation Details

1. **customNotations mapping**
   - iOS: Convert `characterSet` string to `CharacterSet(charactersIn:)`, `character` string to `Character`
   - Android: Pass `characterSet` string directly, `character` string to `Char`

2. **Mask caching** — Use `Mask.getOrCreate` on both platforms (built-in LRU cache)

3. **CaretString construction**
   - iOS: Convert integer `caretPosition` to `String.Index` via `string.index(string.startIndex, offsetBy: min(caretPosition, string.count))`
   - Android: Pass integer directly, clamped to `0..string.length`

4. **CaretGravity mapping**
   - `'forward'` → iOS `.forward(autocomplete: autocomplete)` / Android `FORWARD(autocomplete)`
   - `'backward'` → iOS `.backward(autoskip: autoskip)` / Android `BACKWARD(autoskip)`

5. **Affinity handling**
   - If `affinityFormats` provided: create mask for primary + each affinity format, apply all, pick highest affinity using specified strategy
   - Return result from winning mask, plus primary mask's affinity as `affinityOfPrimaryFormat`
   - Strategy mapping: `'whole_string'` → `.wholeString`/`WHOLE_STRING`, etc.

6. **Return value**
   - `formattedText`: the formatted string from the winning mask's result
   - `extractedValue`: extracted value string
   - `complete`: boolean completeness flag
   - `caretPosition`: integer caret position (iOS: convert `String.Index` back to integer offset)
   - `affinityOfPrimaryFormat`: only present when `affinityFormats` was provided

## Component: `MaskedTextInput`

### Props

```ts
interface MaskedTextInputProps extends TextInputProps {
  mask: string;
  affinityMasks?: string[];
  affinityStrategy?: 'whole_string' | 'prefix' | 'capacity' | 'extracted_value_capacity';
  autocomplete?: boolean;       // default true
  autoskip?: boolean;           // default false
  customNotations?: Array<{ character: string; characterSet: string; isOptional?: boolean }>;
  onMaskResult?: (result: { formattedText: string; extractedValue: string; complete: boolean }) => void;
}
```

### Behavior

1. **State management** — Internal state: `formattedText` (displayed), `caretPosition`. The component manages its own display text.

2. **Keystroke handling** — On `onChangeText` from underlying `TextInput`:
   - Determine caret gravity: new text shorter than previous formatted text → `'backward'`, otherwise → `'forward'`
   - Read caret position from `onSelectionChange` (tracked in ref, no re-renders)
   - Call `applyMask` synchronously
   - Update internal `formattedText` and `caretPosition`
   - Call parent's `onChangeText(result.extractedValue)`
   - Call `onMaskResult(result)` if provided

3. **Caret positioning** — Set `selection` prop on `TextInput` to mask's returned `caretPosition`. Use `requestAnimationFrame` to ensure it applies after React re-render.

4. **Ref forwarding** — `React.forwardRef` passes ref to inner `TextInput`

5. **Props passthrough** — All `TextInput` props forwarded. Component intercepts `onChangeText`, `onSelectionChange`, `value`, `selection`.

6. **External `value` prop** — Treated as extracted (unmasked) value. On mount or external change, run through `applyMask` to produce formatted display.

## Project Structure

```
expo-input-mask/
├── src/
│   ├── index.ts                    # public API: applyMask, MaskedTextInput
│   ├── ExpoInputMask.types.ts      # TypeScript types
│   ├── ExpoInputMaskModule.ts      # requireNativeModule binding
│   └── MaskedTextInput.tsx         # React component
├── ios/
│   ├── ExpoInputMaskModule.swift   # Swift module
│   └── ExpoInputMask.podspec
├── android/
│   ├── src/main/java/expo/modules/inputmask/
│   │   └── ExpoInputMaskModule.kt  # Kotlin module
│   └── build.gradle
├── expo-module.config.json
├── package.json
└── tsconfig.json
```

## Dependencies

### iOS
- `InputMask` pod (~> 7.0)
- `expo-modules-core` (via ExpoModulesCore pod)
- Swift 5.0+, iOS 13.4+

### Android
- `com.redmadrobot:input-mask-android:7.2.4` (Maven Central)
- `expo-modules-core`
- Kotlin, minSdk 23

### JS
- Peer dependencies: `expo`, `react`, `react-native`

## Example App

`example/` — Expo project demonstrating:

1. **Phone number** — mask `+1 ([000]) [000]-[00][00]`
2. **Date** — mask `[00]{/}[00]{/}[0000]`
3. **Credit card with affinity** — Visa `[0000] [0000] [0000] [0000]` vs Amex `[0000] [000000] [00000]`, strategy `whole_string`
4. **Custom notation** — Hex input `[HH]:[HH]:[HH]` where H accepts `0123456789ABCDEFabcdef`

Each input shows `extractedValue` and `complete` status below it in real time. Minimal setup: `App.tsx` with `ScrollView` of the four demo inputs.
