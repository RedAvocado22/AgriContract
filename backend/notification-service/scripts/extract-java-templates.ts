/**
 * Regenerates test/fixtures/java-templates.json from the Java
 * ProcessNotificationUseCaseImpl (golden fixtures, plan §8).
 *   npm run fixtures:java
 * Only meaningful while the Java service is still in the repo (until M5).
 */
import { readFileSync, writeFileSync } from 'node:fs';
import {
  FIXTURE,
  JAVA_SOURCE,
  extractJavaTemplates,
} from '../test/support/java-templates';

const templates = extractJavaTemplates(readFileSync(JAVA_SOURCE, 'utf8'));
writeFileSync(FIXTURE, `${JSON.stringify(templates, null, 2)}\n`, 'utf8');
console.log(`Wrote ${Object.keys(templates).length} templates to ${FIXTURE}`);
