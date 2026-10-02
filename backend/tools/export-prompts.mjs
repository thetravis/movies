#!/usr/bin/env node
/**
 * Exports the prompts given to Claude Code in this project into a Markdown file, to document how
 * the code was produced with AI.
 *
 * Claude Code stores every session as a JSONL file under
 *   ~/.claude/projects/<project path with non-alphanumerics replaced by "-">/
 * (for C:\_work\movies that is ~/.claude/projects/C---work-movies). Claude Code deletes these files
 * after 30 days by default (setting "cleanupPeriodDays"), so run this script before then.
 *
 * For each prompt the output contains:
 *   - the prompt text and its local time,
 *   - answers given to Claude's multiple-choice questions during that step,
 *   - the first paragraph of Claude's final answer for that step (a short summary),
 *   - the files Claude created or edited with its file tools in that step.
 *
 * Usage (from the repository root, Node.js 18+, no dependencies):
 *   node backend/tools/export-prompts.mjs [--dir <sessions dir>] [--out <markdown file>]
 *                                         [--since <date/time>] [--until <date/time>]
 *
 * Defaults: --dir is derived from the repository path, --out is backend/documentation/ai-prompts.md.
 * --since / --until limit the export to prompts sent at or after / before an ISO date or date-time
 * (e.g. 2026-10-02T19:49:00Z), so separate parts of the work get separate logs:
 *   backend:  node backend/tools/export-prompts.mjs --until 2026-10-02T19:49:00Z
 *   frontend: node backend/tools/export-prompts.mjs --since 2026-10-02T19:49:00Z --out frontend/documentation/ai-prompts.md
 * Review the output before committing it: prompts are included as written.
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SUMMARY_MAX_LENGTH = 500;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..', '..');

const args = parseArgs(process.argv.slice(2));
const sessionsDir = args.dir ?? path.join(os.homedir(), '.claude', 'projects', repoRoot.replace(/[^a-zA-Z0-9]/g, '-'));
const outFile = path.resolve(args.out ?? path.join(repoRoot, 'backend', 'documentation', 'ai-prompts.md'));
const since = args.since ? parseTime(args.since, '--since') : null;
const until = args.until ? parseTime(args.until, '--until') : null;

if (!fs.existsSync(sessionsDir)) {
    console.error(`Sessions directory not found: ${sessionsDir}\nUse --dir to point to it.`);
    process.exit(1);
}

const sessions = fs.readdirSync(sessionsDir)
    .filter(name => name.endsWith('.jsonl'))
    .map(name => readSession(path.join(sessionsDir, name)))
    .map(session => withinRange(session, since, until))
    .filter(session => session.turns.length > 0)
    .sort((a, b) => a.start.localeCompare(b.start));

fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, toMarkdown(sessions));

const promptCount = sessions.reduce((sum, s) => sum + s.turns.length, 0);
console.log(`Wrote ${promptCount} prompts from ${sessions.length} session(s) to ${outFile}`);

// ---------------------------------------------------------------------------------------------

function parseArgs(argv) {
    const result = {};
    for (let i = 0; i < argv.length; i++) {
        const [key, value] = [argv[i], argv[i + 1]];
        if (['--dir', '--out', '--since', '--until'].includes(key)) {
            if (!value) {
                console.error(`Missing value for ${key}`);
                process.exit(1);
            }
            result[key.slice(2)] = value;
            i++;
        } else {
            console.error(`Unknown argument: ${key}\nUsage: node export-prompts.mjs [--dir <sessions dir>] [--out <file>] [--since <date/time>] [--until <date/time>]`);
            process.exit(1);
        }
    }
    return result;
}

/** Parses an ISO date or date-time given on the command line. */
function parseTime(value, option) {
    const time = new Date(value);
    if (Number.isNaN(time.getTime())) {
        console.error(`Invalid date for ${option}: ${value} (use e.g. 2026-10-02 or 2026-10-02T19:49:00Z)`);
        process.exit(1);
    }
    return time;
}

/** Keeps the turns whose prompt was sent at or after `since` and before `until` (both optional). */
function withinRange(session, since, until) {
    const turns = session.turns.filter(turn => {
        const time = new Date(turn.time);
        return (!since || time >= since) && (!until || time < until);
    });
    return { ...session, turns, start: turns[0]?.time ?? '' };
}

