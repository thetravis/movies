# CLAUDE.md

Project instructions are kept tool-neutral in AGENTS.md, so that other AI agents can use them too:

@AGENTS.md

## Claude Code specifics

- Prompts and answers of each session are stored by Claude Code under
  `~/.claude/projects/<project>/` and deleted after 30 days. `tools/export-prompts.mjs` turns them
  into `documentation/ai-prompts.md`; re-run it after a working session.
- Work phase by phase as in `documentation/plan.md`; tick off items there when done.
- Ask before decisions that change the design or the API contract; propose a recommendation.
