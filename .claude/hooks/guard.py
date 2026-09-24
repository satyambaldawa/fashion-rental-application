#!/usr/bin/env python3
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from policy import (  # noqa: E402
    secrets,
    destructive,
    gcp,
    db,
    github,
    pr_review,
    git_write,
    egress,
    privilege,
)

_CHECKERS = (
    secrets.check,
    destructive.check,
    gcp.check,
    db.check,
    github.check,
    pr_review.check,
    git_write.check,
    egress.check,
    privilege.check,
)

_HEREDOC_START_RE = re.compile(r"<<-?\s*(['\"]?)(\w+)\1")


def _strip_heredocs(text):
    """Drop heredoc bodies so prose/data they carry (a PR description, a JSON
    payload, a script written for later use) can't be mistaken for a command
    actually being invoked by any policy checker. Applied once here, before
    dispatch, so no individual checker can be tripped by heredoc contents."""
    lines = text.split("\n")
    kept = []
    i = 0
    while i < len(lines):
        line = lines[i]
        kept.append(line)
        match = _HEREDOC_START_RE.search(line)
        if match:
            delimiter = match.group(2)
            i += 1
            while i < len(lines) and lines[i].strip() != delimiter:
                i += 1
            i += 1  # also drop the delimiter line itself
            continue
        i += 1
    return "\n".join(kept)


def _extract_text(tool_input):
    if not isinstance(tool_input, dict):
        return ""
    command = tool_input.get("command")
    return _strip_heredocs(str(command)) if command else ""


def decide(tool_name, tool_input):
    text = _extract_text(tool_input)
    results = [checker(tool_name, text, tool_input or {}) for checker in _CHECKERS]
    results = [result for result in results if result is not None]

    # A chained command (`gh pr view 86 && rm ...`) can trip one checker's
    # allow-list and another's deny rule at once. Deny must win regardless of
    # which checker ran first, or an allow-listed prefix would launder a
    # dangerous suffix straight past every later policy. Ask outranks allow
    # for the same laundering reason: an uncertain/needs-a-human verdict from
    # one checker must not be overridden by another checker's allow.
    for decision, reason in results:
        if decision == "deny":
            return (decision, reason)
    for decision, reason in results:
        if decision == "ask":
            return (decision, reason)
    for decision, reason in results:
        if decision == "allow":
            return (decision, reason)

    # Nothing objected. Bash defaults to allow here rather than falling
    # through to settings.json's allow-list — an ever-growing enumeration of
    # every safe command (pnpm test, ./gradlew build, mkdir -p, ...) doesn't
    # scale, and background subagents can't answer the interactive prompt
    # that an unmatched command would otherwise trigger. The checkers above
    # are the actual gate for anything dangerous; this is a deliberate
    # default-allow-except-denied model, not an oversight. Edit/Write/
    # NotebookEdit keep deferring to settings.json's Edit/Write allow rules.
    if tool_name == "Bash":
        return (
            "allow",
            "No policy objected; Bash defaults to allow under the "
            "default-allow-except-denied model.",
        )
    return ("defer", "")


def _deny_asks_from_subagents(decision, reason, payload):
    # An "ask" is meant to put a human in the loop. A subagent has no human to
    # ask, and under auto mode Claude Code hands "ask" to a classifier that may
    # approve it — so for subagents an ask becomes a hard deny. Claude Code
    # populates agent_type only for real subagents (absent in the main session).
    if decision == "ask" and payload.get("agent_type"):
        return ("deny", f"{reason} (Subagents cannot get human approval — denied.)")
    return (decision, reason)


def main():
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw) if raw.strip() else {}
        tool_name = payload.get("tool_name", "") or ""
        tool_input = payload.get("tool_input", {}) or {}
        decision, reason = decide(tool_name, tool_input)
        decision, reason = _deny_asks_from_subagents(decision, reason, payload)
    except Exception as exc:  # noqa: BLE001 - fail closed on any unexpected error
        decision, reason = (
            "ask",
            f"guard.py could not evaluate this call ({exc}); escalating to manual approval.",
        )

    # "defer" is not a neutral "no opinion" to Claude Code: it pauses the tool
    # call for an external host, which silently ends a subagent's run. Having
    # no opinion means emitting nothing, so normal settings.json resolution runs.
    if decision == "defer":
        sys.exit(0)

    output = {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
        }
    }
    if reason:
        output["hookSpecificOutput"]["permissionDecisionReason"] = reason
    print(json.dumps(output))
    sys.exit(0)


if __name__ == "__main__":
    main()
