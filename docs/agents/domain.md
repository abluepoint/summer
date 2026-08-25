# Domain Docs

This repository uses a single domain context for engineering skills.

## Before exploring

- Read the root `CONTEXT.md` when it exists.
- Read ADRs under `docs/adr/` that touch the area being changed.
- If a domain document is absent, proceed without inventing its contents.

## Vocabulary

- Use terms as defined in `CONTEXT.md` in specifications, issues, tests and implementation notes.
- Avoid synonyms that the glossary explicitly rejects.
- Treat a missing necessary term as a possible domain-model gap rather than silently inventing competing language.

## ADR conflicts

Surface conflicts with existing ADRs explicitly. Preserve superseded ADRs as historical decisions and record the replacement decision in a new ADR.

## Layout

```text
/
├── CONTEXT.md
└── docs/adr/
```
