# Composition version-range grammar

`contracts/composition/version-range-v1.json` is the platform-owned grammar for capability, workflow, and application compatibility. The backend and frontend load the same case file and implement the same interval rules.

Accepted expressions are exact semantic versions (`1.2`, `1.2.3`), wildcard intervals (`1.x`, `1.*`, `1.2.x`, `1.2.*`), or one lower and/or upper bound using `>=`, `>`, `<=`, and `<`. Wildcards are inclusive lower and exclusive upper intervals. Bounds retain their inclusive or exclusive operator. Numeric components cannot have leading zeroes or exceed the declared maximum. Prerelease, build metadata, `^`, `~`, `=`, `||`, and provider-specific expressions are unsupported.

Empty, malformed, duplicate-direction, contradictory, and ambiguous expressions return typed validation errors. The OpenAPI candidate advertises this grammar with `x-composition-version-range-grammar: composition-version-range-v1`.
