---
nav_order: 29
parent: Decision Records
---
<!-- markdownlint-disable-next-line MD025 -->
# Use CUID2 as globally unique identifiers

## Context and Problem Statement

Each BibEntry needs to have a unique id.
See [Remote Storage - JabDrive](../code-howtos/remote-storage-jabdrive.md) for details.

## Decision Drivers

* "Nice and modern looking" identifiers
* Easy to generate

## Considered Options

* UUIDv4
* [CUID2](https://github.com/paralleldrive/cuid2)
* [Nano ID](https://github.com/ai/nanoid)

## Decision Outcome

Chosen option: "CUID2", because resolves all decision drivers.
