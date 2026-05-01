// Used by Jest (via babel-jest) to transform test files. The library itself is
// built with `tsc` via `expo-module build`, which does not consult this file.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      ['@babel/preset-env', { targets: { node: 'current' } }],
      '@babel/preset-typescript',
      ['@babel/preset-react', { runtime: 'automatic' }],
    ],
  };
};
