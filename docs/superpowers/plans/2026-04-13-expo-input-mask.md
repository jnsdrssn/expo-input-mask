# expo-input-mask Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Expo native module wrapping RedMadRobot's input-mask libraries, exposing a synchronous `applyMask` function and a `MaskedTextInput` React component for iOS and Android.

**Architecture:** Synchronous native function via expo-modules-core `Function` + JS React component wrapping `TextInput`. No native views. The native function handles mask compilation/caching and text formatting; the JS component manages state, caret tracking, and prop forwarding.

**Tech Stack:** TypeScript, Swift (iOS), Kotlin (Android), expo-modules-core, InputMask pod, input-mask-android

---

## File Map

| File | Responsibility |
|------|---------------|
| `package.json` | Package metadata, scripts, peer/dev dependencies |
| `tsconfig.json` | TypeScript config extending expo-module-scripts |
| `expo-module.config.json` | Registers native modules for both platforms |
| `src/ExpoInputMask.types.ts` | All shared TypeScript types |
| `src/ExpoInputMaskModule.ts` | `requireNativeModule` binding + `applyMask` wrapper |
| `src/MaskedTextInput.tsx` | React component wrapping TextInput with masking |
| `src/index.ts` | Public API re-exports |
| `ios/ExpoInputMask.podspec` | CocoaPods spec with InputMask dependency |
| `ios/ExpoInputMaskModule.swift` | Swift module: parses args, calls InputMask, returns result |
| `android/build.gradle` | Gradle config with input-mask-android dependency |
| `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt` | Kotlin module: parses args, calls input-mask-android, returns result |
| `example/package.json` | Example app package |
| `example/app.json` | Expo config |
| `example/tsconfig.json` | TS config |
| `example/metro.config.js` | Metro config resolving parent module |
| `example/babel.config.js` | Babel config |
| `example/App.tsx` | Four demo masked inputs |

---

