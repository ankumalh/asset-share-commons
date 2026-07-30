# Discovery Agent Integration: Server-Resolved Canonical Search

## Outcome

Discovery search resolves a natural-language prompt into the same canonical ASC URL that a user
would produce by selecting search-rail filters and pressing Search.

The browser does not translate the agent response, mutate filter controls, or execute a
discovery-specific query. A successful request performs:

1. One authenticated agent call.
2. One navigation to a canonical, same-page ASC URL.
3. One normal `QuerySearchProviderImpl` QueryBuilder execution after the page reload.

Consequently, the URL, server-rendered selected controls, refresh, sharing, Back behavior, hidden
predicates, page paths, search safety, and custom query processors all use existing ASC behavior.

## Decisions

This implementation deliberately follows ASC's existing server-rendered search model.

| Decision | Reason |
| --- | --- |
| Resolve a prompt into a canonical GET URL | A discovery search must finish in the same state as a user selecting filters and pressing Search. The URL, rendered selections, and results are then reproducible without the agent. |
| Build agent controls from page-scoped Sling Models | The server knows the authored configuration, current request selections, customer overlays, and the actual QueryBuilder parameter mapping. The browser DOM is not the source of truth and does not contain hidden predicates. |
| Resolve fulltext through the rendered `FulltextPredicate` | Standard ASC pages may submit either `fulltext` or `ai-fulltext`. Mapping the residual semantic query back through the page model preserves AI Search and the server-rendered field value. |
| Keep QueryBuilder and JCR-property mapping in AEM | The agent reasons about labels, options, constraints, and semantic state. It cannot invent QueryBuilder groups, predicate families, parameter names, or property paths. |
| Reproduce each component's submitted parameter shape | A semantically equivalent QueryBuilder shape is not sufficient: customer preprocessors and deep-link integrations may observe the request names. Checkbox option indexes, radio indexes, unindexed dropdown names, and repeated multiselect values therefore match manual form serialization. |
| Treat the agent response as a patch over the current search | Discovery refines the user's current search. Omitted controls remain selected, mentioned controls are fully replaced, and an explicit empty state clears a control. |
| Validate the complete response before mapping anything | Unknown IDs, invalid states, disabled or unknown options, unsafe paths, and schema changes fail the entire resolution. A partial agent response is never applied. |
| Never make hidden or page predicates agent-writable | Hidden predicates express application policy. Page predicates define allowed roots. Both remain owned and enforced by the normal ASC search. |
| Provide a public adapter SPI | Standard ASC predicate interfaces work automatically. Customers with nonstandard models or QueryBuilder shapes can add a focused adapter without replacing the resolver. |
| Accept one full-page reload | The reload is what restores normal Sling Model rendering, `PredicateUtil` selection handling, hidden-predicate merging, and `QuerySearchProviderImpl` execution. |

The earlier alternatives were intentionally removed:

- The browser no longer applies agent state directly to rendered controls.
- `DiscoverySearchProviderImpl` no longer translates the agent response into a parallel
  QueryBuilder request.
- Raw agent responses are not embedded in HTL or retained in the destination URL.

## Request flow

```text
Browser                         AEM resolver                    Agent
   | POST page.discovery.json       |                            |
   | prompt + current ASC params -->|                            |
   |                                | build semantic controls    |
   |                                | POST v2 prompt/context ---->|
   |                                |<---- validated state patch |
   |                                | map patch to ASC params     |
   |<-- {version, redirectUrl} ------|                            |
   | window.location.assign         |                            |
   | GET page.html?... ------------>| normal ASC search          |
```

The POST target is rendered on the results form as
`data-asset-share-discovery-action`. The Dispatcher allows only
`POST /content/*.discovery.json`; `POST *.results.html` is not exposed.

The browser submits the current serialized filter, sort, layout, pagination, and customer
parameters. It removes the input containing the `/discovery` command and adds the reserved
`discovery.prompt` transport parameter. AEM builds the v2 semantic context from the page's Sling
Models in component render order. Command recognition is scoped to the marked discovery search-bar
input; another freeform or customer field whose legitimate value begins with `/discovery` remains
an ordinary search value.

The current page content resource is the primary model root. Core Component Experience Fragment
variations rendered by the page are expanded automatically at the reference component's exact
render-order position. This is required because ASC predicate Sling Models allocate request-scoped
QueryBuilder group IDs as they render. A reference/include component with a different external
composition mechanism can register a `DiscoveryModelRootProvider` that returns the roots rendered
by that component. Those resource paths stay inside AEM; the resolver passes only the resulting
semantic controls to the agent. Page and hidden predicates found in an external model root are
never imported as policy—the canonical page continues to own those predicates.

