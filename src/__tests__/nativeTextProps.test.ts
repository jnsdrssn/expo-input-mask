/**
 * `nativeTextProps()` splits `<TextInput />`-style text props into the native
 * view's own props: the text keys leave `style` and travel as props, colors
 * are processed, and the accessibility label goes to the text field alone.
 *
 * `react-native` is mocked: this suite runs under a node test environment.
 * `processColor` tags its input so the tests can see a color was processed;
 * `StyleSheet.flatten` merges arrays as the real one does.
 */
import { nativeTextProps } from '../nativeTextProps';

jest.mock('react-native', () => {
  const flatten = (style: unknown): Record<string, unknown> | undefined => {
    if (style == null || style === false) return undefined;
    if (Array.isArray(style)) {
      return style.reduce<Record<string, unknown>>(
        (acc, item) => ({ ...acc, ...(flatten(item) ?? {}) }),
        {}
      );
    }
    return style as Record<string, unknown>;
  };
  return {
    processColor: (value: unknown) => `processed(${String(value)})`,
    StyleSheet: { flatten },
  };
});

describe('nativeTextProps', () => {
  it('lifts the text keys out of style and leaves the box in it', () => {
    const props = nativeTextProps({
      style: {
        padding: 8,
        borderRadius: 4,
        fontFamily: 'Inter',
        fontSize: 17,
        fontWeight: '600',
        fontVariant: ['tabular-nums'],
        letterSpacing: 0.5,
        textAlign: 'right',
        color: 'red',
      },
    });

    expect(props.style).toEqual({ padding: 8, borderRadius: 4 });
    expect(props).toEqual(
      expect.objectContaining({
        fontFamily: 'Inter',
        fontSize: 17,
        fontWeight: '600',
        fontVariant: ['tabular-nums'],
        letterSpacing: 0.5,
        textAlign: 'right',
        color: 'processed(red)',
      })
    );
  });

  it('flattens style arrays', () => {
    const props = nativeTextProps({
      style: [{ fontSize: 12, margin: 2 }, { fontSize: 20 }],
    });

    expect(props.style).toEqual({ margin: 2 });
    expect(props.fontSize).toBe(20);
  });

  it('turns a numeric fontWeight into a string', () => {
    const props = nativeTextProps({
      style: { fontWeight: 700 as unknown as '700' },
    });

    expect(props.fontWeight).toBe('700');
  });

  it('prefers the textAlign prop over style', () => {
    const props = nativeTextProps({
      textAlign: 'center',
      style: { textAlign: 'right' },
    });

    expect(props.textAlign).toBe('center');
  });

  it('processes every color prop', () => {
    const props = nativeTextProps({
      placeholderTextColor: 'gray',
      selectionColor: 'blue',
      cursorColor: 'green',
    });

    expect(props.placeholderTextColor).toBe('processed(gray)');
    expect(props.selectionColor).toBe('processed(blue)');
    expect(props.cursorColor).toBe('processed(green)');
  });

  it('passes allowFontScaling through', () => {
    expect(nativeTextProps({ allowFontScaling: false }).allowFontScaling).toBe(
      false
    );
  });

  it('sends accessibilityLabel as fieldAccessibilityLabel', () => {
    const props = nativeTextProps({ accessibilityLabel: 'Amount' });

    expect(props.fieldAccessibilityLabel).toBe('Amount');
    expect(props).not.toHaveProperty('accessibilityLabel');
  });

  it('prefers aria-label over accessibilityLabel', () => {
    expect(
      nativeTextProps({ accessibilityLabel: 'Amount', 'aria-label': 'Total' })
        .fieldAccessibilityLabel
    ).toBe('Total');
    expect(
      nativeTextProps({ 'aria-label': 'Total' }).fieldAccessibilityLabel
    ).toBe('Total');
  });

  it('leaves everything unset when nothing is given', () => {
    const props = nativeTextProps({});

    expect(props.style).toEqual({});
    for (const key of [
      'textAlign',
      'fontFamily',
      'fontSize',
      'fontWeight',
      'fontVariant',
      'letterSpacing',
      'allowFontScaling',
      'color',
      'placeholderTextColor',
      'selectionColor',
      'cursorColor',
      'fieldAccessibilityLabel',
    ] as const) {
      expect(props[key]).toBeUndefined();
    }
  });

  it('leaves null colors unset rather than processing them', () => {
    const props = nativeTextProps({ cursorColor: null });

    expect(props.cursorColor).toBeUndefined();
  });
});
