import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { lookupBodo, matchesBodo, BODO_REFERENCE, VISUAL_POLICY, CONVERSATION_POLICY, visualInput, parseVisualResult } from '../supabase/functions/_shared/bodo-policy.ts';
import { lexicon } from '../supabase/functions/_shared/bodo-lexicon.generated.ts';
import { readFileSync } from 'node:fs';

// Execute the actual edge handler locally; only the Gemini network boundary is mocked.
let handler;
const env = new Map([['GEMINI_API_KEY', 'test-only-not-a-real-key']]);
globalThis.Deno = { serve(fn) { handler = fn; }, env: { get: key => env.get(key) } };
await import('../supabase/functions/quick-processor/index.ts');
let requests;
let upstream;
beforeEach(() => {
  requests = [];
  upstream = [];
  for (const key of ['ORKI_STANDARD_MODEL', 'ORKI_PRO_MODEL', 'ORKI_FALLBACK_MODEL']) env.delete(key);
  globalThis.fetch = async (url, options) => {
    requests.push({ url, body: JSON.parse(options.body) });
    assert.ok(upstream.length, 'Unexpected upstream request');
    return upstream.shift();
  };
});
const call = body => handler(new Request('http://edge.test', { method: 'POST', body: JSON.stringify(body) }));
const gemini = result => Response.json({ candidates: [{ content: { parts: [{ text: JSON.stringify(result) }] } }] });
const success = enhancedPrompt => ({ enhancedPrompt, needsClarification: false, clarificationQuestion: '' });

test('all indexed forms retain identity and verification status', () => {
  for (const entry of lexicon.entries) {
    for (const form of [...entry.roman, ...entry.devanagari]) {
      assert.equal(lookupBodo(form)?.id, entry.id);
      assert.equal(lookupBodo(form.toUpperCase())?.id, entry.id);
    }
    assert.ok(entry.sources.length);
    assert.ok(['user_confirmed', 'provisional', 'verified'].includes(entry.meaningStatus));
  }
  assert.equal(lookupBodo('swima').meaningStatus, 'user_confirmed');
  assert.equal(lookupBodo('mousi').meaningStatus, 'provisional');
});
for (const form of ['swima', 'suima', 'saima', 'सैमा']) {
  test(`dog regression: exact lookup for ${form}`, () => assert.equal(lookupBodo(form)?.meanings[0], 'dog'));
}
test('no fuzzy/global replacements; short ambiguous tokens keep notes', () => {
  assert.equal(lookupBodo('swimming'), undefined);
  assert.equal(lookupBodo('swim'), undefined);
  assert.equal(lookupBodo('swimathon'), undefined);
  assert.equal(lookupBodo('dog'), undefined);
  assert.equal(lookupBodo(' na '), undefined);
  assert.match(lookupBodo('na').notes, /ambiguous/);
  assert.equal(lookupBodo('dwi').meanings[0], 'water');
});
test('policy preserves requested language and flags provisional knowledge', () => {
  assert.match(CONVERSATION_POLICY, /Explicit language\/script requests override/);
  assert.match(CONVERSATION_POLICY, /not verified translations/);
  assert.match(BODO_REFERENCE, /not grammar rules/);
  assert.match(VISUAL_POLICY, /exact counts/);
  assert.match(VISUAL_POLICY, /ALL exclusions/);
  assert.match(VISUAL_POLICY, /keep it unspecified/);
});
test('input extraction strips command only; does not transliterate meaning', () => {
  assert.equal(visualInput({ prompt: '/IMAGE suima' }), 'suima');
  assert.equal(visualInput({ prompt: 'swimming dog, no robots' }), 'swimming dog, no robots');
  assert.equal(visualInput({ contents: [{ parts: [{ text: 'सैमा' }] }] }), 'सैमा');
  assert.equal(visualInput({ messages: [{ content: 'hagra' }, { content: 'swima' }] }), 'swima');
});

