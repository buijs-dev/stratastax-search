# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

First release of Stratastax Search: sorting, RSQL filtering and cursor pagination for the entities described with
stratastax-entity.

- `SearchParser` that turns a `SearchRequest` into a validated `SearchQuery`, with configurable cursor expiry and
  filter and cursor length limits.
- RSQL filter parser with `==`, `!=`, `=gt=`, `=ge=`, `=lt=`, `=le=`, `=in=`, `=out=`, `=like=` and `=ilike=`.
- Sort parser for `field:DIRECTION` lists that always appends the tiebreaker.
- HMAC-signed cursors and `CursorPageAssembler` for previous, current and next cursors.
- `search { }` and filter DSLs, including `containsIgnoreCase`.
- `SearchConfiguration`, `CursorCodec`, `CursorKeyExtractor` and `CursorValueFactory` SPIs.

See [README.md](https://github.com/buijs-dev/stratastax-search/blob/main/README.md) for the search contract.
