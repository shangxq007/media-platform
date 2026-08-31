#!/usr/bin/env python3
"""Behavioral mutation tests for the architecture drift admin-policy predicate."""

from __future__ import annotations

import pathlib
import subprocess
import tempfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/check-architecture-drift.sh"
POLICY = ROOT / (
    "platform-app/src/main/java/com/example/platform/security/"
    "PhaseZeroContainmentPolicy.java"
)


def run_guard(policy: pathlib.Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["bash", str(GUARD), "--check-enabled-admin-policy", str(policy)],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def require_pass(name: str, policy: pathlib.Path) -> None:
    result = run_guard(policy)
    if result.returncode != 0:
        raise AssertionError(
            f"{name} unexpectedly failed: stdout={result.stdout!r} stderr={result.stderr!r}"
        )


def require_fail(name: str, policy: pathlib.Path, expected_error: str) -> None:
    result = run_guard(policy)
    if result.returncode == 0:
        raise AssertionError(f"RED mutation unexpectedly passed: {name}")
    if expected_error not in result.stderr:
        raise AssertionError(
            f"{name} failed through the wrong predicate: stderr={result.stderr!r}"
        )


def mutate_once(source: str, old: str, new: str) -> str:
    if source.count(old) != 1:
        raise AssertionError(f"expected exactly one mutation target: {old!r}")
    return source.replace(old, new)


def main() -> None:
    source = POLICY.read_text(encoding="utf-8")
    require_pass("current enabled-security policy", POLICY)

    with tempfile.TemporaryDirectory(prefix="architecture-admin-policy-") as directory:
        fixture = pathlib.Path(directory) / POLICY.name

        role_admin_statement = (
            'requestMatchers(auth, ADMIN_FAMILIES).hasAuthority("ROLE_ADMIN");'
        )
        role_admin_line = f"        {role_admin_statement}\n"
        deny_all_statement = "auth.anyRequest().denyAll();"
        enabled_tail = (
            "        requestMatchers(auth, AUTHENTICATED_FAMILIES).authenticated();\n"
            f"        {deny_all_statement}\n"
        )

        fixture.write_text(
            mutate_once(
                source,
                role_admin_line,
                f"        // {role_admin_statement}\n"
                "        requestMatchers(auth, ADMIN_FAMILIES).authenticated();\n",
            ),
            encoding="utf-8",
        )
        require_fail(
            "comment decoy for weakened ROLE_ADMIN binding",
            fixture,
            "top-level ROLE_ADMIN binding statement",
        )

        fixture.write_text(
            mutate_once(
                source,
                role_admin_line,
                "        if (false) {\n"
                f"            {role_admin_statement}\n"
                "        }\n"
                "        requestMatchers(auth, ADMIN_FAMILIES).authenticated();\n",
            ),
            encoding="utf-8",
        )
        require_fail(
            "if-false decoy for weakened ROLE_ADMIN binding",
            fixture,
            "top-level ROLE_ADMIN binding statement",
        )

        fixture.write_text(
            mutate_once(
                source,
                enabled_tail,
                "        requestMatchers(auth, AUTHENTICATED_FAMILIES).authenticated();\n"
                f"        // {deny_all_statement}\n",
            ),
            encoding="utf-8",
        )
        require_fail(
            "comment decoy for removed deny-all fallback",
            fixture,
            "top-level denyAll fallback statement",
        )

        fixture.write_text(
            mutate_once(
                source,
                enabled_tail,
                "        requestMatchers(auth, AUTHENTICATED_FAMILIES).authenticated();\n"
                "        if (false) {\n"
                f"            {deny_all_statement}\n"
                "        }\n",
            ),
            encoding="utf-8",
        )
        require_fail(
            "if-false decoy for removed deny-all fallback",
            fixture,
            "top-level denyAll fallback statement",
        )

        admin_declaration = (
            "    private static final List<String> ADMIN_FAMILIES = List.of(\n"
            '            "/api/admin/**",\n'
            '            "/api/audit/admin/**",\n'
            '            "/api/identity/admin/**");\n'
        )
        fixture.write_text(
            mutate_once(source, admin_declaration, ""),
            encoding="utf-8",
        )
        require_fail(
            "missing ADMIN_FAMILIES declaration",
            fixture,
            "ADMIN_FAMILIES declaration is missing",
        )

        fixture.write_text(
            mutate_once(
                source,
                admin_declaration,
                "    private static final List<String> ADMIN_FAMILIES = List.of();\n",
            ),
            encoding="utf-8",
        )
        require_fail(
            "empty ADMIN_FAMILIES declaration",
            fixture,
            "ADMIN_FAMILIES declaration is empty",
        )

        require_fail(
            "missing policy file",
            pathlib.Path(directory) / "missing-policy.java",
            "policy file is missing",
        )

        fixture.write_text("// no Java tokens\n", encoding="utf-8")
        require_fail(
            "zero-token scan universe",
            fixture,
            "zero-token scan universe",
        )

        fixture.write_text(source + "\n/* unterminated", encoding="utf-8")
        require_fail(
            "unterminated block comment",
            fixture,
            "unterminated block comment",
        )

        fixture.write_text(source + '\n"unterminated', encoding="utf-8")
        require_fail(
            "unterminated string literal",
            fixture,
            "unterminated string or character literal",
        )

        fixture.write_text(source.removesuffix("}\n"), encoding="utf-8")
        require_fail(
            "unterminated class brace",
            fixture,
            "unterminated delimiter '{'",
        )

        malformed_admin = admin_declaration.replace(
            '            "/api/admin/**",\n',
            "            42,\n",
        )
        fixture.write_text(
            mutate_once(source, admin_declaration, malformed_admin),
            encoding="utf-8",
        )
        require_fail(
            "malformed ADMIN_FAMILIES declaration",
            fixture,
            "must contain only string paths",
        )

        fixture.write_text(
            mutate_once(source, admin_declaration, admin_declaration + admin_declaration),
            encoding="utf-8",
        )
        require_fail(
            "ambiguous ADMIN_FAMILIES declaration",
            fixture,
            "ADMIN_FAMILIES declaration is missing, malformed, or ambiguous",
        )

        method_declaration = (
            "    public static void applyEnabled(\n"
            "            AuthorizeHttpRequestsConfigurer<HttpSecurity>"
            ".AuthorizationManagerRequestMatcherRegistry auth) {\n"
        )
        duplicate_method = (
            method_declaration
            + f"        {role_admin_statement}\n"
            + f"        {deny_all_statement}\n"
            + "    }\n\n"
        )
        fixture.write_text(
            mutate_once(source, method_declaration, duplicate_method + method_declaration),
            encoding="utf-8",
        )
        require_fail(
            "ambiguous applyEnabled declaration",
            fixture,
            "applyEnabled method declaration is missing or ambiguous",
        )

    print("ARCHITECTURE_ADMIN_POLICY_MUTATION_COUNT=14")
    print("ARCHITECTURE_ADMIN_POLICY_CURRENT_FILE=PASS")


if __name__ == "__main__":
    main()
