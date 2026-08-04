#!/usr/bin/env python3

"""Rewrite the HEAD commit message to satisfy the project commit message policy.

Dependabot generates its own commit messages and offers no configuration for
subject length. Most of them already comply, but two cases do not:

  * Long subjects, such as
    "Bump @douyinfe/semi-illustrations from 2.99.3 to 2.100.0 in /webapp" (68).
  * Long description lines, such as the "Bumps <artifact> from A to B." line
    that Dependabot writes without a Markdown link when it cannot resolve a
    repository URL for the package.

Both fail the check-commit-messages job in ci.yml, which blocks the pull
request. This script amends HEAD so those commits pass.

The rules mirror airlift/github-actions/check-commit-messages: subjects are at
most 60 characters and should be at most 50, and ordinary description lines are
at most 79 characters and should wrap at 72.

Subjects are shortened one step at a time, keeping the first result that fits,
so no more detail is dropped than necessary:

  Bump com.github.eirslett:frontend-maven-plugin from 2.0.0 to 2.0.1  (66)
  Bump com.github.eirslett:frontend-maven-plugin to 2.0.1             (54)

Nothing is lost in the process, because Dependabot repeats the full name and
both versions in the description and in its updated-dependencies metadata.

This amends HEAD in place and does not push, which keeps it runnable locally:

    git log -1 --pretty=%B
    .github/scripts/reword-dependabot-commit.py
    git log -1 --pretty=%B

Use --dry-run to print the result without amending. When GITHUB_OUTPUT is set,
a "changed" output reports whether the message was rewritten.
"""

import argparse
import os
import re
import subprocess
import sys
import textwrap

MAX_SUBJECT_LENGTH = 60
WRAP_WIDTH = 72
MAX_DESCRIPTION_LINE_LENGTH = 79

URL_PATTERN = re.compile(r"(?:https?://|ssh://|git@|www\.)\S+")
TRAILER_PATTERN = re.compile(
    r"^(?:"
    r"Signed-off-by|Co-authored-by|Assisted-by|Reviewed-by|Acked-by|"
    r"Tested-by|Reported-by|Fixes|Refs|Relates-to|Change-Id"
    r"):\s+\S.+$",
    re.IGNORECASE,
)

# "Bump <name> from <old> to <new>[ <qualifier>]". The name and versions are
# matched non-greedily so a trailing qualifier such as " in /webapp" stays in
# the qualifier group rather than being absorbed into the new version.
BUMP_PATTERN = re.compile(
    r"^Bump (?P<name>\S+) from (?P<old>\S+) to (?P<new>\S+)(?P<qualifier>.*)$"
)
# Trailing scope Dependabot appends: " in /webapp", " in the airlift group",
# " in the airlift group across 1 directory".
QUALIFIER_PATTERN = re.compile(
    r"\s+in\s+(?:/\S+|the\s+\S+\s+group)(?:\s+across\s+\d+\s+director(?:y|ies))?$"
)
ACROSS_PATTERN = re.compile(r"\s+across\s+\d+\s+director(?:y|ies)$")
# A Maven coordinate, "group:artifact", where the group is a dotted namespace.
COORDINATE_PATTERN = re.compile(r"^[\w.-]+\.[\w-]+:(?P<artifact>[\w.-]+)$")

# Dependabot appends a YAML metadata block delimited by "---" and "...".
# Rewrapping it would corrupt the YAML, so it is left untouched.
METADATA_START = "---"
METADATA_END = "..."


def run_git(arguments: list[str], input_text: str | None = None) -> str:
    result = subprocess.run(
        ["git", *arguments],
        check=True,
        input=input_text,
        stdout=subprocess.PIPE,
        text=True,
    )
    return result.stdout


def shorten_subject(subject: str) -> str:
    """Return the longest form of the subject that fits the length limit."""
    for candidate in subject_candidates(subject):
        if len(candidate) <= MAX_SUBJECT_LENGTH:
            return candidate

    # Nothing fit, so fall back to a hard truncation on a word boundary. This
    # is unreachable for the messages Dependabot generates today, but a silent
    # CI failure later would be worse than a blunt subject.
    return textwrap.shorten(subject, width=MAX_SUBJECT_LENGTH, placeholder="...")


