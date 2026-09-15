---
name: task-summary
description: Write a short task-completion summary under Documentation/tasks/ right after finishing a GitHub issue/ticket in this repo (Restaurant-Orders-Management). Use this skill proactively whenever a ticket's implementation and verification are done and about to be handed off or committed — don't wait for the user to ask for documentation. Triggers on cues like "that ticket is done", "issue #N is implemented", "ready to commit this", or immediately after a Verify/test step passes for a tracked issue.
---

# Task summary

Capture what was actually done on a ticket while it's fresh, in the same terse,
concrete style used for `Documentation/tasks/02-postgresql-integration-and-base-project-configuration.md`
(read that file for the exact tone and level of detail — it's the reference example).

## When to write one

Right after a ticket's changes are implemented **and verified** (tests run, app
booted, whatever "done" means for that ticket) — before or alongside committing.
Don't write it speculatively before verification happened; the summary should
describe what was actually confirmed, not what was intended.

## Where it goes

`Documentation/tasks/<issue-number>-<kebab-case-issue-title>.md`

Zero-pad the issue number to two digits to match the existing example
(`02-postgresql-integration-and-base-project-configuration.md`). Derive the
kebab-case title from the GitHub issue title, shortened if needed to stay
readable.

## Structure

Use exactly these sections, in this order. Skip a section entirely if it has
nothing real to say for this ticket — don't pad it out.

```markdown
# Issue #<N>: <Title>

## What was done

One short paragraph. What changed and why it needed to change — plain
language, no jargon dump. If there was no meaningful "before" state to
contrast against, just state what now exists and why it matters.

## Why each dependency

| Dependency | Purpose |
|---|---|
| `some-dependency` | One sentence: what it actually does *for this ticket*, not a generic description copied from its docs. Call out anything surprising (e.g. "this replaced X because Y"). |

Only include this section if the ticket added or changed dependencies. Skip
it for tickets that are pure application code.

## The other files

- **`path/to/file`** — one sentence on its purpose. Bullet per new or
  meaningfully-changed file; skip trivial/mechanical changes (formatting,
  a one-line import fix) that don't need explaining.

## Verification performed

1. Numbered list of what was *actually run or checked* — commands executed,
   what they confirmed. Not "should work" — what you observed working.
2. ...

If real bugs were hit and fixed along the way, add a sub-list phrased as
root cause → fix, e.g.:
- A hardcoded host port broke X — fixed by doing Y instead.
```

## Writing it

Keep it as tight as the example file — a competent teammate should be able to
read it in under a minute and know what happened, why, and how it was proven
to work. Prefer concrete facts ("Flyway creates `flyway_schema_history` and
applies `V1__init.sql`") over vague claims ("the database integration works
correctly").

## If a related fix lands shortly after

Sometimes a small follow-up fix for the same ticket surfaces after the
summary is already written (a deprecation warning, a review comment, a bug
noticed on a second look) — this happened on issue #2, where a Testcontainers
API deprecation was fixed and folded back into the existing file rather than
spawning a new one. In that case, append a short bullet to the relevant
section (usually the bug sub-list under "Verification performed") instead of
creating a new task file or rewriting the whole thing — the summary should
read as one continuous, growing record of the ticket, not a new document per
fix.
