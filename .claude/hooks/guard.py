#!/usr/bin/env python3
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from policy import secrets, destructive, gcp, db, github, pr_review  # noqa: E402

_CHECKERS = (secrets.check, destructive.check, gcp.check, db.check, github.check, pr_review.check)


def _extract_text(tool_input):
    if not isinstance(tool_input, dict):
        return ""
    command = tool_input.get("command")
    return str(command) if command else ""


def decide(tool_name, tool_input):
    text = _extract_text(tool_input)
    results = [checker(tool_name, text, tool_input or {}) for checker in _CHECKERS]
    results = [result for result in results if result is not None]

    # A chained command (`gh pr view 86 && rm ...`) can trip one checker's
    # allow-list and another's deny rule at once. Deny must win regardless of
    # which checker ran first, or an allow-listed prefix would launder a
    # dangerous suffix straight past every later policy.
    for decision, reason in results:
        if decision == "deny":
            return (decision, reason)
    for decision, reason in results:
        if decision == "allow":
            return (decision, reason)
    return ("defer", "")


def main():
    try:
        raw = sys.stdin.read()
        payload = json.loads(raw) if raw.strip() else {}
        tool_name = payload.get("tool_name", "") or ""
        tool_input = payload.get("tool_input", {}) or {}
        decision, reason = decide(tool_name, tool_input)
    except Exception as exc:  # noqa: BLE001 - fail closed on any unexpected error
        decision, reason = (
            "ask",
            f"guard.py could not evaluate this call ({exc}); escalating to manual approval.",
        )

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
