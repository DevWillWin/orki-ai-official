# Bodo lexicon: evidence before expansion

Edit `lexicon.json`, then run `python3 scripts/generate-bodo-lexicon.py`.
The generator produces Android and edge projections. `--check` fails on stale projections or duplicate forms. Neither lookup normalizes the actual user request: NFC/case normalization applies only to index keys. There is no fuzzy correction, suffix stripping, global semantic replacement, or automatic language identification.

## Entry fields

- `id`, `canonical`, `canonicalScript`: stable identity and preferred stored form.
- `roman`, `devanagari`: lookup forms, not guarantees of standard orthography.
- `meanings`, `partOfSpeech`, `domain`: contextual glosses, not translation grammar.
- `meaningStatus`: `verified`, `user_confirmed`, or `provisional`.
- `formStatus`: independent spelling/script verification status.
- `sources`: traceable provenance. A repository mapping is not an independent language reference.
- `examples`: reserved for sourced natural sentences **with translations and their own verification status**. Empty is better than invented examples.
- `notes`: ambiguity, collisions, and caveats.

Current inventory: **32 entries**, covering animals, household/meal vocabulary, teach/learn, I/you, what/who, absence, greeting/thanks/good, and Devanagari numerals 1–10. Only the **dog meaning** is user-confirmed as product vocabulary (`swima`/`suima`). The other **31 meanings**, and all stored forms as standard-language spellings, are provisional. There are **zero independently/native-speaker-verified entries**. Roman number forms, zero's linguistic meaning, and horse's Devanagari form were deliberately not invented. `dwi` means inherited *water*, not automatically *river*. `dao` and short `na` require context. `mousi` must not automatically become a Hindi kinship term, but an isolated token is not proof of Bodo either.

The older edge-only `mousho` cow form was not merged into `mwsou` without evidence. `mouzi`, `hagrama`, and `gorai` are inherited edge material with provisional provenance, not newly verified discoveries. Legacy speech fallback entries outside this starter lexicon remain in `BodoTtsNormalizer`; this is a staged migration, not a complete language dictionary.

## Promotion to verified

A reviewer should record a reliable reference (edition/page or stable URL) or native-speaker review, date, correct scripts, sense/POS, context/ambiguity, and permission for examples. Validate the Roman and Devanagari forms independently. Add separate senses rather than overriding an existing short ambiguous word. Do not promote generated translations or model agreement to verified evidence.

`evaluation-cases.json` includes product-confirmed single-word checks, English controls, explicitly labelled code-switched constraints, and two inherited natural Bodo sentences with **no gold translation**. Those natural sentences remain `needs_native_review`; they are not training examples or a correctness benchmark yet.

## Tests and live evaluation

```sh
python3 scripts/generate-bodo-lexicon.py --check
node --test tests/bodo-pipeline.test.mjs
bash scripts/test-bodo-jvm.sh  # java + kotlinc required
```

The local suites test lookup, raw request preservation, routing/contracts, and actual JVM speech preprocessing. They do **not** evaluate a real model's Bodo grammar or generated audio.

For a live model evaluation, outside the restricted sandbox:

```sh
ORKI_EDGE_URL='<deployed quick-processor HTTPS URL>' \
  node scripts/evaluate-bodo-live.mjs --run
```

Optional auth is read from `ORKI_EDGE_AUTH_TOKEN` / `ORKI_EDGE_APIKEY`, never source code. `--case=dog-roman` limits quota use. Results go to ignored `.build-outputs/bodo-eval/`; review prompt fidelity manually and Bodo conversation with a native speaker. The evaluator never marks HTTP 200 as language correctness.
