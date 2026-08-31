"""Structural verifier for PhaseZeroContainmentPolicy's enabled admin rules."""

from __future__ import annotations

from dataclasses import dataclass
import pathlib
import sys


class PolicyStructureError(ValueError):
    """The Java source cannot prove the required enabled-policy structure."""


@dataclass(frozen=True)
class Token:
    kind: str
    value: str
    offset: int


ADMIN_DECLARATION_PREFIX = (
    "private",
    "static",
    "final",
    "List",
    "<",
    "String",
    ">",
    "ADMIN_FAMILIES",
    "=",
    "List",
    ".",
    "of",
    "(",
)
ROLE_ADMIN_STATEMENT = (
    "requestMatchers",
    "(",
    "auth",
    ",",
    "ADMIN_FAMILIES",
    ")",
    ".",
    "hasAuthority",
    "(",
    '"ROLE_ADMIN"',
    ")",
    ";",
)
DENY_ALL_STATEMENT = (
    "auth",
    ".",
    "anyRequest",
    "(",
    ")",
    ".",
    "denyAll",
    "(",
    ")",
    ";",
)


def lex_java(source: str) -> list[Token]:
    tokens: list[Token] = []
    index = 0
    length = len(source)
    while index < length:
        char = source[index]
        if char.isspace():
            index += 1
            continue
        if source.startswith("//", index):
            newline = source.find("\n", index + 2)
            index = length if newline < 0 else newline + 1
            continue
        if source.startswith("/*", index):
            closing = source.find("*/", index + 2)
            if closing < 0:
                raise PolicyStructureError("unterminated block comment")
            index = closing + 2
            continue
        if source.startswith('\"\"\"', index):
            closing = source.find('\"\"\"', index + 3)
            if closing < 0:
                raise PolicyStructureError("unterminated text block")
            tokens.append(Token("STRING", source[index : closing + 3], index))
            index = closing + 3
            continue
        if char in {'\"', "'"}:
            quote = char
            start = index
            index += 1
            while index < length:
                current = source[index]
                if current in "\r\n":
                    raise PolicyStructureError("unterminated string or character literal")
                if current == "\\":
                    index += 2
                    continue
                index += 1
                if current == quote:
                    tokens.append(Token("STRING" if quote == '\"' else "CHAR", source[start:index], start))
                    break
            else:
                raise PolicyStructureError("unterminated string or character literal")
            continue
        if char.isalpha() or char in {"_", "$"}:
            start = index
            index += 1
            while index < length and (source[index].isalnum() or source[index] in {"_", "$"}):
                index += 1
            tokens.append(Token("IDENTIFIER", source[start:index], start))
            continue
        if char.isdigit():
            start = index
            index += 1
            while index < length and (source[index].isalnum() or source[index] in {"_", "."}):
                index += 1
            tokens.append(Token("NUMBER", source[start:index], start))
            continue
        tokens.append(Token("SYMBOL", char, index))
        index += 1
    if not tokens:
        raise PolicyStructureError("zero-token scan universe")
    return tokens


def delimiter_facts(tokens: list[Token]) -> tuple[dict[int, int], list[int]]:
    openings = {"(": ")", "[": "]", "{": "}"}
    closings = {closing: opening for opening, closing in openings.items()}
    stack: list[tuple[str, int]] = []
    pairs: dict[int, int] = {}
    brace_depth: list[int] = []
    depth = 0
    for index, token in enumerate(tokens):
        brace_depth.append(depth)
        value = token.value
        if value in openings:
            stack.append((value, index))
            if value == "{":
                depth += 1
        elif value in closings:
            if not stack or stack[-1][0] != closings[value]:
                raise PolicyStructureError(f"mismatched closing delimiter {value!r}")
            opening, opening_index = stack.pop()
            pairs[opening_index] = index
            pairs[index] = opening_index
            if opening == "{":
                depth -= 1
    if stack:
        opening, _ = stack[-1]
        raise PolicyStructureError(f"unterminated delimiter {opening!r}")
    return pairs, brace_depth


def token_values(tokens: list[Token], start: int, stop: int) -> tuple[str, ...]:
    return tuple(token.value for token in tokens[start:stop])


def locate_policy_class(
    tokens: list[Token], pairs: dict[int, int]
) -> tuple[int, int]:
    candidates: list[tuple[int, int]] = []
    for index in range(len(tokens) - 2):
        if tokens[index].value != "class" or tokens[index + 1].value != "PhaseZeroContainmentPolicy":
            continue
        opening = index + 2
        while opening < len(tokens) and tokens[opening].value not in {"{", ";"}:
            opening += 1
        if opening < len(tokens) and tokens[opening].value == "{" and opening in pairs:
            candidates.append((opening, pairs[opening]))
    if len(candidates) != 1:
        raise PolicyStructureError(
            "PhaseZeroContainmentPolicy class declaration is missing or ambiguous"
        )
    return candidates[0]