def subject_candidates(subject: str):
    """Yield progressively shorter forms of a Dependabot subject."""
    yield subject

    match = BUMP_PATTERN.match(subject)
    if match is None:
        # Not a "Bump X from A to B" subject, for example
        # "Bump io.airlift:airbase in the airlift group across 1 directory".
        # Only the trailing qualifier can be trimmed.
        yield ACROSS_PATTERN.sub("", subject)
        yield QUALIFIER_PATTERN.sub("", subject)
        return

    name = match["name"]
    new = match["new"]
    qualifier = match["qualifier"]

    # Drop the old version; the diff and the description still record it.
    yield f"Bump {name} to {new}{qualifier}"
    # Drop the directory or group scope.
    yield f"Bump {name} to {new}{ACROSS_PATTERN.sub('', qualifier)}"
    yield f"Bump {name} to {new}"

    # Drop the group id from a Maven coordinate; the artifact id identifies the
    # dependency well enough and the description keeps the full coordinate.
    coordinate = COORDINATE_PATTERN.match(name)
    if coordinate is not None:
        yield f"Bump {coordinate['artifact']} to {new}"

    # Last resort: name the dependency without a version, which is what
    # Dependabot itself does for coordinates that are too long to fit.
    yield f"Bump {name}"
    if coordinate is not None:
        yield f"Bump {coordinate['artifact']}"


def is_wrapping_exempt(line: str, in_code_block: bool) -> bool:
    """Mirror the checker's exemptions so only flagged lines are rewrapped."""
    stripped = line.strip()

    if in_code_block or not stripped:
        return True
    if stripped.startswith(">"):
        return True
    if TRAILER_PATTERN.fullmatch(stripped):
        return True

    # A line holding a URL or another token too long to break is exempt only
    # when what remains around that token already fits.
    tokens = stripped.split()
    if any(URL_PATTERN.search(token) for token in tokens):
        return is_wrapped_without_unwrappable_tokens(tokens)
    if any(len(token) > MAX_DESCRIPTION_LINE_LENGTH for token in tokens):
        return is_wrapped_without_unwrappable_tokens(tokens)

    return False


def is_wrapped_without_unwrappable_tokens(tokens: list[str]) -> bool:
    wrappable = [
        token
        for token in tokens
        if not URL_PATTERN.search(token)
        and len(token) <= MAX_DESCRIPTION_LINE_LENGTH
    ]
    return len(" ".join(wrappable)) <= MAX_DESCRIPTION_LINE_LENGTH


def rewrap_description(lines: list[str]) -> list[str]:
    """Rewrap description lines that the checker would reject."""
    rewrapped = []
    in_code_block = False
    in_metadata = False

    for line in lines:
        stripped = line.strip()

        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_code_block = not in_code_block
            rewrapped.append(line)
            continue

        # Leave Dependabot's YAML metadata block alone.
        if not in_metadata and stripped == METADATA_START:
            in_metadata = True
            rewrapped.append(line)
            continue
        if in_metadata:
            if stripped == METADATA_END:
                in_metadata = False
            rewrapped.append(line)
            continue

        if len(line) <= MAX_DESCRIPTION_LINE_LENGTH or is_wrapping_exempt(
            line, in_code_block
        ):
            rewrapped.append(line)
            continue

        rewrapped.extend(
            textwrap.wrap(
                line,
                width=WRAP_WIDTH,
                break_long_words=False,
                break_on_hyphens=False,
            )
        )

    return rewrapped


def reword(message: str) -> str:
    lines = message.splitlines()
    if not lines:
        return message

    new_subject = shorten_subject(lines[0])
    description = rewrap_description(lines[1:])

    body = "\n".join(description).strip("\n")
    if body:
        return f"{new_subject}\n\n{body}\n"
    return f"{new_subject}\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Reword HEAD to satisfy the commit message policy."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the reworded message without amending the commit.",
    )
    args = parser.parse_args()

    original = run_git(["show", "-s", "--format=%B", "HEAD"]).strip("\n")
    reworded = reword(original).strip("\n")

    if reworded == original:
        print("Commit message already complies, nothing to do.")
        report_changed(False)
        return 0

    if args.dry_run:
        print(reworded)
        report_changed(True)
        return 0

    run_git(["commit", "--amend", "--allow-empty", "--file=-"], input_text=reworded)

    print("Reworded commit message:")
    print(f"  from: {original.splitlines()[0]}")
    print(f"  to:   {reworded.splitlines()[0]}")
    report_changed(True)
    return 0


def report_changed(changed: bool) -> None:
    github_output = os.environ.get("GITHUB_OUTPUT")
    if not github_output:
        return
    with open(github_output, "a", encoding="utf-8") as output:
        output.write(f"changed={str(changed).lower()}\n")


if __name__ == "__main__":
    sys.exit(main())
