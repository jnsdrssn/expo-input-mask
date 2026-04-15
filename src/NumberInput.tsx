import React from 'react';
import { View, Text } from 'react-native';

// Placeholder — will be replaced with native view in subsequent tasks
export const NumberInput = React.forwardRef<View, any>((props, ref) => {
  return (
    <View ref={ref}>
      <Text>NumberInput: native view not yet implemented</Text>
    </View>
  );
});

NumberInput.displayName = 'NumberInput';
