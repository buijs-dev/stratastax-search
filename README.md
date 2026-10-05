# Stratastax Search API

[![](https://img.shields.io/badge/Buijs-Software-blue)](https://buijs.dev/)
[![GitHub](https://img.shields.io/github/license/buijs-dev/klutter?color=black)](https://github.com/buijs-dev/stratastax-search/blob/main/LICENSE)

A generic search API used throughout Stratastax.
Searches support field-based sorting, RSQL-style filtering, and cursor-based pagination.

This document describes the contract-level behavior that all search endpoints must implement consistently. 
It is the reference for implementing search/filter/sort/pagination logic in Kotlin.

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
Filter operators and field names are evaluated **case-insensitively**.

> Only filterable fields documented for the endpoint are supported.

### Supported Operators

| Operator | Description                 | Supported Data Type                            | Example                            |
|:---------|:----------------------------|:-----------------------------------------------|:-----------------------------------|
| `==`     | Equal to                    | String, Number, Boolean, Date/Time, Enum, Null | `status==ACTIVE`                   |
| `!=`     | Not equal to                | String, Number, Boolean, Date/Time, Enum, Null | `status!=CANCELLED`                |
| `=gt=`   | Greater than                | Number, Date/Time                              | `playedAt=gt=2026-01-01T00:00:00Z` |
| `=ge=`   | Greater than or equal to    | Number, Date/Time                              | `score=ge=50`                      |
| `=lt=`   | Less than                   | Number, Date/Time                              | `age=lt=18`                        |
| `=le=`   | Less than or equal to       | Number, Date/Time                              | `price=le=99.95`                   |
| `=in=`   | In a list of values         | String, Number, Enum                           | `status=in=(PENDING,ACTIVE)`       |
| `=out=`  | Not in a list of values     | String, Number, Enum                           | `role=out=(GUEST,BANNED)`          |
| `=like=` | Wildcard / pattern matching | String                                         | `name=like="*john*"`               |

This is the only supported syntax; no alternative notations are accepted.

The `*` wildcard matches zero or more characters. `=like=` value matching is case-insensitive.

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
filter=name=like="Alex*"
```

### Data Types & Formatting
- Strings: Can be unquoted if they contain no whitespace or special characters (`;`, `,`, `=`, etc.). Use double or single quotes for strings containing spaces: `title=="Grand Final"`.
- Numbers: Integer or floating-point numbers without quotes: `score=ge=100`, `price=le=99.95`.
- Booleans: `true` or `false`: `active==true`.
- Date & Times: Must be formatted as ISO-8601 UTC strings: `2026-08-04T10:00:00Z`.
- Null Values: Use the `null` keyword for checking missing values: `deletedAt==null` or `notes!=null`.

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
When there are no more additional items, the `next` cursor is `null`.

When navigating to the next set of items using the next cursor, all original criteria can be optionally added to the request. 
E.g., taking the above example into account, the following requests are both valid and return the exact same response:

```shell
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH&filter=playedAt=gt=2026-01-01T00:00:00Z&sort=playedAt:DESC,status:ASC"
curl -X GET "https://api.stratastax.dev/v1/matches?limit=10&cursor=ABXCFEDFSFDBH"
```

Included criteria (as returned in the original response) in a paginated request must exactly match the criteria used to generate the cursor. 
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
If a cursor is malformed, unrecognized, or has expired, the API returns `400 Bad Request` with an error body identifying the cursor as the cause. 
Clients should treat this as unrecoverable for that cursor and restart pagination from the first page (no cursor).