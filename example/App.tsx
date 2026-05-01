import React, { useRef, useState } from 'react';
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { CurrencyInput, MaskedTextInput, NumberInput } from 'expo-input-mask';
import type {
  CurrencyValueResult,
  NumberInputRef,
  NumberValueResult,
} from 'expo-input-mask';

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

function NumberDemoInput({
  label,
  locale,
  groupingSeparator,
  decimalSeparator,
  decimalPlaces,
  min,
  max,
  placeholder,
}: {
  label: string;
  locale?: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  min?: number;
  max?: number;
  placeholder: string;
}) {
  const [result, setResult] = useState<NumberValueResult>({
    formattedText: '',
    rawValue: '',
    value: null,
    complete: false,
  });

  return (
    <View style={styles.card}>
      <Text style={styles.label}>{label}</Text>
      {locale && <Text style={styles.maskLabel}>Locale: {locale}</Text>}
      <View style={styles.numberInputBox}>
        <NumberInput
          locale={locale}
          groupingSeparator={groupingSeparator}
          decimalSeparator={decimalSeparator}
          decimalPlaces={decimalPlaces}
          min={min}
          max={max}
          placeholder={placeholder}
          style={styles.numberInputField}
          onValueChange={setResult}
        />
      </View>
      <Text style={styles.info}>Formatted: {result.formattedText}</Text>
      <Text style={styles.info}>Raw: {result.rawValue}</Text>
      <Text style={styles.info}>Value: {result.value !== null ? result.value : 'null'}</Text>
      <Text style={[styles.info, result.complete ? styles.complete : styles.incomplete]}>
        {result.complete ? 'Complete' : 'Incomplete'}
      </Text>
    </View>
  );
}

function CurrencyDemoInput({
  label,
  locale,
  currency,
  groupingSeparator,
  decimalSeparator,
  decimalPlaces,
  mode,
  min,
  max,
  placeholder,
}: {
  label: string;
  locale?: string;
  currency: string;
  groupingSeparator?: string;
  decimalSeparator?: string;
  decimalPlaces?: number;
  mode?: 'decimal' | 'cents';
  min?: number;
  max?: number;
  placeholder: string;
}) {
  const [result, setResult] = useState<CurrencyValueResult>({
    formattedText: '',
    rawValue: '',
    value: null,
    minorUnits: null,
    complete: false,
  });

  return (
    <View style={styles.card}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.maskLabel}>Currency: {currency}</Text>
      {locale && <Text style={styles.maskLabel}>Locale: {locale}</Text>}
      <View style={styles.numberInputBox}>
        <CurrencyInput
          locale={locale}
          currency={currency}
          groupingSeparator={groupingSeparator}
          decimalSeparator={decimalSeparator}
          decimalPlaces={decimalPlaces}
          mode={mode}
          min={min}
          max={max}
          placeholder={placeholder}
          style={styles.numberInputField}
          onValueChange={setResult}
        />
      </View>
      <Text style={styles.info}>Formatted: {result.formattedText}</Text>
      <Text style={styles.info}>Raw: {result.rawValue}</Text>
      <Text style={styles.info}>Value: {result.value !== null ? result.value : 'null'}</Text>
      <Text style={styles.info}>
        minorUnits: {result.minorUnits !== null ? result.minorUnits : 'null'}
      </Text>
      <Text style={[styles.info, result.complete ? styles.complete : styles.incomplete]}>
        {result.complete ? 'Complete' : 'Incomplete'}
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

        <Text style={[styles.title, { marginTop: 20 }]}>NumberInput Demos</Text>

        <NumberDemoInput
          label="Plain Number"
          placeholder="1,234,567"
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

        <Text style={[styles.title, { marginTop: 20 }]}>CurrencyInput Demos</Text>

        <CurrencyDemoInput
          label="USD Currency"
          currency="USD"
          locale="en-US"
          placeholder="$0.00"
        />

        <CurrencyDemoInput
          label="USD Cents Mode"
          currency="USD"
          locale="en-US"
          mode="cents"
          placeholder="$0.00"
        />

        <CurrencyDemoInput
          label="EUR Currency (German)"
          currency="EUR"
          locale="de-DE"
          placeholder="0,00 €"
        />

        <ControlledCurrencyDemo />

        <ImperativeRefDemo />
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * Controlled-mode demo: parent owns the value and echoes `onValueChange` back
 * via `value`. Exercises the first-mount-with-initial-value path and the
 * focused-typing no-op behavior of `setExternalValue`.
 */
function ControlledCurrencyDemo() {
  const [value, setValue] = useState<number | null>(1234.56);
  const [minorUnits, setMinorUnits] = useState<number | null>(123456);

  return (
    <View style={styles.card}>
      <Text style={styles.label}>Controlled (EUR / de-DE, initial 1234.56)</Text>
      <Text style={styles.maskLabel}>value = {value === null ? 'null' : value}</Text>
      <Text style={styles.maskLabel}>
        minorUnits = {minorUnits === null ? 'null' : minorUnits}
      </Text>
      <View style={styles.numberInputBox}>
        <CurrencyInput
          currency="EUR"
          locale="de-DE"
          value={value}
          placeholder="0,00 €"
          style={styles.numberInputField}
          onValueChange={(r) => {
            setValue(r.value);
            setMinorUnits(r.minorUnits);
          }}
        />
      </View>
      <View style={styles.buttonRow}>
        <Pressable style={styles.button} onPress={() => setValue(0)}>
          <Text style={styles.buttonText}>Reset to 0</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => setValue(null)}>
          <Text style={styles.buttonText}>Set null</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => setValue(9999.99)}>
          <Text style={styles.buttonText}>Set 9999.99</Text>
        </Pressable>
      </View>
    </View>
  );
}

/**
 * Imperative-ref demo: focus / blur / clear via `NumberInputRef`. Both
 * `<NumberInput />` and `<CurrencyInput />` share the ref shape.
 */
function ImperativeRefDemo() {
  const ref = useRef<NumberInputRef>(null);

  return (
    <View style={styles.card}>
      <Text style={styles.label}>Imperative ref (focus / blur / clear)</Text>
      <View style={styles.numberInputBox}>
        <CurrencyInput
          ref={ref}
          currency="USD"
          locale="en-US"
          placeholder="$0.00"
          style={styles.numberInputField}
        />
      </View>
      <View style={styles.buttonRow}>
        <Pressable style={styles.button} onPress={() => ref.current?.focus()}>
          <Text style={styles.buttonText}>Focus</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => ref.current?.blur()}>
          <Text style={styles.buttonText}>Blur</Text>
        </Pressable>
        <Pressable style={styles.button} onPress={() => ref.current?.clear()}>
          <Text style={styles.buttonText}>Clear</Text>
        </Pressable>
      </View>
    </View>
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
  // NumberInput / CurrencyInput are custom native views, so RN's `padding`
  // style doesn't propagate to their inner text rendering the way it does for
  // `<TextInput />`. Wrap them in this View to get the same bordered-box look —
  // the wrapper provides the horizontal padding via Yoga layout, and the inner
  // text field fills the resulting smaller content area.
  numberInputBox: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 12,
    marginBottom: 8,
    height: 44,
    justifyContent: 'center',
  },
  numberInputField: {
    fontSize: 16,
    height: 22,
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
  buttonRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 8,
  },
  button: {
    flex: 1,
    backgroundColor: '#1976d2',
    paddingVertical: 8,
    borderRadius: 6,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontSize: 13,
    fontWeight: '600',
  },
});