def validate_admin_families(
    tokens: list[Token], pairs: dict[int, int], brace_depth: list[int], class_body: tuple[int, int]
) -> None:
    class_open, class_close = class_body
    member_depth = brace_depth[class_open] + 1
    candidates: list[int] = []
    prefix_length = len(ADMIN_DECLARATION_PREFIX)
    for start in range(class_open + 1, class_close - prefix_length + 1):
        if brace_depth[start] != member_depth:
            continue
        if token_values(tokens, start, start + prefix_length) == ADMIN_DECLARATION_PREFIX:
            candidates.append(start)
    if len(candidates) != 1:
        raise PolicyStructureError("ADMIN_FAMILIES declaration is missing, malformed, or ambiguous")

    opening = candidates[0] + prefix_length - 1
    closing = pairs.get(opening)
    if closing is None or closing + 1 >= class_close or tokens[closing + 1].value != ";":
        raise PolicyStructureError("ADMIN_FAMILIES declaration is malformed")
    arguments = tokens[opening + 1 : closing]
    if not arguments:
        raise PolicyStructureError("ADMIN_FAMILIES declaration is empty")
    for index, token in enumerate(arguments):
        if index % 2 == 0:
            if token.kind != "STRING" or token.value.startswith('\"\"\"'):
                raise PolicyStructureError("ADMIN_FAMILIES declaration must contain only string paths")
            content = token.value[1:-1]
            if not content.strip():
                raise PolicyStructureError("ADMIN_FAMILIES declaration contains an empty path")
        elif token.value != ",":
            raise PolicyStructureError("ADMIN_FAMILIES declaration is malformed")
    if len(arguments) % 2 == 0:
        raise PolicyStructureError("ADMIN_FAMILIES declaration is malformed")


def locate_apply_enabled(
    tokens: list[Token], pairs: dict[int, int], brace_depth: list[int], class_body: tuple[int, int]
) -> tuple[int, int]:
    class_open, class_close = class_body
    member_depth = brace_depth[class_open] + 1
    candidates: list[tuple[int, int, int]] = []
    for name_index in range(class_open + 1, class_close):
        if tokens[name_index].value != "applyEnabled" or brace_depth[name_index] != member_depth:
            continue
        parameter_open = name_index + 1
        if parameter_open >= class_close or tokens[parameter_open].value != "(":
            continue
        parameter_close = pairs.get(parameter_open)
        if parameter_close is None:
            continue
        body_open = parameter_close + 1
        while body_open < class_close and tokens[body_open].value not in {"{", ";"}:
            body_open += 1
        if body_open < class_close and tokens[body_open].value == "{" and body_open in pairs:
            candidates.append((name_index, body_open, pairs[body_open]))
    if len(candidates) != 1:
        raise PolicyStructureError("applyEnabled method declaration is missing or ambiguous")

    name_index, body_open, body_close = candidates[0]
    if name_index < 3 or token_values(tokens, name_index - 3, name_index) != (
        "public",
        "static",
        "void",
    ):
        raise PolicyStructureError("applyEnabled declaration must be public static void")
    return body_open, body_close


def top_level_statements(
    tokens: list[Token], pairs: dict[int, int], brace_depth: list[int], body: tuple[int, int]
) -> list[tuple[str, ...]]:
    body_open, body_close = body
    statement_depth = brace_depth[body_open] + 1
    statements: list[tuple[str, ...]] = []
    segment_start = body_open + 1
    index = segment_start
    while index < body_close:
        token = tokens[index]
        if token.value == "{" and brace_depth[index] == statement_depth:
            index = pairs[index] + 1
            segment_start = index
            continue
        if token.value == ";" and brace_depth[index] == statement_depth:
            statements.append(token_values(tokens, segment_start, index + 1))
            segment_start = index + 1
        index += 1
    return statements


def verify(source: str) -> None:
    tokens = lex_java(source)
    pairs, brace_depth = delimiter_facts(tokens)
    class_body = locate_policy_class(tokens, pairs)
    validate_admin_families(tokens, pairs, brace_depth, class_body)
    method_body = locate_apply_enabled(tokens, pairs, brace_depth, class_body)
    statements = top_level_statements(tokens, pairs, brace_depth, method_body)
    if statements.count(ROLE_ADMIN_STATEMENT) != 1:
        raise PolicyStructureError(
            "applyEnabled must contain exactly one top-level ROLE_ADMIN binding statement"
        )
    if statements.count(DENY_ALL_STATEMENT) != 1:
        raise PolicyStructureError(
            "applyEnabled must contain exactly one top-level denyAll fallback statement"
        )


def main(arguments: list[str]) -> int:
    if len(arguments) != 2:
        print(f"usage: {arguments[0]} POLICY_FILE", file=sys.stderr)
        return 2
    policy_file = pathlib.Path(arguments[1])
    if not policy_file.is_file():
        print(f"PhaseZeroContainmentPolicy policy file is missing: {policy_file}", file=sys.stderr)
        return 1
    try:
        source = policy_file.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        print(f"PhaseZeroContainmentPolicy policy file is unreadable: {error}", file=sys.stderr)
        return 1
    if not source:
        print(f"PhaseZeroContainmentPolicy policy file is empty: {policy_file}", file=sys.stderr)
        return 1
    try:
        verify(source)
    except PolicyStructureError as error:
        print(f"PhaseZeroContainmentPolicy structural validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
