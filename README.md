# Stratastax Search API

[![Stratastax](https://img.shields.io/badge/Product-stratastax.dev-4F46E5?style=flat)](https://stratastax.dev/)
[![](https://img.shields.io/badge/Buijs-Software-blue)](https://buijs.dev/)
[![GitHub](https://img.shields.io/github/license/buijs-dev/stratastax-search?color=black)](https://github.com/buijs-dev/stratastax-search/blob/main/LICENSE)
[![codecov](https://codecov.io/gh/buijs-dev/stratastax-search/graph/badge.svg?token=BNKJJFHIcP)](https://codecov.io/gh/buijs-dev/stratastax-search)
[![CodeScene Average Code Health](https://codescene.io/projects/85920/status-badges/average-code-health)](https://codescene.io/projects/85920)
[![CodeScene System Mastery](https://codescene.io/projects/85920/status-badges/system-mastery)](https://codescene.io/projects/85920)

A generic search API used throughout Stratastax.
Searches support field-based sorting, RSQL-style filtering, and cursor-based pagination.

This document describes the contract-level behavior that all search endpoints must implement consistently. 
It is the reference for implementing search/filter/sort/pagination logic in Kotlin.

See [USAGE.md](USAGE.md) for end-to-end examples: declaring entity fields and search configurations,
parsing requests, assembling cursor pages and building queries with the Kotlin DSL.

## Sorting
An optional parameter that allows you to sort the results by one or more fields.
The format is `field1:ASC,field2:DESC`.

- Field names are compared case-insensitively (`playedAt:ASC` and `playedat:ASC` are equivalent).
- The `ASC`/`DESC` direction keywords are compared case-insensitively.
- If direction is not specified, `ASC` is assumed.
- The comparison of the underlying field *values* (e.g., string ordering) follows the database collation and is not affected by this rule.

Search results are **always** sorted. If no `sort` parameter is provided, 
results are sorted by the endpoint's default sort field (documented per endpoint),
with the internal unique identifier appended as a tiebreaker, as described below.

When multiple records have identical sort values, an internal unique identifier is automatically appended as the final sort key. 
This guarantees deterministic ordering across requests.

> Only sortable fields exposed by the endpoint may be used.

## Filtering
Filtering is based on an RSQL-style query syntax. 
Filter parameters should be URL-encoded when sent in a request. 
Filter operators and field names are evaluated **case-insensitively**; values are not (see [Data Types & Formatting](#data-types--formatting)).

> Only filterable fields documented for the endpoint are supported.

### Supported Operators

| Operator  | Description                         | Supported Data Type                                        | Example                            |
|:----------|:------------------------------------|:-----------------------------------------------------------|:-----------------------------------|
| `==`      | Equal to                            | String, Number, Boolean, Date/Time, Enum, UUID, JSON, Null | `status==ACTIVE`                   |
| `!=`      | Not equal to                        | String, Number, Boolean, Date/Time, Enum, UUID, JSON, Null | `status!=CANCELLED`                |
| `=gt=`    | Greater than                        | Number, Date/Time                                          | `playedAt=gt=2026-01-01T00:00:00Z` |
| `=ge=`    | Greater than or equal to            | Number, Date/Time                                          | `score=ge=50`                      |
| `=lt=`    | Less than                           | Number, Date/Time                                          | `age=lt=18`                        |
| `=le=`    | Less than or equal to               | Number, Date/Time                                          | `price=le=99.95`                   |
| `=in=`    | In a list of values                 | String, Number, Enum, UUID                                 | `status=in=(PENDING,ACTIVE)`       |
| `=out=`   | Not in a list of values             | String, Number, Enum, UUID                                 | `role=out=(GUEST,BANNED)`          |
| `=like=`  | Pattern matching (case-sensitive)   | String                                                     | `name=like="John%"`                |
| `=ilike=` | Pattern matching (case-insensitive) | String                                                     | `name=ilike="%john%"`              |

This is the only supported syntax; no alternative notations are accepted.

In a pattern, `%` matches zero or more characters and `_` matches exactly one. 
With `=ilike=`, a backslash makes the next `%`, `_` or `\` literal: `code=ilike=100\%`. Quotes consume a backslash themselves, so quoted this becomes `code=ilike="100\\%"`.

### Logical Operators & Precedence
You can combine multiple criteria using logical operators:

* **AND**: `;` (semicolon) or `and`
* **OR**: `,` (comma) or `or`

> **Note on Precedence:** The `AND` (`;`) operator has a higher precedence than `OR` (`,`).
> Use parentheses `()` to explicitly define the grouping and override the default precedence.

### Examples

**1. Basic comparison & multiple conditions (AND)**
Matches played after January 1, 2026 AND with status COMPLETED:

```
filter=playedAt=gt=2026-01-01T00:00:00Z;status==COMPLETED
```

**2. List inclusion (IN operator)**
Matches with status PENDING or IN_PROGRESS:

```
filter=status=in=(PENDING,IN_PROGRESS)
```

**3. Combining AND & OR with Parentheses**
Matches played in 2026 AND (status is COMPLETED OR score is greater than 100):

```
filter=playedAt=ge=2026-01-01T00:00:00Z;(status==COMPLETED,score=gt=100)
```

**4. String matching with Wildcards**
Find player names starting with "Alex" (case-insensitive):

```
filter=name=ilike="Alex%"
```

### Data Types & Formatting
- Strings: Can be unquoted if they contain no whitespace, quotes, parentheses or the connectors `;` and `,`. Use double or single quotes for strings containing any of those: `title=="Grand Final"`. A value following an operator may contain `=` and `!` unquoted (`code==a=b` matches the text `a=b`). Inside quotes a quote character or backslash is escaped with a backslash.
- Numbers: Integer or floating-point numbers without quotes: `score=ge=100`, `price=le=99.95`.
- Booleans: `true` or `false`: `active==true`.
- Date & Times: Must be a complete ISO-8601 instant, preferably in UTC: `2026-08-04T10:00:00Z`; fractional seconds are allowed (`2026-08-04T10:00:00.500Z`). A date without a time (`2026-08-04`) is rejected. Offsets such as `+02:00` are tolerated and converted to the same instant, but clients should send UTC.
- Enums: Must exactly match one of the documented values; enum values are case-sensitive: `status==ACTIVE`.
- UUIDs: The standard textual form: `id==3f2504e0-4f89-11d3-9a0c-0305e82c3301`.
- JSON: Only `==` and `!=`, compared against the whole value.
- Null Values: Use the `null` keyword for checking missing values: `deletedAt==null` or `notes!=null`. The keyword is case-insensitive and only applies to `==` and `!=`. A quoted value is always literal text: `notes=="null"` matches the text `null`.

## Pagination
All search endpoints support pagination. 
After each request, the response will contain a `cursor` parameter that can be used to fetch the previous or next page of results. 
A cursor is an opaque token identifying the last item found based on the given criteria. 
This can be used by the REST service to find the next set of items efficiently. 
Clients must never inspect or modify cursor values. Example:

```json
{
  "criteria": {
    "sort": "playedAt:DESC,status:ASC",
    "filter": "playedAt=gt=2026-01-01T00:00:00Z"
  },
  "pagination": {
    "limit": 10,
    "cursor": {
      "previous": "645344343FSDNJDFS",
      "current": "ABCD8764",
      "next": "XXXX76429"
    }
  },
  "items": [ ... ]
}
```

The first request has no cursor and returns results using only `sort`/`filter`; the response's `previous` cursor will be `null` in this case. 
When there are no more additional items, the `next` cursor is `null`. The `current` cursor is the cursor the page was requested with, so it is `null` for a first page.

When navigating to the next set of items using the next cursor, all original criteria can be optionally added to the request. 
E.g., taking the above example into account, the following requests are both valid and return the exact same response:

```shell
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&filter=playedAt=gt=2026-01-01T00:00:00Z&sort=playedAt:DESC,status:ASC"
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH"
```

The cursor itself carries the criteria it was issued for (it remains opaque and signed), which is what allows a request to omit them.
Included criteria (as returned in the original response) in a paginated request must exactly match the criteria used to generate the cursor.
An omitted `sort` means the default sort of the endpoint: criteria are valid when they resolve to the same sort and filter as the cursor, so a client that never sends `sort` can repeat just its `filter`. 
An error is returned when it does not match, as in the following example:

```shell
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&filter=playedAt=gt=2026-01-01T00:00:00Z&sort=playedAt:DESC,status:ASC"
# Error because sort values are missing
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&filter=playedAt=gt=2026-01-01T00:00:00Z"
# Error because filter values are missing
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&sort=playedAt:DESC,status:ASC"
# Error because sort values are changed
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&filter=playedAt=gt=2026-01-01T00:00:00Z&sort=playedAt:DESC,status:DESC"
```

> Limit is not part of the criteria and thus may be changed when requesting data using the next cursor.

Because a cursor embeds its filter, the length of a filter is limited to 1000 characters (`maxFilterLength` on the `SearchParser`) and a cursor longer than 4096 characters (`maxCursorLength`) is rejected as invalid without being decoded.

Cursor validation is important. This prevents clients from accidentally mixing cursors with different search criteria, 
which could otherwise lead to skipped or duplicated results.

| Cursor  | Criteria  | Result          |
|---------|-----------|-----------------|
| absent  | n/a       | ✔ (first page) |
| present | omitted   | ✔              |
| present | identical | ✔              |
| present | partial   | ❌              |
| present | modified  | ❌              |

### Invalid or expired cursors
If a cursor is malformed, unrecognized, or has expired, an `InvalidCursorException` is thrown. The same applies when the criteria of the request do not match the criteria of the cursor.
This should result in a `400 Bad Request` with an error body identifying the cursor as the cause; handling the exception is up to the consuming application.
Clients should treat this as unrecoverable for that cursor and restart pagination from the first page (no cursor).

A cursor expires 24 hours after it was issued by default. The expiry is configurable on the `SearchParser` (`cursorExpiry`).

### Limit
`limit` must be at least 1 and at most the maximum of the endpoint; other values are rejected.

## Related Stratastax projects

- [stratastax-entity](https://github.com/buijs-dev/stratastax-entity) - the `@Search*` annotations this library implements
- [stratastax-codegen](https://github.com/buijs-dev/stratastax-codegen) - generates the search configuration per entity
- [stratastax-persistence](https://github.com/buijs-dev/stratastax-persistence) - applies search criteria to jOOQ queries
- [stratastax-quarkus › quarkus-starter](https://github.com/buijs-dev/stratastax-quarkus/tree/main/quarkus-starter) - wires search into a Quarkus application
- [stratastax-contract](https://github.com/buijs-dev/stratastax-contract) - the OpenAPI specs whose `sort`/`filter`/`cursor` parameters follow this contract
- [stratastax-style](https://github.com/buijs-dev/stratastax-style) - the Kotlin code style this library is formatted with
