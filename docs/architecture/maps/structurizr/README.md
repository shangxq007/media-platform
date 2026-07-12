# Structurizr (Optional)

**Status:** PLACEHOLDER — not maintained
**Authority:** ARCH-MAP.0 / ADR-027

---

## Purpose

Optional high-level System/Container-only Structurizr view. NOT the primary architecture map.

## When to Maintain

- If team needs a standard C4 model for external stakeholders
- If integrating with Structurizr Lite for web-based viewing
- If required by compliance or documentation standards

## When NOT to Maintain

- For internal development (use LikeC4 instead)
- For detailed component views (LikeC4 is better)
- If it duplicates LikeC4 effort

## Notes

- No direct LikeC4-to-Structurizr export is assumed
- If created, maintain as separate high-level System/Container-only workspace
- Keep in sync manually or via CI validation
- Structurizr DSL is more verbose than LikeC4

## LikeC4 is Primary

LikeC4 (`likec4/`) is the primary architecture map. Structurizr is optional and derivative.
