# LikeC4 Architecture Maps

**DSL:** LikeC4 v1.58.0
**Source:** media-platform.likec4

---

## Source Files

| File | Purpose |
|------|---------|
| `media-platform.likec4` | Single-file LikeC4 source (model + views) |

## Defined Views

| View | Title | Description |
|------|-------|-------------|
| `systemContext` | System Context | External actors and systems |
| `containerDiagram` | Container View | Major modules within media-platform |
| `controlPlane` | Hermes Control Plane | Control plane components |
| `controlPlaneContext` | Control Plane in Context | Hermes with external integrations |

## Validation

```bash
cd docs/architecture/maps/likec4
npx likec4 validate .
```

## Render Static Site

```bash
cd docs/architecture/maps/likec4
npx likec4 build . -o ../exports/html
```

Output: `docs/architecture/maps/exports/html/index.html`

## Export JSON

```bash
npx likec4 export json . -o ../exports/
```

Output: `docs/architecture/maps/exports.json`

## Export PNG (requires Playwright)

```bash
npx playwright install
npx likec4 export png . -o ../exports/png/
```

## Authority Level

LikeC4 is the **human-curated architecture-intent model**. It is normative for
approved intent views, while source code and accepted ADR/canonical-contract
documents remain authoritative for implementation and semantic decisions. The
generated Spring Modulith C4 view is the separate as-built structural view.

Shared identifiers for logical and physical views are maintained in
`../architecture-identifiers.yaml`.

Authority precedence is defined in
`../../governance/source-of-truth/architecture-authority-model.md`.
