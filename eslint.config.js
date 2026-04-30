// Flat-config wrapper around the Expo module preset, which extends
// `eslint-config-universe/flat/native` (React Native globals, React +
// react-hooks, TypeScript, and Prettier integration). `expo-module lint`
// runs `eslint src` and looks for this file at the repo root.
const baseConfig = require('expo-module-scripts/eslint.config.base');

module.exports = [
  ...baseConfig,
  {
    ignores: ['build/', 'node_modules/', 'example/', '.build/'],
  },
];
