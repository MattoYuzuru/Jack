# AGENTS.md

## Workflow Rules

Before starting any new task:

1. Fetch the latest repository state from remote.
2. Make sure local `main` matches the latest `origin/main`.
3. Make sure the work does not start from a branch that is already ahead of `main`.
4. If the user did not explicitly ask to stay on the current branch, create a new feature branch from fresh `main`.

## Commits

1. Every important logical chunk of work must be committed separately.
2. Use conventional commits only.
3. Do not accumulate many unrelated edits into one commit.

Examples:

- `feat(viewer): add image metadata panel`
- `fix(pdf): preserve page rotation during merge`
- `docs(roadmap): update iteration 2 scope`
- `test(converter): cover heic to jpg pipeline`

## Code Comments

1. Complex or key implementation points must be commented in Russian.
2. Comments should explain why the logic exists or what non-obvious constraint is being handled.
3. Do not add noisy comments for trivial code.

## Finish Checklist

Before finishing the task:

1. Run all relevant tests.
2. Run lint and formatting checks for the touched parts.
3. Update docs and roadmap if behavior or scope changed.
4. Commit the work.
5. Push the branch to remote.
6. Prepare the change for an MR unless the user explicitly asked not to.

## Safety

1. Do not rewrite history unless the user explicitly asked for it.
2. Do not force-push without explicit permission.
3. Do not revert user changes that are unrelated to the current task.
