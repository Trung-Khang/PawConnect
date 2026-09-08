# PawConnect - Controlled Breed Crawl Validation

- run_id: `20260908T121000Z`
- Batch disposition: `20260908T120000Z` was rejected at the parser quality gate because its smoke output contained CSS and a page-status sentence; handoff batch only is `20260908T121000Z` with fetched `3`, accepted `3`.
- source: `Wikipedia English`
- fetched: `3`
- accepted: `3`
- skipped: `0`
- duplicates: `0`
- curated_breeds_unchanged: `True`

## Controls

| Check | Result |
| --- | --- |
| Exact article allowlist | PASS |
| Robots policy snapshot approved | PASS |
| Same-domain redirect guard | PASS |
| text/html and 2 MB response limit | PASS |
| 2-second per-domain rate limit | PASS |
| No HTML body, image URL, PII/contact, account, token or API key in raw records | PASS |
| Curated breeds.csv unchanged | PASS |

## Output

- records.jsonl: `3` records
- request-log.jsonl: `3` entries
- Raw descriptions are capped at 300 characters. No cleaner, dedup pipeline, candidate/curated merge or DB import was executed.

## Conclusion: PASS
