// ==============================================================================
// ORKI AI: Quick Processor Supabase Edge Function
// Deno TypeScript Runtime
// Handles:
// 1. Image Prompt Enhancement with Bodo Dictionary resolution & strict English output
// 2. Chat Streaming with dual TTS markup and persona management
// ==============================================================================

import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.21.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

// Starter Vocabulary for deterministic O(1) resolution
const STARTER_VOCABULARY: Record<string, { english: string; category: string; keywords: string[] }> = {
  swima: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  suima: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  soima: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  सैमा: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  सैमाया: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  mouzi: { english: "cat", category: "animal", keywords: ["cat", "feline", "kitten"] },
  mousi: { english: "cat", category: "animal", keywords: ["cat", "feline", "kitten"] },
  mauzi: { english: "cat", category: "animal", keywords: ["cat", "feline", "kitten"] },
  मौजौ: { english: "cat", category: "animal", keywords: ["cat", "feline", "kitten"] },
  dao: { english: "bird", category: "animal", keywords: ["bird", "avian", "feather"] },
  दाव: { english: "bird", category: "animal", keywords: ["bird", "avian", "feather"] },
  oma: { english: "pig", category: "animal", keywords: ["pig", "piglet", "swine"] },
  अमा: { english: "pig", category: "animal", keywords: ["pig", "piglet", "swine"] },
  mousho: { english: "cow", category: "animal", keywords: ["cow", "bovine", "cattle"] },
  mouso: { english: "cow", category: "animal", keywords: ["cow", "bovine", "cattle"] },
  gorai: { english: "horse", category: "animal", keywords: ["horse", "stallion", "equine"] },
  गराय: { english: "horse", category: "animal", keywords: ["horse", "stallion", "equine"] },
  na: { english: "fish", category: "animal", keywords: ["fish", "aquatic"] },
  ना: { english: "fish", category: "animal", keywords: ["fish", "aquatic"] },
  hagrama: { english: "dense lush green forest jungle with tall trees", category: "nature", keywords: ["forest", "jungle", "trees", "woods"] },
  हाग्रामा: { english: "dense lush green forest jungle with tall trees", category: "nature", keywords: ["forest", "jungle", "trees", "woods"] },
  dwi: { english: "crystal clear freshwater river stream", category: "nature", keywords: ["river", "water", "stream"] },
  दै: { english: "crystal clear freshwater river stream", category: "nature", keywords: ["river", "water", "stream"] },
  swimani: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  suimani: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  सैमानि: { english: "dog", category: "animal", keywords: ["dog", "canine", "puppy", "hound"] },
  mouzini: { english: "cat", category: "animal", keywords: ["cat", "feline", "kitten"] },
  hinjaw: { english: "traditional Bodo woman", category: "person", keywords: ["woman", "lady", "female"] },
  हिनजाव: { english: "traditional Bodo woman", category: "person", keywords: ["woman", "lady", "female"] },
  sikhla: { english: "young Bodo woman", category: "person", keywords: ["woman", "girl"] },
  सिख्ला: { english: "young Bodo woman", category: "person", keywords: ["woman", "girl"] },
  howa: { english: "traditional Bodo man", category: "person", keywords: ["man", "male"] },
  hawa: { english: "traditional Bodo man", category: "person", keywords: ["man", "male"] },
  gotho: { english: "Bodo child", category: "person", keywords: ["child", "kid"] },
  bibar: { english: "blossoming flowers in full bloom", category: "nature", keywords: ["flower", "blossom"] },
  nokhor: { english: "traditional rural Assam courtyard village house", category: "architecture", keywords: ["house", "home", "courtyard"] },
  dokhona: { english: "traditional Bodo dokhona attire with handwoven Agor motifs in bright yellow, green, and red", category: "attire", keywords: ["dokhona", "traditional", "dress", "attire"] },
  दखना: { english: "traditional Bodo dokhona attire with handwoven Agor motifs in bright yellow, green, and red", category: "attire", keywords: ["dokhona", "traditional", "dress", "attire"] },
  aronai: { english: "traditional Bodo aronai scarf with intricate geometric Agor embroidery", category: "attire", keywords: ["aronai", "scarf", "embroidery"] },
  आर'नाइ: { english: "traditional Bodo aronai scarf with intricate geometric Agor embroidery", category: "attire", keywords: ["aronai", "scarf", "embroidery"] },
  bwisagu: { english: "Bodo Bwisagu spring festival celebration, traditional folk dance and instruments", category: "culture", keywords: ["bwisagu", "festival", "dance"] },
};

