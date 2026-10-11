import { processColor, StyleSheet } from 'react-native';
import type {
  ColorValue,
  ProcessedColorValue,
  StyleProp,
  TextStyle,
  ViewStyle,
} from 'react-native';

/** The text styling the native field draws itself, as the native view takes it. */
export interface NativeTextProps {
  style?: StyleProp<ViewStyle>;
  textAlign?: string;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: string;
  fontVariant?: string[];
  letterSpacing?: number;
  allowFontScaling?: boolean;
  color?: ProcessedColorValue | null;
  placeholderTextColor?: ProcessedColorValue | null;
  selectionColor?: ProcessedColorValue | null;
  cursorColor?: ProcessedColorValue | null;
  fieldAccessibilityLabel?: string;
}

/** The `<TextInput />` props `nativeTextProps` reads. */
export interface TextStylingProps {
  style?: StyleProp<TextStyle>;
  textAlign?: string;
  allowFontScaling?: boolean;
  placeholderTextColor?: ColorValue;
  selectionColor?: ColorValue;
  cursorColor?: ColorValue | null;
  accessibilityLabel?: string;
  'aria-label'?: string;
}

const processed = (
  value: ColorValue | null | undefined
): ProcessedColorValue | null | undefined =>
  value == null ? undefined : processColor(value);

/**
 * Splits `<TextInput />`-style text props into the native view's own props.
 * The native field draws its text itself, so the text keys leave `style`
 * (which keeps the view's layout and box) and travel as props; colors are
 * processed to the platform's form. `accessibilityLabel` goes to the text
 * field alone, so the container never reads as a second element. A prop that
 * isn't given stays unset, and the field keeps its platform default.
 */
export function nativeTextProps({
  style,
  textAlign,
  allowFontScaling,
  placeholderTextColor,
  selectionColor,
  cursorColor,
  accessibilityLabel,
  'aria-label': ariaLabel,
}: TextStylingProps): NativeTextProps {
  const {
    color,
    fontFamily,
    fontSize,
    fontWeight,
    fontVariant,
    letterSpacing,
    textAlign: styleTextAlign,
    ...viewStyle
  } = (StyleSheet.flatten(style) ?? {}) as TextStyle;

  return {
    style: viewStyle as ViewStyle,
    textAlign: textAlign ?? styleTextAlign,
    fontFamily,
    fontSize,
    fontWeight: fontWeight == null ? undefined : String(fontWeight),
    fontVariant: fontVariant ? [...fontVariant] : undefined,
    letterSpacing,
    allowFontScaling,
    color: processed(color),
    placeholderTextColor: processed(placeholderTextColor),
    selectionColor: processed(selectionColor),
    cursorColor: processed(cursorColor),
    fieldAccessibilityLabel: ariaLabel ?? accessibilityLabel,
  };
}
