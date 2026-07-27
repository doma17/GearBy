# Contributing to GearBy

Contributions are welcome. Before coding, find or open an issue and agree on its scope with a maintainer.

## Issue and branch

- AI-assisted pull requests must reference an **accepted issue**. Discuss unaccepted ideas first.
- Create one focused branch per issue: `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `docs/<issue>-<slug>`, or `chore/<issue>-<slug>`.
- Keep each pull request focused; do not include unrelated cleanup or refactors.

## Commit convention

- Write a short imperative subject that explains the intent, for example: `Show nearby stores on the map`.
- Use the body for relevant constraints, trade-offs, and verification results.
- When AI assisted the work, add one trailer per tool:

  ```text
  Assisted-by: Codex:<model-version>
  ```

## Pull request checklist

- Link the accepted issue and summarize the user-visible change.
- State what you tested and the result; do not submit unverified changes.
- Keep the diff reviewable and update relevant documentation.
- Disclose the AI tools used and the extent of their assistance in the PR description.
- Have a human review and, where applicable, manually verify the result before submission.

## AI use

All AI-assisted work must follow the project's [AI policy](.github/AI_POLICY.md).

## Reference

This guide follows the contribution and AI-policy structure used by [RustPython](https://github.com/RustPython/RustPython/blob/main/CONTRIBUTING.md), adapted to GearBy's current workflow.
