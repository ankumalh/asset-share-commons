# Discovery Agent Integration — Semantic Search Rail

## What this is

Asset Share Commons (ASC) users can type a natural-language request — *"show me
grayscale assets created in the past 10 days"* — into the existing search bar and
have it correctly populate the existing search-rail filters (facets, date ranges,
tags, folders) and run the search. There is no new UI, no new page, and no change
to how a customer has configured their search rail.

The AEM Experience Advisory Agent is the LLM service that interprets the request.
It does not talk to AEM, does not generate AEM QueryBuilder syntax, and does not
know anything about JCR property paths, predicate names, or group numbering. It
only ever sees a small, sanitized, logical description of the search rail's
current controls and returns the desired new state of those controls.

---

## Design rationale

### Structurally fewer hallucinations — an evolution, not a rewrite

The first version of this integration asked the agent to generate AEM QueryBuilder
query strings directly: predicate families (`daterange`, `relativedaterange`,
`propertyvalues`), JCR property paths, group numbering, operators. It worked in the
sense that it returned the right assets, but it failed visibly in the UI. The agent
would pick the wrong predicate family for a field it already had a correct value
for — absolute-date syntax paired with a relative-offset value, or the generic
`property` family where the rail actually used `propertyvalues` — and the rail's
checkboxes and radios would silently fail to reflect the selection. Occasionally it
invented a plausible-looking JCR path by analogy with a sibling field
(`jcr:content/jcr:created` instead of the correct bare `jcr:created`).

Each of these was fixable, but only by teaching the model a new predicate-family
rule in prose, one bug at a time — a pattern that would keep recurring for every
new facet type, because the entire correctness burden lived in prompt engineering.

This version moves that burden back into code. The agent is handed a narrow,
closed, semantic vocabulary — opaque control ids and a closed set of option values
— and returns only `{id, state}` pairs for controls it was explicitly given. There
is no field in the contract for a QueryBuilder parameter name, a JCR property
path, a predicate family, sort order, pagination, or layout, so the agent has no
way to produce one even if it wanted to. Correctness is enforced by a strict,
code-level response schema and a deterministic validator, not by hoping the model
remembers a rule it was told once.

| | v1: agent generates QueryBuilder | v2: agent generates semantic controls |
|---|---|---|
| Agent's job | Produce a raw QueryBuilder query string | Return `{id, state}` pairs for controls it was given |
| Correctness enforced by | Prompt engineering, rule by rule | Response schema + deterministic validator |
| Can invent a property path or predicate family | Yes — observed in production | Not structurally possible |

### Meets enterprise customers where they already are

Every enterprise AEM customer runs a customized ASC — their own search-rail
layout, facets, branding, and component overlays, built up over years of
investment. This design adds natural-language search as a capability of the search
rail they already have, not a new UI surface or a parallel search experience to
adopt, learn, and maintain. A customer's custom facet participates automatically
the moment it exposes the standard `data-asset-share-predicate-id` contract (see
[`components/README.md`](../ui.apps/src/main/content/jcr_root/apps/asset-share-commons/components/README.md))
— no agent changes, no new prompt rules, no re-onboarding.

### AEM calls out, fronted by IMS

`DiscoveryServlet` runs inside AEM and obtains its own IMS access token to call the
discovery agent (`Authorization: Bearer <token>`); the agent never needs
credentials to reach into a customer's AEM environment. This sidesteps the class of
problem where a customer's AEM instance sits behind a custom identity provider,
VPN, network ACL, or on-prem firewall that an externally-hosted agent could never
be configured to reach — the direction of the call matches the direction that's
already trusted and already working today.

### Lower token cost

The agent only ever sees the control descriptors relevant to the visible search
rail — ids, titles, kinds, closed option lists — not raw AEM schema, QueryBuilder
documentation, or metadata form definitions. Prompt size, and per-request cost,
scales with the number of visible search filters, not with the complexity of the
underlying repository or query language.

---

## How it works

```
 ┌────────────┐   1. type prompt    ┌──────────────────────┐
 │  ASC UI     │ ──────────────────▶│  ASC Discovery        │
 │ (browser,   │                    │  Servlet (AEM, Java)  │
 │  search bar)│◀────────────────── │  /bin/asset-share-    │
 └────────────┘  4. control updates │  commons/discovery    │
       ▲                            └───────────┬───────────┘
       │                                        │ 2. IMS bearer token
       │ 5. rail re-renders,                     │    (AEM calls out,
       │    search re-runs                       │     not the reverse)
       │                                        ▼
       │                            ┌───────────────────────┐
       └────────────────────────────│  Discovery Agent       │
         3. { prompt, controls[] }  │  POST /transform-query │
                                    │  (stateless LLM call)  │
                                    └───────────────────────┘
```

1. The user types a prompt into the existing ASC search bar.
2. `DiscoveryServlet` obtains a short-lived IMS access token and calls the
   discovery agent's `POST /transform-query` endpoint, passing the prompt plus a
   logical description of the search rail's current controls (see below).
3. The agent — a stateless LLM call, no AEM connection, no memory of prior
   requests — returns which controls should change and to what values.
4. The servlet returns this to the browser; ASC's existing client-side search JS
   applies the updates to the real, rendered search-rail inputs and any residual
   free-text/path terms.
5. The rail visibly updates (checkboxes, radios, date pickers) and ASC submits the
   search exactly as if the user had clicked those same controls by hand.

Nothing downstream of step 4 is new: the same QueryBuilder serialization, the same
search execution, the same results rendering that already existed in ASC keeps
running unchanged.

---

## The wire contract

For the example above, the agent receives:

```json
{
  "prompt": "show me grayscale assets created in the past 10 days",
  "context": {
    "controls": [
      { "id": "cmp-style", "title": "STYLE", "kind": "choice",
        "options": [{"value": "grayscale", "label": "Grayscale"}, ...] },
      { "id": "cmp-created", "title": "CREATED", "kind": "date-range",
        "state": {"lowerBound": null, "upperBound": null} }
    ]
  }
}
```

And returns only:

```json
{
  "controlUpdates": [
    { "id": "cmp-style", "state": {"values": ["grayscale"]} },
    { "id": "cmp-created", "state": {"lowerBound": "2026-07-19", "upperBound": null} }
  ]
}
```

`id` values are opaque, request-scoped tokens. Option values are copied verbatim
from a closed vocabulary ASC supplied.

---

## Where to look next

* [`docs/superpowers/specs/2026-07-28-asc-structured-discovery-controls-v2.md`](superpowers/specs/2026-07-28-asc-structured-discovery-controls-v2.md) —
  full request/response contract, validation rules, and worked examples.
* [`components/README.md`](../ui.apps/src/main/content/jcr_root/apps/asset-share-commons/components/README.md) —
  how a component author marks a search-rail control as agent-writable.
* `core/src/main/java/.../search/impl/DiscoveryServlet.java` — the IMS-authenticated
  proxy servlet.
* `ui.apps/.../clientlibs/clientlib-site/js/search/discovery-controls.js` — client-side
  correlation between the agent's `controlUpdates` and the rendered rail inputs.
* `aem-experience-advisory-agent` repo, `src/content_advisor/asc_discovery/` — the
  agent-side handler, Pydantic schema, and deterministic response validator.
