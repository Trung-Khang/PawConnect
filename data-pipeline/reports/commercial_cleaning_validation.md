# Commercial Cleaning Validation

- Contract scope: commercial observation v0.2; raw data remains unchanged.
- Candidate datasets contain no source URL, listing title, store name, description, image, phone, email, address, or chat marker.
- Chợ Tốt individual listings are excluded from the puppy candidate file; only aggregate price bands are eligible for handoff.
- Raw Chợ Tốt age values have no source phrase evidence, so every populated value is cleared and represented as `life_stage=UNKNOWN` for aggregation.
- Pet Mart category_code remains blank because raw input has no confirmed subcategory mapping.

| Source | Raw | Accepted | Quarantined | Deduped | Age unverified | Unmapped breed | Insufficient sample | Candidate | Band contribution |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Chợ Tốt | 38 | 38 | 0 | 0 | 27 | 20 | 3 | 0 | 15 |
| Chợ Tốt (cho-giong) | 54 | 50 | 0 | 4 | 37 | 46 | 0 | 0 | 4 |
| Pet Mart | 96 | 73 | 23 | 0 | 0 | 0 | 0 | 73 | 0 |

## Output counts

- puppy_market_observations.csv: 0 rows (required to remain empty for Chợ Tốt listing privacy).
- product_catalog_observations.csv: 73 rows.
- market_price_bands.csv: 3 rows; bands require sample_count >= 3.
- Result: PASS.
