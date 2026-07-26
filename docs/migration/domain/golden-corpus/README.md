# Golden Corpus (Task 3.4)

Sanitized, synthetic datasets exercising every critical domain rule, each paired with the
**expected output of the legacy TypeScript implementation**. These are the parity oracles:
the Spring port of each algorithm/service must produce identical output for each dataset.

| File | Covers |
|------|--------|
| `corpus-graph-generation.json` | Multiple parents (incl. adoption as second parent pair), spouse groups/components, deepest-constraint generation, legacy reciprocal rows, ancestry path & subgraph |
| `corpus-leap-day-events.json` | Annual recurrence, Feb-29 → Feb-28 convention, window filtering, sort order |
| `corpus-vietnamese-search.json` | NFD/`đ→d` normalization, scoring weights, prefix bonus, suggest mode, filters, duplicate detection normalization |
| `corpus-corrupt-import.json` | Import validation: unknown fields (strict), broken references, invalid dates, cross-tree records — expected `ImportIssue[]` |

Expected outputs were derived from the legacy sources referenced in
[`../domain-semantics.md`](../domain-semantics.md)
(`src/lib/algorithms/generation.ts`, `ancestry.ts`, `src/lib/services/event-service.ts`,
`search-service.ts`, `member-service.ts`, `import-service.ts`). Contract-level (HTTP)
fixtures live separately in
[`../../contract/golden-fixtures/golden-fixtures.json`](../../contract/golden-fixtures/golden-fixtures.json).
