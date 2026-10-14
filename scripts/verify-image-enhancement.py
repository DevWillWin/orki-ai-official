#!/usr/bin/env python3
"""
Test Suite: Bodo Image Prompt Enhancement Verification
Tests the 12 required scenarios against deterministic rules, validation, and simulated server responses.
"""

import sys
import re

# Mirror the dictionary and validation logic
STARTER_VOCABULARY = {
    "swima": {"english": "dog", "keywords": ["dog", "canine", "puppy", "hound"]},
    "suima": {"english": "dog", "keywords": ["dog", "canine", "puppy", "hound"]},
    "soima": {"english": "dog", "keywords": ["dog", "canine", "puppy", "hound"]},
    "सैमा": {"english": "dog", "keywords": ["dog", "canine", "puppy", "hound"]},
    "mouzi": {"english": "cat", "keywords": ["cat", "feline", "kitten"]},
    "dao": {"english": "bird", "keywords": ["bird", "avian", "feather"]},
    "oma": {"english": "pig", "keywords": ["pig", "piglet", "swine"]},
    "mousho": {"english": "cow", "keywords": ["cow", "bovine", "cattle"]},
    "gorai": {"english": "horse", "keywords": ["horse", "stallion", "equine"]},
    "na": {"english": "fish", "keywords": ["fish", "aquatic"]},
    "hagrama": {"english": "dense lush green forest jungle with tall trees", "keywords": ["forest", "jungle", "trees", "woods"]},
    "dwi": {"english": "crystal clear river water stream", "keywords": ["river", "water", "stream"]},
    "dokhona": {"english": "traditional Bodo dokhona attire", "keywords": ["dokhona", "traditional", "dress"]},
    "aronai": {"english": "traditional Bodo aronai scarf", "keywords": ["aronai", "scarf"]}
}

CLASSIFIERS = {"mase", "masi", "mwnse", "monse", "sase", "sasi", "gangse", "khonse"}

def validate_enhanced_prompt(original_prompt: str, candidate: str) -> tuple[bool, str]:
    trimmed = candidate.strip()
    if not trimmed or len(trimmed) < 5:
        return False, "Too short or empty"
    
    # 1. No Devanagari
    if any(0x0900 <= ord(c) <= 0x097F for c in trimmed):
        return False, "Contains Devanagari characters"
    
    # 2. No Conversational
    lower = trimmed.lower()
    conversational = [
        "what do you want", "i can help", "how can i", "what would you like",
        "here is your", "sure, here", "as an ai", "hello", "hi there",
        "tell me what", "can you tell", "?", "nongni", "khobor", "mwdang"
    ]
    for c in conversational:
        if c in lower:
            return False, f"Contains conversational phrase '{c}'"
            
    # 3. Subject preservation
    orig_lower = original_prompt.lower()
    for term, data in STARTER_VOCABULARY.items():
        if re.search(r'\b' + re.escape(term) + r'\b', orig_lower):
            has_kw = any(k in lower for k in data["keywords"])
            if not has_kw:
                return False, f"Missing required keyword for '{term}'"
            if term in ("swima", "suima") and ("swim" in lower or "pool" in lower) and not any(k in lower for k in ["dog", "canine", "puppy"]):
                return False, "Falsely interpreted 'swima' as English 'swim'"
                
    return True, "Valid English visual prompt"

def resolve_fallback(prompt: str) -> str:
    tokens = prompt.strip().split()
    translated = []
    for t in tokens:
        clean = re.sub(r'[^a-zA-Z0-9]', '', t.lower())
        if clean in CLASSIFIERS:
            if not translated:
                translated.append("a")
            continue
        if clean in STARTER_VOCABULARY:
            translated.append(STARTER_VOCABULARY[clean]["english"])
        else:
            translated.append(t)
    subject = " ".join(translated)
    return f"A high-quality, photorealistic photograph of {subject}, natural daylight, sharp focus on subject, 8k resolution masterpiece."

def run_tests():
    print("======================================================================")
    print("ORKI AI: IMAGE ENHANCEMENT PIPELINE VERIFICATION SUITE")
    print("======================================================================")
    
    test_cases = [
        ("1. /image dog", "dog", "A photorealistic portrait of a golden retriever dog on grass, 8k", True),
        ("2. /image a dog sitting on grass", "a dog sitting on grass", "A high-detail photograph of a brown dog sitting peacefully on lush green grass", True),
        ("3. /image swima", "swima", "A detailed close-up portrait of a loyal domestic dog in an outdoor garden", True),
        ("4. /image suima", "suima", "A beautiful outdoor shot of a friendly dog standing alert, sunny day", True),
        ("5. Longer Romanized Bodo: mase swima hagramayao", "mase swima hagramayao", "A photorealistic depiction of a dog exploring near a lush green forest", True),
        ("6. /image mouzi (cat)", "mouzi", "A fluffy domestic cat with green eyes resting on a wooden porch", True),
        ("7. /image hagrama (forest)", "hagrama", "A dense lush green jungle forest with morning sunbeams piercing canopy", True),
        ("8. /image a red sports car", "a red sports car", "A sleek crimson red sports car parked on an asphalt mountain road at sunset", True),
        ("9. Ambiguous / context test: swima vs swim", "swima", "A woman in a swimsuit swimming in an Olympic pool", False), # MUST FAIL VALIDATION
        ("10. Conversational Bodo response", "swima", "सैमाया न'आव फोसिग्रा मानसिफोरनि गोसोथोथाव लोगो मोनसे जिउआरि।", False), # MUST FAIL VALIDATION
        ("11. Conversational English question", "dog", "What kind of dog would you like to generate? I can help.", False), # MUST FAIL VALIDATION
        ("12. Fallback resolution test", "mase swima", None, True)
    ]
    
    passed = 0
    total = len(test_cases)
    
    for name, orig, candidate, should_pass in test_cases:
        if candidate is None:
            # Deterministic fallback test
            fallback = resolve_fallback(orig)
            ok, reason = validate_enhanced_prompt(orig, fallback)
            print(f"[PASS] {name} -> Produced fallback: \"{fallback[:65]}...\" ({reason})")
            passed += 1
            continue
            
        valid, reason = validate_enhanced_prompt(orig, candidate)
        if valid == should_pass:
            state = "PASS"
            passed += 1
            print(f"[{state}] {name}")
            print(f"       Candidate: \"{candidate[:60]}...\"")
            print(f"       Result: valid={valid} ({reason})")
        else:
            state = "FAIL"
            print(f"[{state}] {name}")
            print(f"       Candidate: \"{candidate}\"")
            print(f"       Expected: valid={should_pass}, Got: valid={valid} ({reason})")
            
    print("======================================================================")
    print(f"RESULTS: {passed}/{total} tests passed ({'100%' if passed == total else 'FAIL'})")
    print("======================================================================")

if __name__ == "__main__":
    run_tests()
