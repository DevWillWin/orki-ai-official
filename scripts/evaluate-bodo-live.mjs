#!/usr/bin/env node
// Explicit opt-in: this calls the configured live model and can consume quota.
// Never prints authorization headers or saves secrets. Results are ignored build outputs.
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

if (!process.argv.includes('--run') || !process.env.ORKI_EDGE_URL) {
  console.error('Usage: ORKI_EDGE_URL=<deployed quick-processor URL> node scripts/evaluate-bodo-live.mjs --run [--case=id]');
  console.error('Optional ORKI_EDGE_AUTH_TOKEN / ORKI_EDGE_APIKEY must be supplied via the environment, never chat or source.');
  process.exit(2);
}
const endpoint = new URL(process.env.ORKI_EDGE_URL);
if (endpoint.protocol !== 'https:') throw new Error('Live evaluation requires HTTPS.');
const corpus = JSON.parse(readFileSync(new URL('../data/bodo/evaluation-cases.json', import.meta.url), 'utf8'));
const chosen = process.argv.find(value => value.startsWith('--case='))?.slice(7);
const cases = corpus.cases.filter(item => !chosen || item.id === chosen);
if (!cases.length) throw new Error('Unknown evaluation case.');
const headers = { 'Content-Type': 'application/json' };
if (process.env.ORKI_EDGE_AUTH_TOKEN) headers.Authorization = `Bearer ${process.env.ORKI_EDGE_AUTH_TOKEN}`;
if (process.env.ORKI_EDGE_APIKEY) headers.apikey = process.env.ORKI_EDGE_APIKEY;
const outputDirectory = resolve('.build-outputs/bodo-eval');
mkdirSync(outputDirectory, { recursive: true });
const results = [];
for (const fixture of cases) {
  const payload = fixture.task === 'chat' ? {
    plan: 'Guest', model: 'orki', script: fixture.script,
    contents: [...(fixture.history || []), { role: 'user', parts: [{ text: fixture.prompt }] }],
    generationConfig: { temperature: 0.25, maxOutputTokens: 500 },
  } : { task: fixture.task, prompt: fixture.prompt, mediaType: 'image', plan: 'Guest', model: 'orki' };
  const result = { caseId: fixture.id, originalPrompt: fixture.prompt, expectedIntent: fixture.intent,
    verificationStatus: fixture.status, review: 'pending_human_review' };
  try {
    const response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(payload), signal: AbortSignal.timeout(90000) });
    result.httpStatus = response.status;
    result.model = response.headers.get('X-Orki-Model');
    result.lexiconVersion = response.headers.get('X-Orki-Lexicon-Version');
    const text = await response.text();
    if (fixture.task === 'chat' && response.ok) {
      result.output = text.split('\n').filter(line => line.startsWith('data:')).map(line => {
        try {
          const parsed = JSON.parse(line.slice(5).trim());
          return (parsed.candidates?.[0]?.content?.parts || []).filter(part => !part.thought).map(part => part.text || '').join('');
        } catch { return ''; }
      }).join('');
    } else {
      try { result.output = JSON.parse(text); } catch { result.output = text; }
    }
    // Deliberately do not score correctness based on HTTP 200, compilation or our own glosses.
  } catch (error) {
    result.error = error.name; // avoid URL/token-bearing request error dumps
  }
  results.push(result);
  console.log(`${fixture.id}: HTTP ${result.httpStatus ?? 'unavailable'}; human review pending`);
}
const outputFile = resolve(outputDirectory, `results-${new Date().toISOString().replace(/[:.]/g, '-')}.json`);
writeFileSync(outputFile, JSON.stringify(results, null, 2) + '\n');
console.log(`Saved ${results.length} cases to ${outputFile}. No correctness claim is made.`);
