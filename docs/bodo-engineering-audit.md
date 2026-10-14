# Orki engineering and intelligence audit — 2026-10-13

## Scope and outcome

Baseline: `11218b9bf67ae839b6e640f0653c17848ab15a82`. Working branch: `arena/a50b281b-orki-ai-official` (this Arena session cannot switch to the differently named branch in the request). Initial working tree was clean; existing ZIPs, image integrations, screens, preferences, playback and unrelated files were preserved.

Implemented: dedicated image-enhancement routing, structured interpretation/clarification handling, shared provenance-aware Bodo vocabulary, non-destructive intent routing, two reproducible speech-normalization fixes, multipart streaming parsing, routing observability and local regression tooling. **This is not a claim that Orki now understands Bodo grammar or has working offline synthesis.**

## 1. Root causes in the checked-in pipeline

### Image enhancement was sent to the wrong route/persona

The old Android enhancer sent ordinary chat `contents`, `plan="Pro"`, `model="gemini-2.0-flash"`, `script="Latin"`, with enhancement instructions inside a user message. The archive selects Pro only for `model="okafwr-2.1"`; thus this request actually chose the standard engine. Only `script="roman"` selects the Roman persona, so `Latin` selected the Devanagari chat persona. The dedicated `task="image_prompt_enhancement"` branch, including the archive's dog vocabulary, was never selected by this client method.

Consequently the image enhancer did **not** receive the normal ViewModel vocabulary injection or the dedicated enhancer system prompt. Instead it received a chat persona and joke examples competing with a user-level request for English. This is a concrete cause of inconsistent interpretation paths, not evidence of the exact historical production model output.

The old Android prompt also forced photorealism, camera/lighting additions and colours/motifs, even when the user requested a different style or did not specify these details. New policy prioritizes original subject/actions/counts/appearance/attire/setting/style/composition/exclusions; it does not require elaboration or a constraint-dropping 60-word cap.

### Vocabulary reached different components inconsistently

There were separate animal/gloss lists in ViewModel, speech normalizer and the archive enhancer. Some differed: `mouzi` versus `mousi`, `mousho` versus `mwsou`, and `dwi` as water versus water/river. New generated reference is identical across Android and edge chat/voice/image requests. Water is no longer automatically described as a river; uncertain forms are labelled, not silently merged into purported verified vocabulary.

`quick-processor-merged-index-dog-fix.ts` and `quick-processor-merged-index.ts` are **absent from this checkout**. The only available quick-processor implementation was `quick-processor (1).zip:source/index.ts`. It is now extracted into reviewable deployment source under `supabase/functions/quick-processor/`; the original archive remains unchanged. No deployed export or live project access was available, so repository/deployment parity is **unknown**, not asserted.

### Speech cleanup changed valid Devanagari and skipped Roman words

1. Java `\b` matra cleanup changed `सैमा` to `सऐमा` on the actual JVM used here. Repair now checks that a vowel mark does not follow any Unicode letter/combining mark.
2. Loanword conversion or existing Devanagari triggered a whole-string early return, leaving Roman Bodo unconverted. Processing now handles each Roman token independently, including punctuation-adjacent tokens.

These functions are speech-only; they do **not** run on raw chat/image requests. Their bugs explain speech spelling/pronunciation corruption, not a proven historical chat interpretation failure.

### Other relevant findings

- Local Bodo image trigger detection globally removed `छबि` / `बानाय` anywhere in a sentence. New detector keeps the full Bodo request; routing is now independently tested.
- Chat uses 4/6/12/24 recent messages for Guest/Free/Plus/Pro. Model replies can supply stale assumptions. New policy says latest subject/corrections override history, but no live history-contamination evaluation was possible.
- The old SSE client read only the first text part of each event and swallowed all parsing exceptions. New parsing retains all non-thought text parts (including speech delimiters), accepts `data:` with or without a following space, surfaces explicit upstream errors, and closes HTTP/reader resources.
- The archived edge ignored requested token budgets, including live talk's 180-token request. It now honors positive integer requests up to the existing plan cap.
- The old fallback log named a different model from its actual URL. Logs and response metadata now identify the selected/fallback engine.