/** Reads one session file and groups its entries into turns, one per user prompt. */
function readSession(file) {
    const entries = fs.readFileSync(file, 'utf8')
        .split('\n')
        .filter(line => line.trim())
        .map(line => {
            try {
                return JSON.parse(line);
            } catch {
                return null; // a partially written last line while the session is running
            }
        })
        .filter(Boolean);

    const turns = [];
    const models = new Set();
    let current = null;

    for (const entry of entries) {
        if (entry.isSidechain) continue; // sub-agent conversations

        if (entry.type === 'user') {
            const prompt = promptText(entry);
            if (prompt !== null) {
                if (current && current.prompt === prompt) {
                    current.resent = true; // the same prompt sent again
                    continue;
                }
                current = { time: entry.timestamp, prompt, answers: [], summary: '', files: new Set() };
                turns.push(current);
                continue;
            }
            if (current && entry.toolUseResult?.answers) {
                for (const [question, answer] of Object.entries(entry.toolUseResult.answers)) {
                    current.answers.push({ question, answer });
                }
            }
        }

        if (entry.type === 'assistant' && current) {
            if (entry.message?.model && !entry.message.model.startsWith('<')) models.add(entry.message.model);
            for (const block of entry.message?.content ?? []) {
                if (block.type === 'text' && block.text.trim()) {
                    current.summary = block.text; // keep the last text: Claude's final answer
                }
                if (block.type === 'tool_use' && ['Write', 'Edit', 'NotebookEdit'].includes(block.name)) {
                    const filePath = block.input?.file_path ?? block.input?.notebook_path;
                    const relative = filePath && toRepoPath(filePath);
                    if (relative) current.files.add(relative);
                }
            }
        }
    }

    return {
        id: path.basename(file, '.jsonl'),
        start: turns[0]?.time ?? '',
        models: [...models],
        turns,
    };
}

/** Returns the text the user typed, or null if the entry is not a prompt (tool results etc.). */
function promptText(entry) {
    if (entry.isMeta) return null;
    const content = entry.message?.content;
    let text = null;
    if (typeof content === 'string') {
        text = content;
    } else if (Array.isArray(content) && content.every(block => block.type === 'text')) {
        text = content.map(block => block.text).join('\n');
    }
    if (!text) return null;

    const command = text.match(/<command-name>(.*?)<\/command-name>/s);
    if (command) {
        const commandArgs = text.match(/<command-args>(.*?)<\/command-args>/s)?.[1]?.trim();
        return `${command[1].trim()}${commandArgs ? ' ' + commandArgs : ''}`;
    }
    if (text.startsWith('<') || text.startsWith('[Request interrupted')) return null;
    return text.trim();
}

/** Repository-relative path with forward slashes, or null for files outside the repository. */
function toRepoPath(filePath) {
    const relative = path.relative(repoRoot, path.resolve(filePath));
    if (relative.startsWith('..') || path.isAbsolute(relative)) return null;
    return relative.split(path.sep).join('/');
}

function toMarkdown(sessions) {
    // Link to this script, relative to wherever the log is written.
    const scriptLink = path.relative(path.dirname(outFile), fileURLToPath(import.meta.url)).split(path.sep).join('/');
    const range = [since && `from ${formatTime(since.toISOString())}`, until && `until ${formatTime(until.toISOString())}`]
        .filter(Boolean)
        .join(' ');
    const lines = [
        '# AI prompts log',
        '',
        `${range ? `Prompts given to Claude Code ${range}` : 'All prompts given to Claude Code while building this project'}, in order.`,
        `Generated by [\`export-prompts.mjs\`](${scriptLink}) from the Claude Code session transcripts on`,
        `${formatTime(new Date().toISOString())}.`,
        '',
        'For each prompt: the prompt as written, answers to Claude\'s multiple-choice questions, the',
        'first paragraph of Claude\'s final answer, and the files Claude created or edited with its file',
        'tools. Files changed through shell commands (e.g. `sed`) are not listed; see the git history',
        'for the complete changes.',
        '',
    ];

    let number = 0;
    sessions.forEach((session, index) => {
        lines.push(`## Session ${index + 1} – ${formatTime(session.start).slice(0, 10)}`, '');
        lines.push(`Session id \`${session.id}\`${session.models.length ? `, model: ${session.models.map(m => `\`${m}\``).join(', ')}` : ''}.`, '');

        for (const turn of session.turns) {
            number++;
            lines.push(`### ${number}. ${formatTime(turn.time)}${turn.resent ? ' (sent twice)' : ''}`, '');
            lines.push(...turn.prompt.split('\n').map(line => (line ? `> ${line}` : '>')), '');

            if (turn.answers.length) {
                lines.push('**Answers to Claude\'s questions:**', '');
                for (const { question, answer } of turn.answers) {
                    lines.push(`- ${question} → **${answer}**`);
                }
                lines.push('');
            }
            const summary = firstParagraph(turn.summary);
            if (summary) {
                lines.push(`**Result:** ${summary}`, '');
            }
            if (turn.files.size) {
                lines.push(`**Files:** ${[...turn.files].sort().map(f => `\`${f}\``).join(', ')}`, '');
            }
        }
    });

    return lines.join('\n');
}

function firstParagraph(text) {
    const paragraph = (text ?? '').trim().split(/\n\s*\n/)[0].replace(/\s*\n\s*/g, ' ').trim();
    return paragraph.length > SUMMARY_MAX_LENGTH
        ? paragraph.slice(0, SUMMARY_MAX_LENGTH).replace(/\s+\S*$/, '') + ' …'
        : paragraph;
}

/** ISO timestamp → "YYYY-MM-DD HH:mm" in the local time zone. */
function formatTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
