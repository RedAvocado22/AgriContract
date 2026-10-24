/**
 * Review #157 (rules §4, §4B): canonical service layout shared by the Java and Node services,
 * and the dependency rule between its layers.
 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { deepEqual } from './deepEqual';

const src = resolve(__dirname, '../src');

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

/** Relative imports of a source file, resolved to absolute paths. */
function relativeImports(file: string): string[] {
  const code = readFileSync(file, 'utf8');
  const specs = [...code.matchAll(/^\s*(?:import|export)\b[^'"]*?from\s+'([^']+)'/gm)].map((m) => m[1]!);
  return specs.filter((s) => s.startsWith('.')).map((s) => resolve(dirname(file), s));
}

describe('canonical layout', () => {
  it('src/ holds only domain/, application/, infrastructure/ and main.ts', () => {
    deepEqual(readdirSync(src).sort(), ['application', 'domain', 'infrastructure', 'main.ts']);
  });

  it('use cases live in application/usecase, ports in application/port.ts', () => {
    assert.ok(statSync(join(src, 'application/usecase/recordManualPrice.ts')).isFile());
    assert.ok(statSync(join(src, 'application/port.ts')).isFile());
  });

  it('adapters live under infrastructure/{persistence,cache,http}', () => {
    for (const dir of ['persistence', 'cache', 'http']) {
      assert.ok(statSync(join(src, 'infrastructure', dir)).isDirectory(), dir);
    }
    assert.ok(statSync(join(src, 'infrastructure/config.ts')).isFile());
  });
});

describe('dependency rule', () => {
  const allowed: Record<string, string[]> = {
    domain: ['domain'],
    application: ['domain', 'application'],
    infrastructure: ['domain', 'application', 'infrastructure'],
    main: ['domain', 'application', 'infrastructure'],
  };

  for (const file of tsFiles(src)) {
    const name = relative(src, file).split(sep).join('/');
    it(`${name} only imports allowed layers`, () => {
      const layer = layerOf(file);
      for (const target of relativeImports(file)) {
        assert.ok(
          allowed[layer]!.includes(layerOf(target)),
          `${name} (${layer}) must not import ${relative(src, target)} (${layerOf(target)})`,
        );
      }
    });
  }

  it('domain and application use no framework or driver packages', () => {
    for (const file of tsFiles(src).filter((f) => ['domain', 'application'].includes(layerOf(f)))) {
      const code = readFileSync(file, 'utf8');
      const packages = [...code.matchAll(/from\s+'([^'.][^']*)'/g)].map((m) => m[1]!);
      deepEqual(packages.filter((p) => !p.startsWith('node:')), [], relative(src, file));
    }
  });

  it('HTTP statuses appear only in the web layer', () => {
    for (const file of tsFiles(src).filter((f) => ['domain', 'application'].includes(layerOf(f)))) {
      assert.doesNotMatch(readFileSync(file, 'utf8'), /HttpError|statusCode|\bstatus\s*[:=]/, relative(src, file));
    }
  });
});

describe('NestJS (decision 19/10/2026)', () => {
  const read = (f: string) => readFileSync(join(src, f), 'utf8');

  it('Nest lives only in infrastructure/ and main.ts; domain and application stay framework-free', () => {
    for (const file of tsFiles(src)) {
      const uses = /from\s+'@nestjs\//.test(readFileSync(file, 'utf8'));
      const layer = layerOf(file);
      if (uses) assert.ok(['infrastructure', 'main'].includes(layer), relative(src, file));
    }
  });

  it('controllers, guard, error filter and module exist where the canonical layout puts them', () => {
    assert.match(read('infrastructure/http/prices.controller.ts'), /@Controller\(\)[\s\S]*@Post\(MANUAL_PRICE_PATH\)/);
    assert.match(read('infrastructure/http/health.controller.ts'), /@Get\(HEALTH_PATH\)/);
    assert.match(read('infrastructure/http/manualPrice.guard.ts'), /implements CanActivate/);
    assert.match(read('infrastructure/http/problem.filter.ts'), /@Catch\(\)/);
    assert.match(read('infrastructure/nest/pricing.module.ts'), /provide: APP_FILTER, useClass: ProblemFilter/);
    assert.match(read('infrastructure/nest/app.module.ts'), /@Module\(/);
  });

  it('authorization is a guard, so it runs before the body is read', () => {
    const controller = read('infrastructure/http/prices.controller.ts');
    assert.match(controller, /@UseGuards\(ManualPriceGuard\)/);
    assert.ok(controller.indexOf('readJsonBody(') > controller.indexOf('@UseGuards('));
  });

  it('main.ts only bootstraps: body parser off, shutdown hooks on, no MySQL or Redis client', () => {
    const main = read('main.ts');
    assert.match(main, /bodyParser: false/);
    assert.match(main, /enableShutdownHooks\(\['SIGTERM', 'SIGINT'\]\)/);
    assert.doesNotMatch(main, /from '(mysql2|redis)/);
  });

  it('the MySQL pool and the Redis connection are closed on shutdown', () => {
    const module = read('infrastructure/nest/pricing.module.ts');
    assert.match(module, /implements OnApplicationShutdown/);
    assert.match(module, /this\.pool\.end\(\), this\.cache\.close\(\)/);
  });
});
