# Contributing — Cross-Repo Docs Discipline (read this if you are an LLM agent)

## Why this exists
`esper-android` (Kotlin client) and `022-esper` (web + Rust backend) evolve
together but ship separately. Feeding two full `codebase.txt` dumps to an LLM
on every change is wasteful, so each repo carries a **compressed mirror** of
the other. Uploading one repo's `codebase.txt` then gives the agent both sides.

## The mirrors (keep them in sync — this is mandatory, not optional)
| This repo (`esper-android`) holds | The other repo (`022-esper`) holds |
|---|---|
| `docs/esper-main/overview.md` — compressed web+backend context | `docs/esper-android/overview.md` — compressed Android context |

## Rule
Any change that alters something the mirror describes **must update the mirror
in the same change**. This includes (non-exhaustive): REST endpoints, DTO/JSON
field names, auth flows (Bearer/guest-token), sync semantics (PUT vs PATCH,
conflict rules), DB schema/tables, visibility/security model, build or deploy
commands, package/application IDs, and Room/database versions.

Trivial internal refactors that change none of the above do not require a
mirror touch.

## Mirror budget
- Keep each `overview.md` under ~200 lines: dense bullets, `file:line` refs,
  no prose essays, no duplicated code.
- Record the verification date in the `Last verified:` header whenever you
  touch the file.
- One folder per mirrored repo (`docs/<repo>/`) so mirrors can split later
  without restructuring.

## codebase.txt regeneration
Both repos use [Textify](https://github.com/JohnEsleyer/textify) (`textify.yaml`).
`docs/**/*.md` is included in the bundle, so mirrors ship with context
automatically. After doc/code changes:

```bash
textify start   # regenerates codebase.txt (commit it)
```

## Checklist before finishing any task
1. Did I change API/DTO/auth/sync/schema/build behavior? → update the mirror.
2. Did I bump the mirror's `Last verified:` date?
3. Did I run `textify start` so `codebase.txt` includes the update?