## 2. Request/model map (source evidence, not live verification)

| Request | Actual checked-in path |
|---|---|
| Chat, attachment, voice/live talk | Android → Supabase `quick-processor` → Gemini SSE; audio is `inlineData`, not a separate checked-in ASR model |
| Pro + `okafwr-2.1` | Default `gemini-3.5-flash`; on error, default `gemini-2.5-flash` with the same prompt/contents |
| Other plans/model labels | Default `gemini-3.5-flash-lite`; standard route has no automatic fallback in the preserved logic |
| New image enhancement | Same Supabase function, **dedicated task**, isolated raw prompt/no chat history → Gemini non-streaming structured output |
| Image rendering | Existing `ImageGenerationService`: Cloudflare first only if explicitly selected; otherwise Pollinations Flux → Turbo → Cloudflare → standard Pollinations fallback |
| TTS | Android speech text → `swift-worker` → existing EC2 `/synthesize` with text/gender; server model unknown |
| `quick-task` | Archive proxies EC2 `/transliterate`; no Android reference found |

There is **no fine-tuned Gemini resource/configuration** in these sources. Client model labels do not establish fine-tuning. The availability of the default `gemini-3.5-*` IDs is unverified; they were not replaced with guessed APIs. Operators can set `ORKI_STANDARD_MODEL`, `ORKI_PRO_MODEL`, `ORKI_FALLBACK_MODEL` to API-verified **bare engine IDs**. This is not a Vertex/tuned-resource integration. Chat headers `X-Orki-Model` / `X-Orki-Lexicon-Version`, and visual JSON metadata, expose actual route decisions for later live verification.

The Azure DALL-E helper/preferences exist, but `generateImage` does not invoke that helper; Perchance is also not the main method's active path. These pre-existing integration discrepancies were documented, not rewritten. Existing embedded image credentials need separate rotation/server-side enforcement; no secrets were added or reproduced in this report.

## 3. Lexicon inventory and verification

**32 structured entries**: 8 animals, water/forest, meal/household, teach/learn, I/you, what/who, absence, greeting/thanks/good, and Devanagari numerals 1–10. Canonical identity/scripts, variants, meanings/POS/domain, source, meaning/form status, ambiguity notes and an examples field are available in `data/bodo/lexicon.json`.

- **1 user-confirmed meaning:** custom `swima` / `suima` = dog.
- **31 provisional meanings; all standard spellings/forms provisional.** No native-speaker/reference verification was obtained.
- **0 independently verified entries or verified natural example translations.** Empty examples are intentional, not fabricated material. Two existing natural sentences are separately held for native review, with no asserted gold translation.
- `gorai` horse, `mouzi` cat and `hagrama` forest are inherited archive entries, not newly verified discoveries. Unsupported Roman numeral forms, horse Devanagari and questionable `mousho` equivalence were not invented.

See `data/bodo/README.md` for schema, verification/promotion and evaluation procedure. The starter lexicon is not a morphology engine; remaining legacy speech mappings are a staged-migration backlog.

## 4. Regression results and measured before/after

The **actual baseline Kotlin code** and changed code were compiled independently with Kotlin 2.2.10 and run on Java 21.0.8:

| Input | Baseline output | Changed output |
|---|---|---|
| `swima` | `सऐमा।` | `सैमा।` |
| `suima` | `सऐमा।` | `सैमा।` |
| `सैमा` | `सऐमा।` | `सैमा।` |
| `phone swima` | `फोन swima।` | `फोन सैमा।` |
| `सैमा, suima!` | `सऐमा, suima।` | `सैमा, सैमा।` |
| `swima,सैमा` | `swima, सऐमा।` | `सैमा, सैमा।` |

Final local results:

| Actual command | Result |
|---|---|
| `python3 scripts/generate-bodo-lexicon.py --check` | Pass; 32 entries, projections current |
| `node --test tests/bodo-pipeline.test.mjs` | **28/28 pass**; actual edge handler, Gemini boundary mocked |
| `npx --yes deno@2.9.6 check supabase/functions/quick-processor/index.ts` | Pass |
| `bash scripts/test-bodo-jvm.sh` (Java/Kotlin on PATH) | **114 assertions pass** against actual lexicon/normalizer/intent code |
| `node --check scripts/evaluate-bodo-live.mjs` | Pass; evaluator not run against a live API |
| `git diff --check` | Pass |
| `gradle testDebugUnitTest assembleDebug --stacktrace` | Blocked: `gradle: command not found` |
| `./gradlew testDebugUnitTest assembleDebug --stacktrace` | Blocked: wrapper script/JAR absent |

Initially no Java runtime/Kotlin CLI was installed. A temporary Java runtime was obtained via `jdk4py==21.0.8.2` (PyPI), and `kotlin-compiler@2.2.10` via npm, outside the repo. GitHub release downloads redirected to a disallowed host and failed. Actual smoke command:

```sh
JAVA_HOME=/home/user/audit-scratch/jdk/jdk4py/java-runtime \
PATH=/home/user/audit-scratch/jdk/jdk4py/java-runtime/bin:/home/user/audit-scratch/kotlin/node_modules/kotlin-compiler/bin:$PATH \
  bash scripts/test-bodo-jvm.sh
```

No Android SDK/full Gradle setup was available. The added Android JUnit/Robolectric tests, including streaming/protocol tests, are **not locally executed**. Existing Android CI runs `testDebugUnitTest` then `assembleDebug`; a new independent CI workflow runs generated-file checking, edge tests/type checking and the JVM smoke suite.

**Understanding limitation:** local sentence checks prove raw-input/count/style/negation preservation and reference delivery, not correct full-sentence Bodo interpretation. Gemini replies and native language accuracy were not measured. Prompt edits and dictionary lookup are not proof of language understanding.

## 5. Image flow status

Tested fixtures include English, Roman `swima`/`suima`, Devanagari `सैमा`, mixed-script counts/styles/exclusions, a history about robots, ambiguous `na`, empty prompts, bad/empty/chat/TTS outputs, fallback and video constraint preservation. Local transport tests verify the dedicated `:generateContent` path, isolated original prompt, same lexicon, and structured clarification contract. The actual JVM detector tests verify `/image`, English natural requests with Bodo subjects, and non-destructive inherited Devanagari triggers.

A model-produced `needsClarification` question is shown in the chat, state is reset/persisted, and the ViewModel returns before invoking the image backend. The user can answer with a fuller `/image` request. No generator tab or new screen was introduced.

The existing image engines/endpoints were left untouched. Their live HTTP/rendering behavior is **untested** in this environment. Policy enforcement for arbitrary subject/count/negation fidelity is still model-dependent and needs original-versus-enhanced human comparison. Network failure, old edge deployment or malformed enhancement safely falls back to the original prompt—not a chat greeting/TTS reply—but an untranslated raw Bodo prompt may still be poorly understood by the image backend. This remaining limitation is explicit.

Natural chat-first image detection remains conservative/limited; no assumption is made that a bare Bodo animal word is an image request. General native Bodo image-intent classification and spelling/morphology need verified sentences and live evaluation.

## 6. Offline TTS

See `docs/offline-bodo-tts-audit.md`. Rasa-specific sherpa emotion support exists upstream, so the candidate is worth an isolated export/device spike. Exact artifact, tokenizer/config, speaker/style IDs, sample rate, weight license and Bodo waveform were not directly verified. Secondary Android sources disagree on sample rate. No runtime was integrated and no dependency/model binary was added. All existing playback, caches, previews, voices and live-talk behavior are preserved; pronunciation/device performance remains untested.

## 7. Changed files and purpose

