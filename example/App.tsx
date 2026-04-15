import React, { useState } from 'react';
import { ScrollView, View, Text, StyleSheet, SafeAreaView } from 'react-native';
import { MaskedTextInput, NumberInput } from 'expo-input-mask';

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
  currency,
  groupingSeparator,
  decimalSeparator,
  decimalPlaces,
  fixedDecimalPlaces,
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
  fixedDecimalPlaces?: boolean;
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
        fixedDecimalPlaces={fixedDecimalPlaces}
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
          label="USD Currency"
          currency="USD"
          locale="en-US"
          placeholder="$0.00"
        />

        <NumberDemoInput
          label="USD Fixed Decimals"
          currency="USD"
          locale="en-US"
          fixedDecimalPlaces
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