// Contract fixtures. These validate transport and prompt preservation instructions,
// NOT a model's ability to translate Bodo sentences. Mixed scripts avoid invented Bodo grammar.
const cases = [
  ['English', 'Two dogs in a forest, watercolor, overhead view, no people', 'Two dogs in a forest, watercolor, overhead view, no people'],
  ['Roman Bodo', 'swima', 'A dog'],
  ['Roman variant', '/image suima', 'A dog'],
  ['Devanagari Bodo', 'सैमा', 'A dog'],
  ['Mixed Roman constraints', '2 swima in hagra, watercolor, no people', '2 dogs in a forest, watercolor, no people'],
  ['Mixed Devanagari constraints', '2 सैमा, watercolor, no people', '2 dogs, watercolor, no people'],
];
for (const [name, original, expected] of cases) {
  test(`${name}: dedicated image route isolates intent from chat/history`, async () => {
    upstream.push(gemini(success(expected)));
    const response = await call({ task: 'image_prompt_enhancement', prompt: original, script: 'Latin',
      contents: [{ role: 'model', parts: [{ text: 'Earlier we discussed robots' }] }] });
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.originalPrompt, visualInput({ prompt: original }));
    assert.equal(result.enhancedPrompt, expected);
    assert.equal(result.lexiconVersion, lexicon.version);
    assert.match(requests[0].url, /:generateContent\?key=/);
    assert.equal(requests[0].body.contents.length, 1);
    assert.equal(requests[0].body.contents[0].parts[0].text, result.originalPrompt);
    assert.ok(requests[0].body.systemInstruction.parts[0].text.startsWith(VISUAL_POLICY));
    assert.equal(requests[0].body.generationConfig.responseMimeType, 'application/json');
    assert.doesNotMatch(requests[0].body.systemInstruction.parts[0].text, /witty, casual/);
  });
}
test('genuine ambiguity returns clarification, not invented image', async () => {
  upstream.push(gemini({ enhancedPrompt: '', needsClarification: true, clarificationQuestion: 'Do you mean a fish or something else?' }));
  const result = await (await call({ task: 'image_prompt_enhancement', prompt: 'na' })).json();
  assert.equal(result.needsClarification, true);
  assert.equal(result.enhancedPrompt, '');
});
test('reject empty, malformed and chat/TTS enhancement outputs', async () => {
  for (const output of [success(''), success('---TTS--- सैमा'), success('RESPONSE: Hi'),
    { enhancedPrompt: 'A robot' }, { enhancedPrompt: 'A dog', needsClarification: true, clarificationQuestion: 'Which?' }]) {
    assert.throws(() => parseVisualResult(JSON.stringify(output)));
    upstream.push(gemini(output));
    assert.equal((await call({ task: 'image_prompt_enhancement', prompt: 'swima' })).status, 502);
  }
});
test('empty image request does not reach upstream', async () => {
  assert.equal((await call({ task: 'image_prompt_enhancement', prompt: '/image ' })).status, 400);
  assert.equal(requests.length, 0);
});
test('chat keeps raw sentence and history but receives same reference', async () => {
  const contents = [{ role: 'model', parts: [{ text: 'Previous incorrect guess: robot' }] },
    { role: 'user', parts: [{ text: 'suima, not a robot' }] }];
  upstream.push(new Response('data: {"candidates":[]}\n\n', { headers: { 'Content-Type': 'text/event-stream' } }));
  const response = await call({ plan: 'Free', script: 'roman', contents });
  assert.equal(response.status, 200);
  assert.deepEqual(requests[0].body.contents, contents);
  const policy = requests[0].body.systemInstruction.parts[0].text;
  assert.ok(policy.endsWith(CONVERSATION_POLICY));
  assert.ok(policy.includes(BODO_REFERENCE));
  assert.equal(response.headers.get('X-Orki-Lexicon-Version'), lexicon.version);
  assert.equal(response.headers.get('X-Orki-Model'), 'gemini-3.5-flash-lite');
});
test('Devanagari and voice chat share policy and retain inline audio', async () => {
  for (const parts of [[{ text: 'सैमा' }], [{ inlineData: { mimeType: 'audio/mp4', data: 'fixture' } }, { text: 'Transcribe accurately.' }]]) {
    upstream.push(new Response('data: {}\n\n'));
    assert.equal((await call({ script: 'deva', contents: [{ role: 'user', parts }] })).status, 200);
    assert.deepEqual(requests.at(-1).body.contents[0].parts, parts);
    assert.match(requests.at(-1).body.systemInstruction.parts[0].text, /transcribe what was spoken/);
  }
});
test('configured engines and Pro fallback expose actual model without changing vocabulary', async () => {
  env.set('ORKI_PRO_MODEL', 'verified-pro-resource');
  env.set('ORKI_FALLBACK_MODEL', 'verified-fallback-resource');
  upstream.push(new Response('unavailable', { status: 503 }), new Response('data: {}\n\n'));
  const response = await call({ plan: 'Pro', model: 'okafwr-2.1', contents: [], script: 'roman' });
  assert.equal(response.headers.get('X-Orki-Model'), 'verified-fallback-resource');
  assert.match(requests[0].url, /verified-pro-resource:/);
  assert.match(requests[1].url, /verified-fallback-resource:/);
  assert.deepEqual(requests[0].body, requests[1].body);
});
test('visual fallback records actual engine too', async () => {
  upstream.push(new Response('unavailable', { status: 429 }), gemini(success('A dog')));
  const result = await (await call({ task: 'image_prompt_enhancement', plan: 'Pro', model: 'okafwr-2.1', prompt: 'swima' })).json();
  assert.equal(result.model, 'gemini-2.5-flash');
  assert.deepEqual(requests[0].body, requests[1].body);
});
test('warmup does not pay for a model request', async () => {
  const response = await call({ ping: true, warmup: true });
  assert.equal(response.status, 200);
  assert.equal(requests.length, 0);
});
test('Android contract is wired to dedicated route and existing image backend', () => {
  const api = readFileSync(new URL('../app/src/main/java/com/example/data/network/OrkiApiService.kt', import.meta.url), 'utf8');
  const protocol = readFileSync(new URL('../app/src/main/java/com/example/data/network/VisualPromptProtocol.kt', import.meta.url), 'utf8');
  const vm = readFileSync(new URL('../app/src/main/java/com/example/ui/viewmodel/OrkiViewModel.kt', import.meta.url), 'utf8');
  assert.match(protocol, /put\("task", "image_prompt_enhancement"\)/);
  assert.match(api, /VisualPromptProtocol.request\(trimmed, isVideo, script\)/);
  assert.match(api, /\.url\(STREAM_API_URL\)/);
  assert.match(vm, /prompt = enhancedPrompt/);
  assert.match(vm, /catch \(e: com.example.data.network.VisualPromptClarificationException\)/);
  assert.match(vm, /persistThread\(_uiState.value.messages\)/);
});

