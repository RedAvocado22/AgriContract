import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/integration/**/*.test.ts'],
    // Pulling and starting containers is slow on a cold runner.
    hookTimeout: 180_000,
    testTimeout: 180_000,
  },
});