### Task 1: Project Scaffolding

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `expo-module.config.json`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "expo-input-mask",
  "version": "0.1.0",
  "description": "Expo native module wrapping RedMadRobot input-mask for iOS and Android",
  "main": "src/index.ts",
  "types": "src/index.ts",
  "license": "MIT",
  "scripts": {
    "build": "expo-module build",
    "clean": "expo-module clean",
    "lint": "expo-module lint",
    "test": "expo-module test",
    "prepare": "expo-module prepare",
    "prepublishOnly": "expo-module prepublishOnly",
    "expo-module": "expo-module"
  },
  "peerDependencies": {
    "expo": "*",
    "react": "*",
    "react-native": "*"
  },
  "devDependencies": {
    "expo": "^52.0.0",
    "expo-module-scripts": "^4.0.0",
    "expo-modules-core": "^2.0.0",
    "typescript": "^5.0.0"
  }
}
```

- [ ] **Step 2: Create tsconfig.json**

```json
{
  "extends": "expo-module-scripts/tsconfig.module",
  "compilerOptions": {
    "outDir": "./build"
  },
  "include": ["./src"]
}
```

- [ ] **Step 3: Create expo-module.config.json**

```json
{
  "platforms": ["ios", "android"],
  "ios": {
    "modules": ["ExpoInputMaskModule"]
  },
  "android": {
    "modules": ["expo.modules.inputmask.ExpoInputMaskModule"]
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add package.json tsconfig.json expo-module.config.json
git commit -m "feat: scaffold expo-input-mask project"
```

---

### Task 2: TypeScript Types

**Files:**
- Create: `src/ExpoInputMask.types.ts`

- [ ] **Step 1: Create the types file**

```ts
import type { TextInputProps } from 'react-native';

export interface CustomNotation {
  character: string;
  characterSet: string;
  isOptional?: boolean;
}

export interface ApplyMaskOptions {
  primaryFormat: string;
  text: string;
  caretPosition: number;
  caretGravity?: 'forward' | 'backward';
  autocomplete?: boolean;
  autoskip?: boolean;
  affinityFormats?: string[];
  affinityStrategy?:
    | 'whole_string'
    | 'prefix'
    | 'capacity'
    | 'extracted_value_capacity';
  customNotations?: CustomNotation[];
}

export interface MaskResult {
  formattedText: string;
  extractedValue: string;
  complete: boolean;
  caretPosition: number;
  affinityOfPrimaryFormat?: number;
}

export interface MaskedTextInputProps extends TextInputProps {
  mask: string;
  affinityMasks?: string[];
  affinityStrategy?:
    | 'whole_string'
    | 'prefix'
    | 'capacity'
    | 'extracted_value_capacity';
  autocomplete?: boolean;
  autoskip?: boolean;
  customNotations?: CustomNotation[];
  onMaskResult?: (result: {
    formattedText: string;
    extractedValue: string;
    complete: boolean;
  }) => void;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/ExpoInputMask.types.ts
git commit -m "feat: add TypeScript types for expo-input-mask"
```

---

### Task 3: iOS Native Module

**Files:**
- Create: `ios/ExpoInputMask.podspec`
- Create: `ios/ExpoInputMaskModule.swift`

- [ ] **Step 1: Create the podspec**

```ruby
require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoInputMask'
  s.version        = package['version']
  s.summary        = package['description']
  s.description    = package['description']
  s.license        = package['license']
  s.author         = 'expo-input-mask contributors'
  s.homepage       = 'https://github.com/example/expo-input-mask'
  s.platforms      = { :ios => '13.4' }
  s.swift_version  = '5.4'
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'InputMask'

  s.source_files = '**/*.swift'
end
```

- [ ] **Step 2: Create the Swift module**

This file implements:
1. Record types for structured arguments (`CustomNotationRecord`, `ApplyMaskOptions`)
2. A synchronous `applyMask` Function that:
   - Maps `customNotations` to `Notation` objects (converting `characterSet` string to `CharacterSet`)
   - Calls `Mask.getOrCreate(withFormat:customNotations:)` (cached internally)
   - Builds `CaretString` with correct `CaretGravity` and `String.Index` caret position
   - Handles affinity by creating multiple masks, computing affinity via `AffinityCalculationStrategy`, and picking the best
   - Returns a dictionary with `formattedText`, `extractedValue`, `complete`, `caretPosition` (as integer)

```swift
import ExpoModulesCore
import InputMask

struct CustomNotationRecord: Record {
  @Field var character: String = ""
  @Field var characterSet: String = ""
  @Field var isOptional: Bool = false
}

struct ApplyMaskOptions: Record {
  @Field var primaryFormat: String = ""
  @Field var text: String = ""
  @Field var caretPosition: Int = 0
  @Field var caretGravity: String = "forward"
  @Field var autocomplete: Bool = true
  @Field var autoskip: Bool = false
  @Field var affinityFormats: [String]? = nil
  @Field var affinityStrategy: String? = nil
  @Field var customNotations: [CustomNotationRecord]? = nil
}

public class ExpoInputMaskModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { (options: ApplyMaskOptions) -> [String: Any] in
      let notations = (options.customNotations ?? []).map { record in
        Notation(
          character: Character(record.character),
          characterSet: CharacterSet(charactersIn: record.characterSet),
          isOptional: record.isOptional
        )
      }

      let primaryMask = try Mask.getOrCreate(
        withFormat: options.primaryFormat,
        customNotations: notations
      )

      let gravity: CaretString.CaretGravity = options.caretGravity == "backward"
        ? .backward(autoskip: options.autoskip)
        : .forward(autocomplete: options.autocomplete)

      let clampedPos = min(options.caretPosition, options.text.count)
      let caretIndex = options.text.index(
        options.text.startIndex,
        offsetBy: clampedPos
      )

      let caretString = CaretString(
        string: options.text,
        caretPosition: caretIndex,
        caretGravity: gravity
      )

      if let affinityFormats = options.affinityFormats, !affinityFormats.isEmpty {
        let strategy = self.parseStrategy(options.affinityStrategy)
        let primaryResult = primaryMask.apply(toText: caretString)
        let primaryAffinity = strategy.calculateAffinity(
          ofMask: primaryMask,
          forText: caretString,
          autocomplete: options.autocomplete
        )

        var bestResult = primaryResult
        var bestAffinity = primaryAffinity

        for format in affinityFormats {
          guard let mask = try? Mask.getOrCreate(
            withFormat: format,
            customNotations: notations
          ) else { continue }

          let result = mask.apply(toText: caretString)
          let affinity = strategy.calculateAffinity(
            ofMask: mask,
            forText: caretString,
            autocomplete: options.autocomplete
          )
          if affinity > bestAffinity {
            bestAffinity = affinity
            bestResult = result
          }
        }

        return [
          "formattedText": bestResult.formattedText.string,
          "extractedValue": bestResult.extractedValue,
          "complete": bestResult.complete,
          "caretPosition": bestResult.formattedText.string.distance(
            from: bestResult.formattedText.string.startIndex,
            to: bestResult.formattedText.caretPosition
          ),
          "affinityOfPrimaryFormat": primaryAffinity
        ]
      }

      let result = primaryMask.apply(toText: caretString)

      return [
        "formattedText": result.formattedText.string,
        "extractedValue": result.extractedValue,
        "complete": result.complete,
        "caretPosition": result.formattedText.string.distance(
          from: result.formattedText.string.startIndex,
          to: result.formattedText.caretPosition
        )
      ]
    }
  }

  private func parseStrategy(_ strategy: String?) -> AffinityCalculationStrategy {
    switch strategy {
    case "prefix":
      return .prefix
    case "capacity":
      return .capacity
    case "extracted_value_capacity":
      return .extractedValueCapacity
    default:
      return .wholeString
    }
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add ios/
git commit -m "feat: add iOS native module with InputMask integration"
```

---

### Task 4: Android Native Module

**Files:**
- Create: `android/build.gradle`
- Create: `android/src/main/java/expo/modules/inputmask/ExpoInputMaskModule.kt`

- [ ] **Step 1: Create build.gradle**

```groovy
apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'
apply plugin: 'maven-publish'

group = 'expo.modules.inputmask'
version = '0.1.0'

buildscript {
  def expoModulesCorePlugin = new File(project(":expo-modules-core").projectDir.absolutePath, "ExpoModulesCorePlugin.gradle")
  if (expoModulesCorePlugin.exists()) {
    apply from: expoModulesCorePlugin
    applyKotlinExpoModulesCorePlugin()
  }
}

def expoModulesCorePlugin = new File(project(":expo-modules-core").projectDir.absolutePath, "ExpoModulesCorePlugin.gradle")
if (expoModulesCorePlugin.exists()) {
  apply from: expoModulesCorePlugin
  applyKotlinExpoModulesCorePlugin()
}

android {
  namespace "expo.modules.inputmask"
  compileSdkVersion safeExtGet("compileSdkVersion", 34)

  defaultConfig {
    minSdkVersion safeExtGet("minSdkVersion", 23)
    targetSdkVersion safeExtGet("targetSdkVersion", 34)
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

repositories {
  mavenCentral()
  maven { url 'https://jitpack.io' }
}

dependencies {
  implementation project(':expo-modules-core')
  implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${getKotlinVersion()}"
  implementation 'com.redmadrobot:input-mask-android:7.2.4'
}
```

- [ ] **Step 2: Create the Kotlin module**

This file implements:
1. Record types for structured arguments (`CustomNotationRecord`, `ApplyMaskOptions`)
2. A synchronous `applyMask` Function that:
   - Maps `customNotations` to `Notation` objects (characterSet is already a String on Android)
   - Calls `Mask.getOrCreate(format, customNotations)` (cached internally)
   - Builds `CaretString` with correct `CaretGravity` sealed class and integer caret position
   - Handles affinity via `AffinityCalculationStrategy` enum
   - Returns a map with the result fields

```kotlin
package expo.modules.inputmask

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

class ExpoInputMaskModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoInputMask")

    Function("applyMask") { options: ApplyMaskOptions ->
      val notations = (options.customNotations ?: emptyList()).map { record ->
        Notation(
          character = record.character[0],
          characterSet = record.characterSet,
          isOptional = record.isOptional
        )
      }

      val primaryMask = Mask.getOrCreate(options.primaryFormat, notations)

      val gravity: CaretString.CaretGravity =
        if (options.caretGravity == "backward") {
          CaretString.CaretGravity.BACKWARD(options.autoskip)
        } else {
          CaretString.CaretGravity.FORWARD(options.autocomplete)
        }

      val caretPos = options.caretPosition.coerceIn(0, options.text.length)
      val caretString = CaretString(options.text, caretPos, gravity)

      if (options.affinityFormats != null && options.affinityFormats!!.isNotEmpty()) {
        val strategy = parseStrategy(options.affinityStrategy)
        val primaryResult = primaryMask.apply(caretString)
        val primaryAffinity = strategy.calculateAffinityOfMask(primaryMask, caretString)

        var bestResult = primaryResult
        var bestAffinity = primaryAffinity

        for (format in options.affinityFormats!!) {
          val mask = Mask.getOrCreate(format, notations)
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
```

- [ ] **Step 3: Commit**

```bash
git add android/
git commit -m "feat: add Android native module with input-mask-android integration"
```

---

### Task 5: JS Module Binding and Public API

**Files:**
- Create: `src/ExpoInputMaskModule.ts`
- Create: `src/index.ts`

- [ ] **Step 1: Create the native module binding**

```ts
import { requireNativeModule } from 'expo-modules-core';
import type { ApplyMaskOptions, MaskResult } from './ExpoInputMask.types';

const ExpoInputMaskNative = requireNativeModule('ExpoInputMask');

export function applyMask(options: ApplyMaskOptions): MaskResult {
  return ExpoInputMaskNative.applyMask(options);
}
```

- [ ] **Step 2: Create index.ts with public exports**

```ts
export { applyMask } from './ExpoInputMaskModule';
export { MaskedTextInput } from './MaskedTextInput';
export type {
  ApplyMaskOptions,
  MaskResult,
  MaskedTextInputProps,
  CustomNotation,
} from './ExpoInputMask.types';
```

Note: `MaskedTextInput` doesn't exist yet — this file will have a temporary import error until Task 6. That's fine; we're creating both in sequence.

- [ ] **Step 3: Commit**

```bash
git add src/ExpoInputMaskModule.ts src/index.ts
git commit -m "feat: add JS native module binding and public API exports"
```

---

### Task 6: MaskedTextInput Component

**Files:**
- Create: `src/MaskedTextInput.tsx`

- [ ] **Step 1: Create the component**

Key behaviors:
- Tracks `formattedText` (display value) and `selection` (caret position) in state
- Tracks the last selection from `onSelectionChange` in a ref (no re-renders)
- On `onChangeText`: detects gravity from text length delta, computes caret position, calls `applyMask` synchronously, updates state, fires callbacks
- Uses `isUpdatingRef` guard to prevent `onSelectionChange` from overriding mask-computed selection during an update cycle
- Forwards ref to inner `TextInput` via callback ref
- Handles external `value` prop (treated as extracted value) via useEffect, with `lastExtractedRef` to avoid re-masking when the value came from internal typing

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
import { applyMask } from './ExpoInputMaskModule';
import type { MaskedTextInputProps } from './ExpoInputMask.types';

export const MaskedTextInput = forwardRef<TextInput, MaskedTextInputProps>(
  (
    {
      mask,
      affinityMasks,
      affinityStrategy,
      autocomplete = true,
      autoskip = false,
      customNotations,
      onMaskResult,
      onChangeText,
      onSelectionChange,
      value,
      ...rest
    },
    ref
  ) => {
    const [formattedText, setFormattedText] = useState('');
    const [selection, setSelection] = useState<
      { start: number; end: number } | undefined
    >();
    const selectionRef = useRef({ start: 0, end: 0 });
    const previousTextRef = useRef('');
    const lastExtractedRef = useRef('');
    const isUpdatingRef = useRef(false);
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

    const runMask = useCallback(
      (text: string, caretPos: number, gravity: 'forward' | 'backward') => {
        return applyMask({
          primaryFormat: mask,
          text,
          caretPosition: caretPos,
          caretGravity: gravity,
          autocomplete,
          autoskip,
          affinityFormats: affinityMasks,
          affinityStrategy,
          customNotations,
        });
      },
      [mask, autocomplete, autoskip, affinityMasks, affinityStrategy, customNotations]
    );

    // Handle external value prop changes (value = extracted/unmasked value)
    useEffect(() => {
      if (value !== undefined && value !== lastExtractedRef.current) {
        const result = runMask(value, value.length, 'forward');
        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        lastExtractedRef.current = result.extractedValue;
        setSelection({
          start: result.caretPosition,
          end: result.caretPosition,
        });
      }
    }, [value, runMask]);

    const handleChangeText = useCallback(
      (text: string) => {
        isUpdatingRef.current = true;

        const gravity: 'forward' | 'backward' =
          text.length < previousTextRef.current.length ? 'backward' : 'forward';
        const caretPos = Math.max(
          0,
          selectionRef.current.start +
            (text.length - previousTextRef.current.length)
        );

        const result = runMask(text, caretPos, gravity);

        setFormattedText(result.formattedText);
        previousTextRef.current = result.formattedText;
        lastExtractedRef.current = result.extractedValue;
        setSelection({
          start: result.caretPosition,
          end: result.caretPosition,
        });

        requestAnimationFrame(() => {
          isUpdatingRef.current = false;
        });

        onChangeText?.(result.extractedValue);
        onMaskResult?.({
          formattedText: result.formattedText,
          extractedValue: result.extractedValue,
          complete: result.complete,
        });
      },
      [runMask, onChangeText, onMaskResult]
    );

    const handleSelectionChange = useCallback(
      (event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) => {
        selectionRef.current = event.nativeEvent.selection;
        if (!isUpdatingRef.current) {
          setSelection(event.nativeEvent.selection);
        }
        onSelectionChange?.(event);
      },
      [onSelectionChange]
    );

    return (
      <TextInput
        ref={setRefs}
        {...rest}
        value={formattedText}
        selection={selection}
        onChangeText={handleChangeText}
        onSelectionChange={handleSelectionChange}
      />
    );
  }
);

MaskedTextInput.displayName = 'MaskedTextInput';
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npx tsc --noEmit`

Expected: No errors (may need to `npm install` first).

- [ ] **Step 3: Commit**

```bash
git add src/MaskedTextInput.tsx
git commit -m "feat: add MaskedTextInput React component"
```

---

### Task 7: Example App

**Files:**
- Create: `example/package.json`
- Create: `example/app.json`
- Create: `example/tsconfig.json`
- Create: `example/metro.config.js`
- Create: `example/babel.config.js`
- Create: `example/App.tsx`

- [ ] **Step 1: Create example/package.json**

```json
{
  "name": "expo-input-mask-example",
  "version": "1.0.0",
  "private": true,
  "main": "expo-router/entry",
  "scripts": {
    "start": "expo start",
    "ios": "expo run:ios",
    "android": "expo run:android"
  },
  "dependencies": {
    "expo": "~52.0.0",
    "expo-input-mask": "../",
    "react": "18.3.1",
    "react-native": "0.76.3"
  },
  "devDependencies": {
    "@types/react": "~18.3.0",
    "typescript": "^5.0.0"
  }
}
```

- [ ] **Step 2: Create example/app.json**

```json
{
  "expo": {
    "name": "expo-input-mask-example",
    "slug": "expo-input-mask-example",
    "version": "1.0.0",
    "ios": {
      "bundleIdentifier": "com.example.expoinputmask"
    },
    "android": {
      "package": "com.example.expoinputmask"
    },
    "plugins": ["expo-input-mask"]
  }
}
```

- [ ] **Step 3: Create example/tsconfig.json**

```json
{
  "extends": "expo/tsconfig.base",
  "compilerOptions": {
    "strict": true
  }
}
```

- [ ] **Step 4: Create example/metro.config.js**

```js
const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

config.watchFolders = [monorepoRoot];

config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(monorepoRoot, 'node_modules'),
];

module.exports = config;
```

- [ ] **Step 5: Create example/babel.config.js**

```js
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
  };
};
```

- [ ] **Step 6: Create example/App.tsx**

```tsx
import React, { useState } from 'react';
import { ScrollView, View, Text, StyleSheet, SafeAreaView } from 'react-native';
import { MaskedTextInput } from 'expo-input-mask';

function DemoInput({
  label,
  mask,
  placeholder,
  keyboardType,
  affinityMasks,
  affinityStrategy,
  customNotations,
}: {
  label: string;
  mask: string;
  placeholder: string;
  keyboardType?: 'default' | 'numeric' | 'phone-pad';
  affinityMasks?: string[];
  affinityStrategy?: 'whole_string' | 'prefix' | 'capacity' | 'extracted_value_capacity';
  customNotations?: Array<{ character: string; characterSet: string; isOptional?: boolean }>;
}) {
  const [extracted, setExtracted] = useState('');
  const [complete, setComplete] = useState(false);
  const [formatted, setFormatted] = useState('');

  return (
    <View style={styles.card}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.maskLabel}>Mask: {mask}</Text>
      <MaskedTextInput
        mask={mask}
        placeholder={placeholder}
        keyboardType={keyboardType ?? 'default'}
        affinityMasks={affinityMasks}
        affinityStrategy={affinityStrategy}
        customNotations={customNotations}
        style={styles.input}
        onChangeText={setExtracted}
        onMaskResult={(result) => {
          setComplete(result.complete);
          setFormatted(result.formattedText);
        }}
      />
      <Text style={styles.info}>Formatted: {formatted}</Text>
      <Text style={styles.info}>Extracted: {extracted}</Text>
      <Text style={[styles.info, complete ? styles.complete : styles.incomplete]}>
        {complete ? 'Complete' : 'Incomplete'}
      </Text>
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={styles.title}>expo-input-mask Demo</Text>

        <DemoInput
          label="Phone Number"
          mask="+1 ([000]) [000]-[00][00]"
          placeholder="+1 (___) ___-____"
          keyboardType="phone-pad"
        />

        <DemoInput
          label="Date"
          mask="[00]{/}[00]{/}[0000]"
          placeholder="MM/DD/YYYY"
          keyboardType="numeric"
        />

        <DemoInput
          label="Credit Card (Visa/Amex affinity)"
          mask="[0000] [0000] [0000] [0000]"
          placeholder="Card number"
          keyboardType="numeric"
          affinityMasks={['[0000] [000000] [00000]']}
          affinityStrategy="whole_string"
        />

        <DemoInput
          label="Hex Color (custom notation)"
          mask="[HH]:[HH]:[HH]"
          placeholder="RR:GG:BB"
          customNotations={[
            {
              character: 'H',
              characterSet: '0123456789ABCDEFabcdef',
              isOptional: false,
            },
          ]}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scroll: {
    padding: 20,
    paddingBottom: 40,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 2,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  maskLabel: {
    fontSize: 12,
    color: '#888',
    marginBottom: 8,
    fontFamily: 'monospace',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    marginBottom: 8,
  },
  info: {
    fontSize: 13,
    color: '#555',
    marginTop: 2,
    fontFamily: 'monospace',
  },
  complete: {
    color: '#2e7d32',
    fontWeight: '600',
  },
  incomplete: {
    color: '#c62828',
    fontWeight: '600',
  },
});
```

- [ ] **Step 7: Commit**

```bash
git add example/
git commit -m "feat: add example app with phone, date, credit card, and hex demos"
```

---

### Task 8: Install Dependencies and Build Verification

- [ ] **Step 1: Install root dependencies**

Run: `npm install`

Expected: Successful install. `node_modules/` created.

- [ ] **Step 2: Install example dependencies**

Run: `cd example && npm install`

Expected: Successful install. `example/node_modules/` created with `expo-input-mask` symlinked.

- [ ] **Step 3: Run TypeScript check on library**

Run: `npx tsc --noEmit` (from project root)

Expected: No type errors.

- [ ] **Step 4: Install iOS pods**

Run: `cd example/ios && pod install`

Expected: Pods installed, including `InputMask` and `ExpoInputMask`.

- [ ] **Step 5: Build iOS**

Run: `cd example && npx expo run:ios`

Expected: iOS app builds and launches in simulator.

- [ ] **Step 6: Build Android**

Run: `cd example && npx expo run:android`

Expected: Android app builds and launches in emulator.

- [ ] **Step 7: Manual verification**

Test each demo input in the example app:
- Phone: type `2345678900` → displays `+1 (234) 567-8900`, extracted `2345678900`, complete
- Date: type `12252026` → displays `12/25/2026`, extracted `12252026`, complete
- Credit card: type `378282246310005` → Amex format `3782 822463 10005`, extracted `378282246310005`, complete
- Hex: type `FF00AA` → displays `FF:00:AA`, extracted `FF00AA`, complete

- [ ] **Step 8: Create .gitignore and final commit**

Create `.gitignore`:

```
node_modules/
build/
*.tsbuildinfo
example/node_modules/
example/ios/Pods/
example/ios/build/
example/android/.gradle/
example/android/build/
example/android/app/build/
.expo/
```

```bash
git add .gitignore
git commit -m "chore: add .gitignore"
```
