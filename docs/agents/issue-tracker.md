# Issue tracker: GitHub

Issues and PRDs for this repository live as GitHub issues in `abluepoint/summer`. Use the `gh` CLI for issue operations.

## Conventions

- Create issues with `gh issue create`.
- Read issues with `gh issue view <number> --comments`, including labels and comments when the workflow needs them.
- List issues with `gh issue list`, scoped by the required state and labels.
- Comment with `gh issue comment`.
- Apply or remove labels with `gh issue edit`.
- Close completed or rejected work with `gh issue close` and an explanatory comment when appropriate.
- Infer the repository from the verified GitHub remote when commands run inside this clone.

## Pull requests as a triage surface

PRs as a request surface: no.

GitHub shares one number space across issues and pull requests. Resolve an ambiguous reference before mutating it.

## Skill vocabulary

- When a skill says "publish to the issue tracker", create a GitHub issue.
- When a skill says "fetch the relevant ticket", read the GitHub issue and its comments.

## Wayfinding

- A map is a GitHub issue labelled `wayfinder:map`.
- Child tickets should use GitHub sub-issues when available, with task-list fallback when unavailable.
- Blocking relationships should use GitHub native issue dependencies when available, with a `Blocked by:` fallback.
- Claim work by assigning the issue only after its blockers are resolved.