| Files | Purpose |
|---|---|
| `app/.../data/network/OrkiApiService.kt` | Dedicated visual route/protocol; multipart SSE handling and resource closure |
| `app/.../data/network/VisualPromptProtocol.kt` | Validate task/original/status; separate clarification from a renderable prompt |
| `app/.../ui/viewmodel/OrkiViewModel.kt` | Shared reference, explicit-language override, testable intent routing, clarification stop/reset/persistence |
| `app/.../data/language/VisualIntentDetector.kt` | Isolate existing local intent routing for real JVM tests; preserve full Bodo sentence instead of global deletion |
| `app/.../data/audio/BodoTtsNormalizer.kt` | Shared vocabulary forms; mixed-script processing; Unicode-safe matra cleanup |
| `data/bodo/{lexicon.json,README.md,evaluation-cases.json}` | Evidence-aware source vocabulary, verification procedure, labelled live-review corpus |
| `scripts/generate-bodo-lexicon.py` | Generate/check Android and edge projections with collision/provenance checks |
| `app/.../data/language/BodoLexicon.kt`, `supabase/functions/_shared/bodo-lexicon.generated.ts` | Generated shared lookup/reference projections |
| `supabase/functions/_shared/bodo-policy.ts` | Context/uncertainty and visual intent-preservation policy; input/result validation |
| `supabase/functions/quick-processor/index.ts` | Reviewable archive-derived edge source; dedicated structured visual route, shared chat reference, configurable engines, diagnostics, bounded token budgets, warmup |
| `tests/{bodo-pipeline.test.mjs,BodoJvmSmoke.kt}` | Actual edge handler contracts and actual pure JVM preprocessing/intent tests |
| `app/src/test/.../{BodoLanguageRegressionTest.kt,VisualPromptProtocolTest.kt}` | Android/JUnit/Robolectric coverage including parser/protocol; pending Android run |
| `scripts/{test-bodo-jvm.sh,evaluate-bodo-live.mjs}` | Reproducible independent JVM test; opt-in live evaluation without source-code credentials |
| `.github/workflows/bodo-regressions.yml` | Independent checks in CI |
| `docs/{bodo-engineering-audit.md,offline-bodo-tts-audit.md}` | Findings, limits, deployment instructions and model-specific compatibility evidence |

## 8. Deployment and remaining work

1. Review the extracted edge implementation against the **currently deployed function export**. The ZIP is not proof of deployment identity. Preserve any production-only fixes/secrets/JWT configuration.
2. Verify model availability with the real project's Gemini API. Keep `GEMINI_API_KEY` in Supabase secrets; select bare model IDs through the optional `ORKI_*_MODEL` secrets if needed. No secrets belong in the APK or source. Do not assume app labels correspond to a fine-tuned model.
3. Run local checks above. **Deploy the edge before the new app**, preserving existing authentication policy:
   ```sh
   supabase functions deploy quick-processor --project-ref aqhedgyiworhbfkrzreb
   ```
   CLI login/project rights are required outside this sandbox. Do not disable JWT verification merely to bypass an auth error; reconcile production/client auth separately if necessary.
4. In an Android-capable environment, run `gradle testDebugUnitTest assembleDebug --stacktrace` (the repo's CI uses Gradle 9.3.1/JDK 21). Fix any integration issues before distributing an APK. No successful APK build is claimed here.
5. Run the opt-in live evaluator described in `data/bodo/README.md`, compare original/enhanced subject/count/style/exclusions, and inspect reported model/lexicon version. Test `/image` plus natural chat requests and a Bodo clarification on a device against the existing render endpoints.
6. Have native speakers verify spellings, senses, full sentences, negation/question grammar and replies in both scripts; populate sourced example translations and gold sentence tests before expanding vocabulary/training. Test history corrections and explicit requested languages live.
7. Execute the isolated offline TTS artifact/export/device spike from the companion report. Keep current remote TTS/cache/playback until all compatibility, license, resource and native-audio gates pass.

Changes are local and reviewable. **No Supabase deployment, live Gemini/image/TTS call, remote model inference, device test, push or PR is claimed.** Build outputs/tool downloads are ignored or outside the repository; no model weights were committed.