Hidden predicates and `PagePredicate` are never writable controls. `PagePredicate` contributes
only the configured roots needed to validate a residual semantic path. Hidden predicates and
allowed page paths are applied later by the ordinary canonical GET.

The successful path contains exactly one agent call, one full-page navigation, and one
QueryBuilder execution. Refreshing the canonical URL starts at the final GET and therefore makes
no agent call.

## Browser-to-AEM resolver contract

The endpoint is page-scoped:

```http
POST /content/asset-share-commons/en/light.discovery.json
Content-Type: application/x-www-form-urlencoded

discovery.prompt=Find+landscape+JPEGs&<current serialized ASC parameters>
```

The form serializes the current filter, sort, layout, limit, pagination, residual fulltext/path,
and customer parameters. It excludes the form field carrying the `/discovery` command so the
command cannot leak into the canonical URL. Only the `discovery.*` namespace is reserved for this
browser-to-AEM transport; customer fields literally named `prompt` or `context` remain ordinary
search state and survive reconciliation.

The endpoint is registered for `cq:Page`, the `discovery` selector, the `json` extension, and
`POST`. It adds normal Sling bindings before adapting predicate models because several ASC Sling
Models use the request and response while calculating their selected state.

On success AEM returns:

```json
{
  "version": 1,
  "redirectUrl": "/content/asset-share-commons/en/light.html?<validated ASC parameters>"
}
```

The browser verifies that the redirect is same-origin and calls `window.location.assign`. On
failure, it remains on the current page and displays the existing discovery error.

## Agent contract

AEM keeps the existing v2 request and response contract. The context contains semantic control
IDs, titles, kinds, current state, options, cardinality, and constraints. It does not contain
QueryBuilder group names, predicate parameter names, component resource paths, property paths,
hidden predicates, or HTML.

AEM sends:

```json
{
  "version": 2,
  "prompt": "Find landscape JPEGs modified in the last month",
  "context": {
    "query": {
      "fulltext": null,
      "path": null,
      "allowedPathRoots": ["/content/dam"]
    },
    "controls": [
      {
        "id": "opaque-request-control-id",
        "title": "File type",
        "kind": "choice",
        "cardinality": "many",
        "state": {"values": []},
        "options": [
          {"value": "image/jpeg", "label": "JPEG", "disabled": false}
        ]
      }
    ]
  }
}
```

Configured allowed path roots are included only to constrain an optional residual semantic path.
JCR property paths and the parameters that implement controls remain server-side. Sort fields are
represented as opaque values such as `sort-option-0`; after validation, the built-in adapter maps
that token back to the selected Sling Model option's real `orderby` value.

The agent returns a patch:

```json
{
  "version": 2,
  "query": {
    "fulltext": "landscape",
    "path": null
  },
  "controlUpdates": [
    {
      "id": "cmp-propertyvalues_123456",
      "state": {
        "values": ["image/jpeg"]
      }
    }
  ]
}
```

An omitted control keeps its current state. A mentioned control completely replaces its current
state. `values: []`, or a date range with two null bounds, explicitly clears a control.

AEM rejects the whole response for missing or extra schema fields, an unsupported version,
unknown or duplicate IDs, duplicate values, unknown or disabled options, cardinality or
constraint violations, invalid text or calendar dates, unsupported sort values, and unsafe paths.
The agent cannot provide a redirect URL.

The resolver returns only:

```json
{
  "version": 1,
  "redirectUrl": "/content/assets.html?<validated ASC parameters>"
}
```

Errors use the same version with a stable `error.code` and `error.message`. The browser stays on
the current page and shows the existing discovery error.

## Canonical parameter reconciliation

The agent result refines the current search:

- Controls omitted by the agent remain unchanged.
- Parameters for a mentioned control are completely replaced by its adapter.
- Layout, limit, unrelated manual filters, repeated customer parameters, and other customer state
  are preserved.
- `p.offset` is reset to `0`.
- Reserved `discovery.*` transport fields, authoring fields, and `agent.*` diagnostics are removed.
  Unnamespaced customer parameters, including fields named `prompt` or `context`, are preserved.
- Residual fulltext is length-validated.
- Residual fulltext is written using the page's actual `FulltextPredicate` name, including
  `ai-fulltext` when AI Search is enabled.
- Residual paths are canonicalized and constrained to the page's configured roots.
- The destination path always comes from the current AEM page.

