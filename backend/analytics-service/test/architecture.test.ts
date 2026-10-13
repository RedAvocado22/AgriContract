/**
 * Canonical service layout shared by the Java and Node services (rules §4, §4B; review of #157),
 * and the dependency rule between its layers.
 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';

const src = resolve(dirname(fileURLToPath(import.meta.url)), '../src');

function tsFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? tsFiles(p) : p.endsWith('.ts') ? [p] : [];
  });
}

/** Layer of a file or import target: the first folder under src/, or "main" for src/main.ts. */
function layerOf(file: string): string {
  const first = relative(src, file).split(sep)[0]!;
  return first === 'main.ts' ? 'main' : first;
}

function importSpecifiers(file: string): string[] {
  const code = readFileSync(file, 'utf8');
  return [...code.matchAll(/^\s*(?:import|export)\b[^'"]*?from\s+'([^']+)'/gm)].map((m) => m[1]!);
}

describe('canonical layout', () => {
  it('src/ holds only domain/, application/, infrastructure/ and main.ts', () => {
    assert.deepEqual(readdirSync(src).sort(), ['application', 'domain', 'infrastructure', 'main.ts']);
  });

  it('use case in application/usecase, ports in application/port.ts', () => {
    assert.ok(statSync(join(src, 'application/usecase/ingestEvent.ts')).isFile());
    assert.ok(statSync(join(src, 'application/port.ts')).isFile());
  });

  it('adapters live under infrastructure/{messaging,persistence,http}', () => {
    for (const dir of ['messaging', 'persistence', 'http']) {
      assert.ok(statSync(join(src, 'infrastructure', dir)).isDirectory(), dir);
    }
    assert.ok(statSync(join(src, 'infrastructure/config.ts')).isFile());
  });
});

describe('dependency rule', () => {
  const allowedLayers: Record<string, string[]> = {
    domain: ['domain'],
    application: ['domain', 'application'],
    infrastructure: ['domain', 'application', 'infrastructure'],
    main: ['domain', 'application', 'infrastructure'],
  };
  /** Packages each inner layer may use: the shared eventing library is framework-free (amqplib/mysql2 are only peers). */
  const allowedPackages: Record<string, RegExp> = {
    domain: /^node:/,
    application: /^(node:|@agricontract\/eventing$)/,
  };

  for (const file of tsFiles(src)) {
    const name = relative(src, file).split(sep).join('/');
    const layer = layerOf(file);
    it(`${name} only imports allowed layers and packages`, () => {
      for (const spec of importSpecifiers(file)) {
        if (spec.startsWith('.')) {
          const target = layerOf(resolve(dirname(file), spec));
          assert.ok(allowedLayers[layer]!.includes(target), `${name} (${layer}) must not import ${spec} (${target})`);
        } else if (allowedPackages[layer]) {
          assert.match(spec, allowedPackages[layer]!, `${name} (${layer}) must not depend on ${spec}`);
        }
      }
    });
  }
});
