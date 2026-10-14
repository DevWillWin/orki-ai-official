-- ==============================================================================
-- ORKI AI: Bodo Dictionary Search RPC & Performance Indices
-- Migration: 20261013_search_bodo_dictionary.sql
-- Table: public."DICTIONARY" (~31,000 rows)
-- ==============================================================================

-- 1. Ensure pg_trgm extension is available for fast fuzzy & partial matching
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

-- 2. GIN and B-Tree Indexes for sub-millisecond lookups on 31,000 rows
CREATE INDEX IF NOT EXISTS idx_dictionary_headword_lower 
ON public."DICTIONARY" (LOWER(TRIM(headword)));

CREATE INDEX IF NOT EXISTS idx_dictionary_headword_trgm 
ON public."DICTIONARY" USING gin (headword gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_dictionary_romanized_forms_gin 
ON public."DICTIONARY" USING gin (romanized_forms jsonb_path_ops);

-- 3. High-Performance RPC function for Bodo dictionary search
CREATE OR REPLACE FUNCTION public.search_bodo_dictionary(
    search_term text,
    max_results integer DEFAULT 5
)
RETURNS TABLE (
    id text,
    headword text,
    bodo_translations jsonb,
    english_meanings jsonb,
    category text,
    romanized_forms jsonb,
    relevance_score float
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    clean_term text;
    term_pattern text;
BEGIN
    clean_term := LOWER(TRIM(COALESCE(search_term, '')));
    IF clean_term = '' THEN
        RETURN;
    END IF;

    term_pattern := '%' || clean_term || '%';

    RETURN QUERY
    WITH scored_matches AS (
        SELECT 
            d.id,
            d.headword,
            d.bodo_translations,
            d.english_meanings,
            d.category,
            d.romanized_forms,
            CASE
                -- Exact match on headword or romanized form gets highest score
                WHEN LOWER(d.headword) = clean_term THEN 100.0
                WHEN d.romanized_forms @> to_jsonb(clean_term) THEN 95.0
                -- Prefix match
                WHEN LOWER(d.headword) LIKE clean_term || '%' THEN 80.0
                -- Exists in romanized forms array case-insensitively
                WHEN EXISTS (
                    SELECT 1 
                    FROM jsonb_array_elements_text(COALESCE(d.romanized_forms, '[]'::jsonb)) AS elem
                    WHERE LOWER(elem) = clean_term
                ) THEN 85.0
                -- Substring match in romanized forms
                WHEN EXISTS (
                    SELECT 1 
                    FROM jsonb_array_elements_text(COALESCE(d.romanized_forms, '[]'::jsonb)) AS elem
                    WHERE LOWER(elem) LIKE clean_term || '%'
                ) THEN 60.0
                -- Fuzzy match on headword
                ELSE similarity(LOWER(d.headword), clean_term) * 50.0
            END AS relevance_score
        FROM public."DICTIONARY" d
        WHERE 
            LOWER(d.headword) = clean_term
            OR LOWER(d.headword) LIKE term_pattern
            OR d.romanized_forms @> to_jsonb(clean_term)
            OR EXISTS (
                SELECT 1 
                FROM jsonb_array_elements_text(COALESCE(d.romanized_forms, '[]'::jsonb)) AS elem
                WHERE LOWER(elem) LIKE term_pattern
            )
            OR similarity(LOWER(d.headword), clean_term) > 0.35
    )
    SELECT 
        s.id,
        s.headword,
        s.bodo_translations,
        s.english_meanings,
        s.category,
        s.romanized_forms,
        s.relevance_score
    FROM scored_matches s
    ORDER BY s.relevance_score DESC, LENGTH(s.headword) ASC
    LIMIT LEAST(max_results, 20);
END;
$$;

-- Grant execution permissions to anon and authenticated roles
GRANT EXECUTE ON FUNCTION public.search_bodo_dictionary(text, integer) TO anon, authenticated, service_role;
