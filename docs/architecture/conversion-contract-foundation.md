# Typed conversion contract foundation

A contract declares typed input and output Artifact requirements, cardinality
(one-to-one, one-to-many, or many-to-one), MIME/container/codec and schema
version ranges, parameter types/defaults, deterministic fingerprinting,
contract version, execution mode, retry/cancellation policy, quota estimate,
scope and lineage requirements. Validation is fail-closed and returns stable
codes with structured locations. Contract identity and versions are platform
types; provider compatibility is an implementation detail.

Conversion Specifications persist the normalized declaration and fingerprint.
They do not run work, admit a Composition, create a result repository, or write
Storage objects.