The agent controls only semantic fields that the server described. It does not choose the page,
endpoint, URL, hidden predicates, arbitrary parameters, or QueryBuilder structure.

`PredicateUtil.isParameterizedSearchRequest` recognizes QueryBuilder parameters in a POST body.
This additional detection is restricted to `POST *.discovery.json`. Existing GET behavior,
including fulltext-only, path-only, and sort-only deep links, is unchanged. The discovery-specific
check ensures the Sling Models describe the submitted state instead of reapplying authored
defaults while the resolver builds its context.

The destination GET then enters the unchanged ASC path:

1. Predicate Sling Models read the canonical query parameters and server-render the selected
   filter values.
2. `QuerySearchProviderImpl` builds the QueryBuilder request.
   Repeated dropdown/multiselect `propertyvalues.values` and `path` form values are expanded to
   their indexed QueryBuilder equivalents so no selection is lost.
3. Page paths and hidden predicates are merged.
4. `SearchSafety`, search preprocessors, and postprocessors run normally.
5. Results render through the normal ASC results component.

## Customer filter support

The public OSGi `DiscoveryControlAdapter` SPI maps a `Predicate` model in both directions:

```java
boolean supports(Predicate predicate);

List<DiscoveryControl> describe(
    SlingHttpServletRequest request,
    Predicate predicate);

DiscoveryParameterUpdate toParameterUpdate(
    SlingHttpServletRequest request,
    Predicate predicate,
    String controlId,
    DiscoveryControlState state);
```

ASC selects the supporting adapter with the highest OSGi service ranking. A customer adapter can
therefore override a built-in adapter without replacing the resolver.

The built-in adapter supports the public ASC interfaces:

- `PropertyPredicate`, including tags and customer overlays
- `DatePredicate`, including absolute and relative dates
- `PathPredicate`
- `FreeformTextPredicate`
- `SortPredicate`, including field and direction

Checkbox subtypes use the rendered control's real cardinality. `checkbox` is many-valued, while
radio, toggle, and slider variants are one-valued. This same cardinality is enforced when the agent
response is validated and when the adapter produces canonical parameters.

A customer overlay that continues to implement one of these interfaces works automatically,
including a property filter backed by a custom JCR property. The property path remains inside the
Sling Model and generated ASC parameters; it is not sent to the agent.

A standard-interface implementation must also expose the mapping information required by that
interface. In particular, `DatePredicate.getProperty()` must return a nonblank property.
The method has a default `null` implementation for binary compatibility with older customer
bundles; those older implementations are deliberately treated as unsupported until they override
the method or register a custom adapter. This prevents a valid-looking date control from producing
an incomplete QueryBuilder predicate.

A nonstandard predicate model must expose `Predicate` as a Sling Model adapter type and register a
`DiscoveryControlAdapter`; a complete standard-interface overlay needs neither step. The custom
adapter's descriptor should expose semantic values only. Its parameter update must remove every
old parameter owned by the mentioned control and add the complete replacement. It must not map
hidden or page predicates.

This means a customer's search-rail filter for a custom JCR property works without additional
discovery configuration when it continues to use `PropertyPredicate`. The agent receives the
filter's semantic label and option values; AEM retains the custom property path and translates the
validated selection back into that component's normal request parameters.

Unsupported predicates remain part of the serialized baseline search but are not exposed as
agent-writable controls. Their current parameters are preserved unless another supported control
owns and replaces those same parameters.

### Custom adapter rules

A customer adapter should:

- Return `true` only for the predicate models it owns.
- Generate request-unique, stable-within-the-request control IDs.
- Describe the model's current server-resolved state rather than authored defaults alone.
- Expose only semantic labels, kinds, options, cardinality, and constraints.
- Treat `toParameterUpdate` as complete replacement for the mentioned control.
- Return every old owned parameter in `removeParameters`.
- Add only canonical ASC parameters derived from the validated state, using
  `Map<String, List<String>>` so repeated request names remain representable.
- Reproduce the same parameter names and value order that the component's manual form submission
  produces; do not substitute a merely equivalent QueryBuilder spelling.
- Never expose or make writable a hidden predicate or page predicate.

Adapters are dynamic OSGi services. The resolver chooses the supporting service with the highest
`service.ranking`, then the lowest service ID as a deterministic tie-breaker. This permits a
customer adapter to override a built-in adapter.

The built-in freeform adapter exposes the control's raw text as one semantic value. It re-emits all
authored delimiter parameters and validates the complete raw value with the same min/max/pattern
constraints as the HTML input. It does not split and validate individual tokens.