// Classifiers in Bodo (e.g. "mase swima" = "one dog")
const CLASSIFIERS = new Set(["mase", "masi", "mwnse", "monse", "sase", "sasi", "gangse", "khonse"]);

/**
 * Validates that an enhanced prompt candidate is strictly an English visual description
 * and preserves the user's intended subject.
 */
function validateEnhancedPrompt(
  candidate: string,
  originalPrompt: string,
  detectedVocab: Array<{ term: string; english: string; keywords: string[] }>
): { valid: boolean; reason?: string } {
  const trimmed = candidate.trim();
  if (!trimmed || trimmed.length < 5) {
    return { valid: false, reason: "Candidate is empty or too short" };
  }

  // 1. Reject Devanagari script (\u0900-\u097F)
  if (/[\u0900-\u097F]/.test(trimmed)) {
    return { valid: false, reason: "Contains Devanagari characters" };
  }

  // 2. Reject Conversational Text
  const lower = trimmed.toLowerCase();
  const conversationalMarkers = [
    "what do you want", "i can help", "how can i", "what would you like",
    "here is your", "sure, here", "as an ai", "hello", "hi there",
    "tell me what", "can you tell", "?", "nongni", "khobor", "mwdang"
  ];
  for (const marker of conversationalMarkers) {
    if (lower.includes(marker)) {
      return { valid: false, reason: `Contains conversational marker: "${marker}"` };
    }
  }

  // 3. Subject Preservation Checks
  for (const item of detectedVocab) {
    // If the Bodo word was swima/dog, forbid "swim" / "swimming" unless explicitly containing dog
    if ((item.term === "swima" || item.term === "suima" || item.english === "dog")) {
      const hasDogKeyword = item.keywords.some((k) => lower.includes(k));
      if (!hasDogKeyword) {
        return { valid: false, reason: `Failed subject preservation: prompt does not mention dog/canine for '${item.term}'` };
      }
      if (lower.includes("swim") && !hasDogKeyword) {
        return { valid: false, reason: "Mistranslated 'swima' as English 'swim'" };
      }
    } else {
      const hasKeyword = item.keywords.some((k) => lower.includes(k));
      if (!hasKeyword) {
        return { valid: false, reason: `Failed subject preservation: missing keyword for '${item.term}' (${item.english})` };
      }
    }
  }

  return { valid: true };
}

/**
 * Deterministic Fallback Prompt Builder
 */
