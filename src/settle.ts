/**
 * Lets an imperative call on the native view (`focus`, `blur`, `clear`) go
 * without an unhandled rejection. Expo resolves the view by its tag, and a
 * call that arrives while the view isn't registered (before it mounts, or
 * after it unmounts) rejects; there is then nothing to focus or clear, so
 * the call is a no-op rather than an error.
 */
export function settle(result: Promise<void> | void): void {
  if (result && typeof result.catch === 'function') {
    result.catch(() => {});
  }
}
