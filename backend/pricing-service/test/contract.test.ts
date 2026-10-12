/**
 * Boundary/contract tests against the frozen repo contracts:
 *  - contracts/events/supplementary-services-api.yaml (recordManualPrice)
 *  - contracts/openapi/golden-flow-api.yaml (shared ErrorEnvelope)
 *  - "contract flow does not call pricing synchronously" (pricing-service design §4.1)
 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { after, before, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import Ajv2020Module from 'ajv/dist/2020.js';
import addFormatsModule from 'ajv-formats';
import yaml from 'js-yaml';
import { MANUAL_PRICE_ROLES } from '../src/auth.js';
import { MANUAL_PRICE_PATH } from '../src/http.js';
import { adminHeaders, postManual, startApp, validBody, type TestApp } from './fakes.js';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '../../..');
const load = (p: string) => yaml.load(readFileSync(join(repoRoot, p), 'utf8')) as any;

const supplementary = load('contracts/events/supplementary-services-api.yaml');
const goldenFlow = load('contracts/openapi/golden-flow-api.yaml');

const Ajv2020 = ((Ajv2020Module as unknown as { default?: unknown }).default ?? Ajv2020Module) as new (
  opts: object,
) => { compile(s: object): ((d: unknown) => boolean) & { errors?: unknown } };
const addFormats = ((addFormatsModule as unknown as { default?: unknown }).default ?? addFormatsModule) as (
  ajv: unknown,
) => void;
const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const validateErrorEnvelope = ajv.compile(goldenFlow.components.schemas.ErrorEnvelope);

describe('supplementary-services-api.yaml: recordManualPrice', () => {
  const op = supplementary.paths[MANUAL_PRICE_PATH]?.post;

  it('the implemented path/method exists in the frozen contract and is owned by pricing-service', () => {
    assert.ok(op, `POST ${MANUAL_PRICE_PATH} missing from contract`);
    assert.equal(op.operationId, 'recordManualPrice');
    assert.equal(op['x-owner'], 'pricing-service');
  });

  it('contract requires the Idempotency-Key header', () => {
    const header = op.parameters.find((p: any) => p.name === 'Idempotency-Key');
    assert.equal(header?.in, 'header');
    assert.equal(header?.required, true);
  });

  it('contract auth is ADMIN; OPERATOR is the governance-approved delegate', () => {
    // x-auth: ADMIN in the catalog; data-governance §5.4 and api-gateway design (18/07) add OPERATOR for "giá manual".
    assert.equal(op['x-auth'], 'ADMIN');
    assert.deepEqual([...MANUAL_PRICE_ROLES], ['ADMIN', 'OPERATOR']);
  });

  it('contract responses 201 and 403 are the ones the service returns', () => {
    assert.ok(op.responses['201']);
    assert.ok(op.responses['403']);
  });

  it('contract keeps Phase 2 to domestic COFFEE (read endpoints use const COFFEE)', () => {
    const read = supplementary.paths['/api/v1/prices/{commodity}/{province}'].get;
    const commodity = read.parameters.find((p: any) => p.name === 'commodity');
    assert.equal(commodity.schema.const, 'COFFEE');
  });
});

describe('golden-flow ErrorEnvelope', () => {
  let app: TestApp;
  before(async () => {
    app = await startApp();
  });
  after(async () => {
    await app.close();
  });

  const cases: Array<[string, () => Promise<{ status: number; json: unknown }>]> = [
    ['400 validation', () => postManual(app, validBody({ commodity: 'RICE' }))],
    ['403 role', () => postManual(app, validBody(), adminHeaders({ 'X-User-Role': 'SELLER' }))],
    ['403 gateway', () => postManual(app, validBody(), adminHeaders({ 'X-Gateway-Secret': 'x' }))],
    [
      '409 key reuse',
      async () => {
        await postManual(app, validBody(), adminHeaders({ 'Idempotency-Key': 'env-409' }));
        return postManual(app, validBody({ price: '1.00' }), adminHeaders({ 'Idempotency-Key': 'env-409' }));
      },
    ],
  ];

  for (const [name, call] of cases) {
    it(`${name} response matches the shared ErrorEnvelope schema`, async () => {
      const res = await call();
      assert.ok(res.status >= 400);
      assert.equal(validateErrorEnvelope(res.json), true, JSON.stringify(validateErrorEnvelope.errors));
    });
  }
});

describe('pricing is not a synchronous dependency of the contract flow (design §4.1)', () => {
  const PRICING_REFERENCE = /pricing-service|PRICING_SERVICE_URL|\/api\/v1\/prices|:8091\b/i;

  function sourceFiles(dir: string): string[] {
    const out: string[] = [];
    for (const name of readdirSync(dir)) {
      if (name === 'node_modules' || name === 'target' || name === 'dist') continue;
      const p = join(dir, name);
      if (statSync(p).isDirectory()) out.push(...sourceFiles(p));
      else if (/\.(java|kt|ts|js|yml|yaml|properties)$/.test(name)) out.push(p);
    }
    return out;
  }

  for (const service of ['contract-service', 'escrow-service']) {
    it(`${service} has no client, URL or route to pricing-service`, () => {
      const dir = join(repoRoot, 'backend', service);
      const offenders = sourceFiles(dir).filter((f) => PRICING_REFERENCE.test(readFileSync(f, 'utf8')));
      assert.deepEqual(offenders.map((f) => f.slice(repoRoot.length + 1)), []);
    });
  }

  it('pricing-service makes no outbound HTTP calls and exposes no internal endpoint for contract/escrow', () => {
    const dir = resolve(here, '../src');
    for (const f of sourceFiles(dir)) {
      const src = readFileSync(f, 'utf8');
      assert.doesNotMatch(src, /\bfetch\(|http\.request|https\.request|\/internal\//, f);
    }
  });
});
