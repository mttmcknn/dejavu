# Agent skills

DejaVu's four skills use the open [Agent Skills format](https://agentskills.io/specification).
Each is a portable folder containing `SKILL.md` and any required references.
They teach an agent how to use DejaVu; your project still needs its normal Gradle,
Android SDK or multiplatform test environment to run the resulting tests.

| Skill | Use it for |
|---|---|
| `dejavu-onboarding` | Install DejaVu and prove tracking in a first test |
| `dejavu-test-writer` | Add recomposition assertions to an existing Android or KMP test setup |
| `dejavu-error-triage` | Diagnose failures using source evidence |
| `dejavu-perf-loop` | Reduce unnecessary application recompositions within an agreed budget |

The skills preserve deliberately inefficient accuracy fixtures and distinguish
diagnosis from authorized optimization.

## Install with the Skills CLI

From your application's repository, with Node.js/npm installed:

```bash
npx skills add mttmcknn/dejavu
```

Choose the skills and agents interactively. The [Skills CLI](https://github.com/vercel-labs/skills)
supports Codex, Claude Code, Cursor, GitHub Copilot, OpenCode and other agents.
To select agents explicitly:

```bash
npx skills add mttmcknn/dejavu --skill '*' --agent codex cursor
```

Use the agent identifiers `claude-code`, `github-copilot` or `opencode` for those
clients. Add `--global` to install across projects, or `--copy` when symlinks are
unavailable. Install only one skill with `--skill dejavu-test-writer`.

To preview available skills without installing:

```bash
npx skills add mttmcknn/dejavu --list
```

To test unpublished changes from a local checkout, replace `mttmcknn/dejavu` with
the absolute path to that checkout. GitHub installation uses the repository's
default branch; an unmerged PR is not part of that installation.

## Claude Code marketplace

The existing plugin remains supported. Run these commands inside Claude Code:

```text
/plugin marketplace add mttmcknn/dejavu
/plugin install dejavu@dejavu
```

Use either the plugin or standalone skills for a given agent, to avoid duplicates.
The plugin reads the same `skills/` folders; no Claude-specific instruction fork
is maintained.

## Manual installation and other clients

Download or clone the [repository](https://github.com/mttmcknn/dejavu) and copy the
complete desired folders from `skills/` into your client's documented skill
directory. Include `references/`, not just `SKILL.md`. The canonical folders contain
real files, so copying them does not depend on symlinks or a retained DejaVu checkout.

For example, from the DejaVu checkout, copy the four folders into a fresh Codex
project skill directory (replace the destination with your app's path):

```bash
mkdir -p /path/to/your-app/.agents/skills
cp -R skills/dejavu-* /path/to/your-app/.agents/skills/
```

[Codex discovers project skills in `.agents/skills/`](https://learn.chatgpt.com/docs/build-skills).
Other clients may use different paths or import commands; follow their current
documentation. If skill folders with these names already exist, replace the old
folders deliberately rather than merging stale references into a newer bundle.
For agents without native skill support, explicitly ask the agent to read the
chosen `SKILL.md` and its referenced files; automatic discovery is unavailable there.

## Verify and update

For CLI installs, run `npx skills list` (add `--global` for global installs).
Reload the agent's skill catalog or start a new session, then check that the four
names appear. Ask it to use `dejavu-test-writer` on a small existing test. Successful
installation establishes discovery; run the actual test to verify its output.

Use `npx skills update` for CLI-managed installs, the plugin updater for marketplace
installs, or replace manually copied folders from the chosen source revision.
For reproducible manual installs, check out a reviewed commit before copying and
record that revision. Skill bundle 0.3.0 is independent of DejaVu library versions;
follow each skill's compatibility guidance for the library you use.

## Maintaining the shared source

Canonical files live in `skills/`. In the DejaVu checkout, `.agents/skills/` and
`.claude/skills/` contain discovery links to those files. On checkouts without
working symlinks, use the CLI's copy mode in your consumer project or manual copying.

Packaging checks verify complete bundles and discovery links. The
[evaluation guide](https://github.com/mttmcknn/dejavu/blob/main/evals/README.md)
describes behavioral comparisons. Its current execution adapter is Codex; portable
distribution does not imply that live evaluations have run on every supported client.
