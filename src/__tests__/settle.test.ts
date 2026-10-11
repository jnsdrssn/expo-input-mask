import { settle } from '../settle';

describe('settle', () => {
  it('swallows a rejected native call', async () => {
    const rejected = Promise.reject(
      new Error('Unable to find the view with tag 6520')
    );
    settle(rejected);
    // An unhandled rejection would fail the test run; give it a tick.
    await new Promise((resolve) => setTimeout(resolve, 0));
  });

  it('leaves a resolved call and a plain return alone', async () => {
    expect(() => settle(Promise.resolve())).not.toThrow();
    expect(() => settle(undefined)).not.toThrow();
  });
});
