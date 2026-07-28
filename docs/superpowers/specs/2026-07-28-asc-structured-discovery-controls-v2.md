# ASC Structured Discovery Controls v2

Asset Share Commons discovery uses a strict structured protocol between ASC and the external agent. ASC owns the rendered controls, QueryBuilder serialization, hidden predicates, page defaults, action parameters, and backend path enforcement. The agent returns desired replacement state for known writable controls plus a restricted residual query.

## Request

ASC sends the agent a top-level request with `version: 2`, `prompt`, and `context`. The proxy transports `prompt` separately and forwards the browser-generated context unchanged.

`context.query` contains:

* `fulltext`: string or `null`.
* `path`: string or `null`, singular.
* `allowedPathRoots`: server-provided root metadata from `PagePredicate.getPaths()`. This is capability metadata, not agent-writable state.

`context.controls` contains only agent-writable rail controls. Each descriptor has stable `id`, display `title`, `kind`, current replacement `state`, and any relevant `cardinality`, `options`, or `constraints`. QueryBuilder names, property paths, group IDs, delimiters, hidden predicates, and implementation details are never sent.

Supported `kind` values:

* `choice`: `state.values`, `cardinality` of `one` or `many`, closed `options`.
* `text`: logical `state.values`, open value constraints.
* `date-range`: nullable `state.lowerBound` and `state.upperBound` using UI `YYYY-MM-DD` values.
* `relative-date`: `state.values`, `cardinality: one`, closed `options`.
* `path`: `state.values`, `cardinality` of `one` or `many`, closed authored path `options`.

## Response

The response must contain exactly:

* `version: 2`
* `query`: exactly `fulltext` and singular `path`, each string or `null`
* `controlUpdates`: array of `{id, state}`

Each update is a complete replacement state. Empty `values` or nullable date bounds explicitly clear a control. Only controls whose desired state differs from the request should be returned. ASC normalizes no-op updates as diagnostics, but any real validation error rejects the whole response atomically.

Unknown query keys, arbitrary QueryBuilder parameters, sort, paging, layout, type, property predicates, hidden/system predicates, and group operators are rejected.

## Path Authority

If a path is represented by an enabled path-control option, the agent must return that path through the control update and set residual `query.path` to `null`.

Residual `query.path` is allowed only when it is:

* an absolute repository path with no traversal, query string, fragment, or backslash,
* exactly under one configured `allowedPathRoots` root or a descendant of one,
* not present as any authored path-control option, including disabled options,
* and every writable path control is empty after applying the response.

A user edit to any path control clears the active residual path.

## Validation Errors

ASC rejects the complete response and leaves the DOM unchanged when any of these occur: wrong version or shape, unknown fields, unknown or duplicate control IDs, state shape mismatch, too many values for `one`, unknown option values, newly selected disabled values, invalid text constraints, malformed date bounds, residual path outside allowed roots, representable path returned residually, or residual path colliding with selected path controls.

## Examples

Choice, relative date, and authored path:

```json
{
  "version": 2,
  "query": {"fulltext": null, "path": null},
  "controlUpdates": [
    {"id": "cmp-format", "state": {"values": ["image/jpeg"]}},
    {"id": "cmp-orientation", "state": {"values": ["landscape"]}},
    {"id": "cmp-recency", "state": {"values": ["-1M"]}},
    {"id": "cmp-location", "state": {"values": ["/content/dam/campaigns"]}}
  ]
}
```

Allowed but unrepresented singular residual path:

```json
{
  "version": 2,
  "query": {"fulltext": null, "path": "/content/dam/legal"},
  "controlUpdates": [
    {"id": "cmp-location", "state": {"values": []}}
  ]
}
```

Text plus absolute date range:

```json
{
  "version": 2,
  "query": {"fulltext": null, "path": null},
  "controlUpdates": [
    {"id": "cmp-keywords", "state": {"values": ["red", "blue"]}},
    {"id": "cmp-created", "state": {"lowerBound": "2026-07-01", "upperBound": "2026-07-15"}}
  ]
}
```

ASC applies validated controls first, stores residual `fulltext` and `path`, serializes the rendered form through existing names, overlays residual query fields, then applies action parameters last.