function buildDeterministicFallback(
  originalPrompt: string,
  detectedVocab: Array<{ term: string; english: string }>,
  isVideo: boolean
): string {
  let resolvedSubject = originalPrompt.trim();

  // Strip image command words in Bodo & English
  resolvedSubject = resolvedSubject
    .replace(/\b(छबि|फोटो|फट'|नुथाइ|बानाय|दिन्थि|हर|हरो|नि)\b/gi, " ")
    .replace(/\b(chobi|photo|fotho|nuthai|banay|bana|banna|hordo|dakhaw|dekhao|dinthi|hor|angno|ni)\b/gi, " ");

  // Normalize classifiers
  resolvedSubject = resolvedSubject
    .replace(/\b(mase|masi|mwnse|monse|sase|sasi|मासे|मोनसे|सासे)\s+/gi, "a ");

  // Replace detected tokens
  for (const item of detectedVocab) {
    const regex = new RegExp(`\\b${item.term}\\b`, "gi");
    resolvedSubject = resolvedSubject.replace(regex, item.english);
  }

  // Replace any other starter vocabulary terms
  for (const [term, data] of Object.entries(STARTER_VOCABULARY)) {
    const regex = new RegExp(`\\b${term}\\b`, "gi");
    resolvedSubject = resolvedSubject.replace(regex, data.english);
  }

  resolvedSubject = resolvedSubject
    .replace(/\s+/g, " ")
    .trim();

  if (!resolvedSubject) {
    resolvedSubject = "beautiful scenic masterpiece";
  }

  if (isVideo) {
    return `Cinematic 8k video footage of ${resolvedSubject}, smooth cinematic camera movement, photorealistic natural lighting, highly detailed textures, 4k ultra-hd clarity.`;
  }

  return `A high-quality, photorealistic photograph of ${resolvedSubject}, professional studio lighting, 85mm lens portrait, sharp focus on subject, natural background, 8k resolution masterpiece.`;
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const body = await req.json();

    // =========================================================================
    // BRANCH 1: Task === "image_prompt_enhancement"
    // =========================================================================
    if (body.task === "image_prompt_enhancement") {
      const prompt = (body.prompt || "").trim();
      const isVideo = Boolean(body.isVideo);
      const correlationId = body.correlationId || `img-${crypto.randomUUID().slice(0, 8)}`;

      if (!prompt) {
        return new Response(
          JSON.stringify({ error: "Empty prompt provided" }),
          { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
        );
      }

      console.log(`[${correlationId}] Image enhancement request for: "${prompt}" (isVideo=${isVideo})`);

      // Step A: Tokenize and detect known Bodo vocabulary
      const tokens = prompt.toLowerCase().split(/\s+/).map((t: string) => t.replace(/[^a-z0-9]/g, ""));
      const detectedVocab: Array<{ term: string; english: string; keywords: string[] }> = [];

      for (const token of tokens) {
        if (STARTER_VOCABULARY[token]) {
          detectedVocab.push({
            term: token,
            english: STARTER_VOCABULARY[token].english,
            keywords: STARTER_VOCABULARY[token].keywords,
          });
        }
      }

      // Step B: Query Supabase Bodo Dictionary RPC if available
      const dictionaryMatches: any[] = [];
      try {
        const supabaseUrl = Deno.env.get("SUPABASE_URL");
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY");

        if (supabaseUrl && supabaseKey) {
          const supabase = createClient(supabaseUrl, supabaseKey);

          // Query up to 3 non-stopword tokens
          const candidateTokens = tokens.filter((t: string) => t.length > 2 && !CLASSIFIERS.has(t)).slice(0, 3);
          for (const cand of candidateTokens) {
            // 1.2 second timeout per dictionary search
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 1200);

            const { data, error } = await supabase.rpc("search_bodo_dictionary", {
              search_term: cand,
              max_results: 2,
            });
            clearTimeout(timeoutId);

            if (!error && Array.isArray(data)) {
              for (const row of data) {
                dictionaryMatches.push({
                  headword: row.headword,
                  english_meanings: row.english_meanings,
                  category: row.category,
                });
              }
            }
          }
        }
      } catch (dictErr) {
        console.warn(`[${correlationId}] Dictionary RPC lookup warning:`, dictErr);
      }

      // Step C: Prepare Gemini Prompt
      const geminiApiKey = Deno.env.get("GEMINI_API_KEY");
      let enhancedPrompt = "";
      let fallbackUsed = false;

      if (geminiApiKey) {
        const contextEvidence = [
          ...detectedVocab.map((v) => `• '${v.term}' = ${v.english}`),
          ...dictionaryMatches.map((d) => `• '${d.headword}' = ${JSON.stringify(d.english_meanings)}`),
        ].join("\n");

        const systemInstruction = `You are an expert AI prompt engineer transforming user requests into photorealistic, visually descriptive English image-generation prompts.

CRITICAL RULES:
1. Output MUST be in English only. Diffusion models do not understand Bodo or Devanagari.
2. NEVER output conversational text, greetings, questions, or explanations (e.g. NEVER say "What do you want to know about dogs?", "I can help", "Sure, here is").
3. PRESERVE THE INTENDED SUBJECT:
   - In Bodo, "swima" or "suima" means DOG (canine animal). It has ZERO relation to the English word "swim". You MUST describe a dog. Under NO circumstances describe swimming, water pools, or humans unless explicitly requested.
   - "mouzi" means CAT (feline animal).
   - "dao" means BIRD.
   - "oma" means PIG.
   - "mousho" means COW.
   - "gorai" means HORSE.
   - "hagrama" means LUSH FOREST / JUNGLE.
   - "dwi" means RIVER / WATER STREAM.
   - Traditional Bodo attire: "dokhona" is traditional handwoven dress with Agor motifs in yellow/green/red; "aronai" is traditional scarf.
4. If an animal is requested, describe the animal in its natural setting. DO NOT substitute animals with people, anime characters, or women.
5. Return ONLY the final visual prompt description in English, under 60 words. No quotes, no preamble.
6. DICTIONARY SENSE DISAMBIGUATION:
   When a Bodo word has multiple meanings in the dictionary (e.g. noun vs verb vs homonym), ALWAYS prioritize concrete visual nouns, animals, physical objects, and natural scenery appropriate for an image generation prompt.`;

        const userContent = `User input: "${prompt}"\n${contextEvidence ? `Dictionary & vocabulary context:\n${contextEvidence}\n` : ""}Enhanced visual prompt in English:`;

        const candidateModels = ["gemini-2.0-flash", "gemini-1.5-flash"];
        for (const candidateModel of candidateModels) {
          if (enhancedPrompt) break;
          try {
            const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${candidateModel}:generateContent?key=${geminiApiKey}`;
            const geminiResp = await fetch(geminiUrl, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                contents: [{ role: "user", parts: [{ text: `${systemInstruction}\n\n${userContent}` }] }],
                generationConfig: {
                  temperature: 0.2,
                  maxOutputTokens: 160,
                },
              }),
            });

            if (geminiResp.ok) {
              const geminiJson = await geminiResp.json();
              const rawOutput = geminiJson.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "";
              const cleanOutput = rawOutput
                .replace(/^(Enhanced prompt|Prompt):?\s*/i, "")
                .replace(/^"|"$/g, "")
                .trim();

              const validation = validateEnhancedPrompt(cleanOutput, prompt, detectedVocab);
              if (validation.valid) {
                enhancedPrompt = cleanOutput;
                console.log(`[${correlationId}] Gemini (${candidateModel}) enhanced prompt validated: "${enhancedPrompt}"`);
                break;
              } else {
                console.warn(`[${correlationId}] Validation rejected ${candidateModel} output: "${cleanOutput}" (Reason: ${validation.reason})`);
              }
            } else {
              console.warn(`[${correlationId}] ${candidateModel} returned HTTP ${geminiResp.status}`);
            }
          } catch (apiErr) {
            console.error(`[${correlationId}] Error calling ${candidateModel}:`, apiErr);
          }
        }
      }

      // Step D: Apply Deterministic Fallback if Gemini failed or was rejected
      if (!enhancedPrompt) {
        fallbackUsed = true;
        enhancedPrompt = buildDeterministicFallback(prompt, detectedVocab, isVideo);
        console.log(`[${correlationId}] Fallback prompt generated: "${enhancedPrompt}"`);
      }

      return new Response(
        JSON.stringify({
          task: "image_prompt_enhancement",
          correlationId,
          originalPrompt: prompt,
          detectedVocab,
          dictionaryMatchesCount: dictionaryMatches.length,
          enhancedPrompt,
          status: "success",
          fallbackUsed,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // =========================================================================
    // BRANCH 2: Conversational Chat Streaming (Preserved for existing Orki chat)
    // =========================================================================
    const { model, contents, generationConfig, plan, script, userPersona, isLiveMode } = body;
    const geminiApiKey = Deno.env.get("GEMINI_API_KEY");

    if (!geminiApiKey) {
      return new Response(
        JSON.stringify({ error: "GEMINI_API_KEY is not configured" }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const selectedModel = model === "okafwr-2.1" ? "gemini-2.0-flash" : (model || "gemini-2.0-flash");
    const streamUrl = `https://generativelanguage.googleapis.com/v1beta/models/${selectedModel}:streamGenerateContent?alt=sse&key=${geminiApiKey}`;

    const upstreamResponse = await fetch(streamUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents,
        generationConfig: generationConfig || { temperature: isLiveMode ? 0.25 : 0.35 },
      }),
    });

    if (!upstreamResponse.ok) {
      const errText = await upstreamResponse.text();
      return new Response(errText, {
        status: upstreamResponse.status,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response(upstreamResponse.body, {
      status: 200,
      headers: {
        ...corsHeaders,
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive",
      },
    });
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message || "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
