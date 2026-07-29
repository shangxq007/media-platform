# Platform Algorithms

## Purpose

This directory contains shared algorithm modules that are domain-agnostic and reusable
across the platform. Each sub-module provides a specific category of algorithms.

## Naming Rules

- Leaf projects are registered as `:platform-algorithms:<category>`
- Physical directories follow `platform-algorithms/<category>/`
- Package roots follow `com.example.platform.algorithms.<category>`

## Dependency Direction

Consumers depend on algorithm modules. Algorithm modules MUST NOT depend on:
- Domain modules (Artifact, Timeline, Revision, Media Execution Plan)
- Infrastructure modules (Storage, OpenDAL, Persistence)
- Spring, Cloud SDKs, or any external framework

## New Algorithm Sub-module Gate

A new algorithm sub-module may only be created when:
1. At least two distinct domain consumers require the same algorithm category
2. The algorithm is truly domain-agnostic (no domain types in method signatures)
3. The algorithm is pure (no side effects, no infrastructure dependencies)

## Current Sub-modules

- `:platform-algorithms:graph` — Directed graph algorithms (cycle detection, topological order, reachability)

## Candidate Directions (NOT YET AUTHORIZED)

- Temporal algorithms — temporal relation computation
- Sequence algorithms — sequence diff and merge
- Optimization — NOT FROZEN, belongs to Execution Planning domain, not structural kernel