test('sentence-level lexical matches retain count, style and negation outside the index', () => {
  for (const [input, expected] of [
    ['2 suima in hagra, watercolor, no people', ['dog', 'forest']],
    ['2 सैमा, watercolor, no people', ['dog']],
    ['phone swima, not a robot', ['dog']],
    ['swimming, not swimathon', []],
  ]) {
    assert.deepEqual(matchesBodo(input).map(entry => entry.id), expected);
  }
});
test('inherited natural Bodo sentences are preserved, not declared verified translations', async () => {
  const corpus = JSON.parse(readFileSync(new URL('../data/bodo/evaluation-cases.json', import.meta.url), 'utf8'));
  for (const fixture of corpus.cases.filter(item => item.status === 'needs_native_review')) {
    assert.equal(fixture.intent, null);
    upstream.push(new Response('data: {}\n\n'));
    await call({ script: fixture.script, contents: [{ role: 'user', parts: [{ text: fixture.prompt }] }] });
    assert.equal(requests.at(-1).body.contents[0].parts[0].text, fixture.prompt);
  }
});
test('video route preserves duration and motion rather than forcing eight seconds', async () => {
  upstream.push(gemini(success('A dog running, static camera, 5 seconds')));
  const response = await call({ task: 'image_prompt_enhancement', prompt: 'A dog running, static camera, 5 seconds', mediaType: 'video' });
  assert.equal(response.status, 200);
  assert.match(requests[0].body.systemInstruction.parts[0].text, /Preserve requested duration/);
});

test('chat honors smaller live token budgets without exceeding plan caps', async () => {
  for (const [requested, expected] of [[180, 180], [99999, 1500], [-1, 1500], ['180', 1500]]) {
    upstream.push(new Response('data: {}\n\n'));
    await call({ plan: 'Free', script: 'roman', contents: [], generationConfig: { maxOutputTokens: requested } });
    assert.equal(requests.at(-1).body.generationConfig.maxOutputTokens, expected);
  }
});

test('image clarification script is selected independently of the chat persona', async () => {
  upstream.push(gemini({ enhancedPrompt: '', needsClarification: true, clarificationQuestion: 'fixture question' }));
  await call({ task: 'image_prompt_enhancement', script: 'deva', prompt: 'na' });
  assert.match(requests[0].body.systemInstruction.parts[0].text, /otherwise ask in Devanagari Bodo/);
  assert.match(requests[0].body.systemInstruction.parts[0].text, /final visual prompt remains English/);
});