ASC sort parameters are global. If a page renders the same sort component more than once, only the
first page-order instance is described to the agent; later instances remain equivalent views of
the same `orderby` state rather than creating duplicate or conflicting controls. Sort option
values in the agent contract are opaque request-local tokens; labels remain semantic, and raw JCR
or QueryBuilder sort fields never leave AEM.

### External rendered model roots

The public OSGi `DiscoveryModelRootProvider` SPI is for search controls rendered from a component
tree outside the current page content subtree:

```java
Collection<Resource> getModelRoots(
    SlingHttpServletRequest request,
    Page currentPage,
    Resource renderedComponent);
```

ASC invokes this customer-implemented (`@ConsumerType`) SPI for each component as it walks the
current page in repository/render order. Returned roots are expanded immediately before the
component's local children, so controls rendered before and after an include receive the same group
IDs as the canonical GET. ASC de-duplicates nested or repeated roots, limits the traversal to 20
total roots, and detects reference cycles. The built-in provider resolves localized Core Component
Experience Fragment variations; nested fragments are followed by the resolver using the same
ordered traversal. Customer reference/include components can register a provider for their own
composition mechanism; their standard predicate interfaces then use the same built-in control
adapters.

## Authentication and configuration

`DiscoveryAgentClientImpl` retains the existing endpoint, timeout, response-size, and
`AccessTokenProvider` configuration. AEM opens a short-lived service resource resolver, obtains
the configured IMS bearer token, calls the endpoint, and closes the resolver. Agent credentials
are never exposed to the browser.

The OSGi PID is:

```text
com.adobe.aem.commons.assetshare.search.discovery.impl.DiscoveryAgentClientImpl
```

Supported properties are:

| Property | Purpose | Default |
| --- | --- | --- |
| `agent.endpoint` | Absolute HTTP(S) v2 discovery endpoint | empty |
| `agent.authorization` | Optional complete Authorization header when no configured IMS provider is available | empty |
| `agent.headers` | Optional additional `Name: Value` request headers | empty |
| `ims.provider.name` | `AccessTokenProvider` name used for a bearer token | `Asset Compute` |
| `http.timeout` | Connect, pool, and response timeout in milliseconds | `30000` |
| `max.response.bytes` | Maximum accepted response body | `1048576` |

The `all-cloud` package configures the following discovery endpoint for both author and publish:

```text
https://aem-assets-adobe-aem-experience-advisory-agent-depl-d06424.stage.cloud.adobe.io/transform-query
```

The packaged configurations set `agent.endpoint` and explicitly leave `ims.provider.name`
empty so the stage endpoint is called without the default `Asset Compute` provider. They do
not ship an authorization header, request headers, or credentials. Customer environments can
override the IMS provider and authentication properties with higher-priority run-mode
configuration. If an environment removes or overrides the endpoint with an empty value:

- the results form carries the current discovery contract marker but no resolver action;
- the search bar does not advertise the `/discovery` command; and
- direct resolver calls return the stable `not_configured` error without calling an agent.

Existing results-component overlays that predate the discovery action can derive
`page.discovery.json` from their same-page form action. The current base component emits
`data-asset-share-discovery-contract="1"` so a missing configured action never enters that
compatibility path. An explicit `data-asset-share-discovery-enabled="false"` also disables the
fallback in customer overlays.

Override the packaged endpoint in the target environment when a different deployment is required:

```json
{
  "agent.endpoint": "https://customer-specific-agent.example.com/transform-query"
}
```

Environment-specific endpoint or authentication values should be supplied through the
environment's OSGi configuration. Do not commit access tokens.

When `ims.provider.name` resolves to an `AccessTokenProvider`, configure the Sling subservice
`discovery-ims-client` to an appropriate environment-owned service principal. ASC intentionally
does not ship a mapping to Adobe's environment-specific `nui-process-service`. When no matching
IMS provider is configured, the optional `agent.authorization` value is used.

The `all-cloud` package recursively includes the core bundle, UI applications, content,
configuration, and Dispatcher packages. Build and install it on a local author with:

```bash
mvn -pl all -am -Pcloud,autoInstallSinglePackage -DskipTests install
```

The Dispatcher rule is intentionally narrow:

```text
POST /content/* with selector discovery and extension json
```

The former broad `POST *.results.html` allowance is not required.

Production traffic must apply an environment-appropriate request-rate policy for
`POST *.discovery.json` at the CDN/WAF. A JVM-local client-IP limiter is deliberately not used
because publish nodes normally see proxy addresses and because a cluster-local counter is not an
authoritative edge limit. AEM still enforces defense-in-depth bounds before an outbound call:

- at most 512 submitted parameter values;
- parameter names up to 256 characters and values up to 4096 characters;
- at most 64 KiB of submitted parameter characters;
- at most 100 controls and 500 options per control;
- at most 128 KiB of serialized agent context; and
- at most 16 KiB for the resulting canonical URL.

Requests exceeding these limits fail without calling the agent.

## Failure behavior

The resolver returns stable JSON errors and never queries or navigates on failure. Error classes
include invalid or oversized input or page state, missing configuration, adapter or model-root
provider failure, unavailable or unsuccessful agent response, invalid agent response, mapping
failure, and URL-encoding failure. Responses use `Cache-Control: no-store`. A search-only page with
no adapter-backed rail controls remains valid: its v2 context has an empty `controls` array and the
agent can still resolve residual fulltext/path. A `cq:Page` with no ASC predicate or search-bar
model is rejected before any outbound agent call.

Unexpected exceptions are returned as `internal_error`; agent response bodies, credentials, and
semantic context are not returned to the browser.

## Implementation map

- `DiscoveryPageServlet` owns the page-scoped HTTP contract and stable error envelope.
- `DiscoveryResolverImpl` visits page and included models in render order, selects adapters, calls
  the agent, validates the patch, reconciles parameters, and constructs the same-page URL.
- `DiscoveryResponseValidator` strictly validates the v2 response before mapping.
- `DefaultDiscoveryControlAdapter` supports the standard ASC predicate interfaces.
- `DiscoveryControlAdapter` is the public customer extension SPI.
- `DiscoveryModelRootProvider` supplies rendered external component roots; the built-in
  `ExperienceFragmentDiscoveryModelRootProvider` follows Core Component Experience Fragments.
- `DiscoveryAgentClientImpl` owns authentication, HTTP limits, and the v2 agent call.
- `DiscoveryConfigurationImpl` exposes the opt-in configured state to HTL without exposing the
  endpoint or credentials.
- `search-form.js` serializes current state and posts it to the resolver.
- `search.js` maintains loading/error behavior and performs same-origin navigation.
- `results.html` renders the page-specific `.discovery.json` action.
- `filters.any` exposes only the narrow resolver POST through Dispatcher.

## Verification

Deterministic tests cover built-in adapters, service ranking, patch behavior, response validation,
POST parameter detection, servlet errors, canonical URL generation, repeated customer parameters,
exact checkbox/radio/dropdown/multiselect parameter shapes, raw freeform semantics, incomplete date
models, toggle/slider cardinality, opaque sort mapping, AI Search, search-only pages, Experience
Fragment render order, repeated QueryBuilder multiselect values, duplicate sort renderings, hidden
predicates with custom query processors, non-search-page rejection, opt-in rendering, overlay
fallback, command-input scoping, and browser navigation/error behavior. The
deterministic browser scripts run automatically from the `ui.apps` Maven `test` phase through
`npm test`.

The direct live-agent contract test remains opt-in:

```bash
ASC_DISCOVERY_AGENT_URL="https://<live-endpoint>" \
ASC_DISCOVERY_PROMPT="Find landscape JPEGs modified in the last month" \
node ui.apps/src/test/javascript/discovery-agent-contract.test.js
```

It validates deterministic v2 properties and does not run in normal CI.

The AEM end-to-end check must go through `.discovery.json`, not only call the agent directly:

1. Open an ASC page with standard, custom-property, and hidden predicates.
2. Submit a discovery prompt while existing filters are selected.
3. Confirm the response contains a same-page URL and resets `p.offset` to zero.
4. Navigate to the URL and confirm the selected controls are server-rendered.
5. Confirm visible agent selections, custom query processors, page paths, and hidden predicates
   all constrain results.
6. Refresh and confirm the selected state and results are identical without another agent call.
7. Verify Back and shared canonical URLs behave like manual ASC searches.

The implementation was exercised locally with the configured live endpoint using standard image,
relative-date, orientation, and customer-style custom JCR-property filters. The canonical GET
server-rendered the resulting selections, and returned assets continued to satisfy both custom
property and hidden predicates.

## Acceptance boundary

The integration is complete when the final page is indistinguishable from the equivalent manual
search in its selected filters, results, URL, refresh, Back, and sharing behavior. Standard ASC
predicate-interface overlays with complete mapping information require no discovery-specific
configuration. An older date implementation without `getProperty()`, or any predicate with a
nonstandard model or QueryBuilder shape, is outside the built-in mapping and requires the missing
interface mapping or a `DiscoveryControlAdapter`.
