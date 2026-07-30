/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adobe.aem.commons.assetshare.search.providers.impl;

import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.impl.DatePredicateImpl;
import com.adobe.aem.commons.assetshare.search.UnsafeSearchException;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentClient;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentResponse;
import com.adobe.aem.commons.assetshare.search.impl.predicateevaluators.PropertyValuesPredicateEvaluator;
import com.adobe.aem.commons.assetshare.search.providers.SearchProvider;
import com.adobe.aem.commons.assetshare.search.results.Results;
import com.adobe.aem.commons.assetshare.util.ComponentModelVisitor;
import com.day.cq.search.eval.JcrPropertyPredicateEvaluator;
import com.day.cq.search.eval.PathPredicateEvaluator;
import com.day.cq.search.eval.RangePropertyPredicateEvaluator;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.osgi.framework.Constants.SERVICE_RANKING;

/**
 * A {@link SearchProvider} that resolves natural-language ("discovery") search requests end-to-end
 * in a single round trip:
 *
 * <ol>
 *     <li>Calls the discovery agent with the submitted {@code prompt} and {@code context}
 *         (context describes the search rail's available controls, exactly as submitted by the
 *         browser today via {@code discovery-controls.js}).</li>
 *     <li>Translates the agent's {@code controlUpdates[]} back into real QueryBuilder predicate
 *         parameters, by resolving each control id to the actual predicate component resource
 *         rendered on the current page (see {@link #resolvePredicatesById(SlingHttpServletRequest)}).</li>
 *     <li>Delegates the translated parameters to the existing {@link QuerySearchProviderImpl} via a
 *         lightweight request wrapper, so that page/hidden predicate merging
 *         ({@code PagePredicate.getPredicateGroup()}), {@code SearchSafety} checks, and any
 *         {@code QuerySearchPreProcessor}/{@code QuerySearchPostProcessor} implementations already
 *         registered by the instance apply exactly as they would for a manually-driven rail search
 *         -- by construction (same code path), not by coincidence.</li>
 * </ol>
 *
 * <b>Scope of this first pass:</b> control resolution supports the predicate families that back
 * {@code property}/{@code propertyvalues} (checkbox/radio/select facets) and
 * {@code daterange}/{@code relativedaterange} (date facets) components, the top-level
 * {@code query.fulltext} and {@code query.path} overrides, and the two synthetic sort controls
 * ({@link #SORT_ORDERBY_CONTROL_ID}/{@link #SORT_DIRECTION_CONTROL_ID}), which map directly onto
 * QueryBuilder's {@code orderby}/{@code orderby.sort} parameters. Any other resolved predicate kind
 * (tags, nested path predicates, freeform text, hidden/page predicates) is deliberately left
 * untranslated for now -- it is logged and skipped rather than silently ignored, and is expected to
 * be filled in incrementally as those control kinds are exercised in practice.
 */
@Component(
        service = SearchProvider.class,
        property = {
                SERVICE_RANKING + ":Integer=" + 100
        }
)
public class DiscoverySearchProviderImpl implements SearchProvider {
    private static final Logger log = LoggerFactory.getLogger(DiscoverySearchProviderImpl.class);

    static final String PARAM_PROMPT = "prompt";
    static final String PARAM_CONTEXT = "context";

    /**
     * Synthetic control ids the client (search-form.js#getDiscoverySortControls) uses for the
     * "sort by"/"sort direction" dropdowns. These are NOT backed by a rendered {@link Predicate}
     * component -- they map directly onto QueryBuilder's own {@code orderby}/{@code orderby.sort}
     * request parameters -- so they must be special-cased here rather than resolved via
     * {@link #resolvePredicatesById(SlingHttpServletRequest)}/{@link #applyControlUpdate}.
     */
    static final String SORT_ORDERBY_CONTROL_ID = "__asset_share_discovery_sort_orderby";
    static final String SORT_DIRECTION_CONTROL_ID = "__asset_share_discovery_sort_direction";

    /**
     * Key under which the agent's raw (v2) JSON response is stashed in
     * {@link Results#getAdditionalData()}, so that the results-rendering HTL can embed it in the
     * response fragment for {@code search.js} to pick up in the same round trip.
     */
    static final String ADDITIONAL_DATA_KEY_AGENT_RESPONSE = "discoveryAgentResponse";

    private static final String DEFAULT_LOWER_OPERATION = ">=";

    @Reference
    private transient DiscoveryAgentClient discoveryAgentClient;

    @Reference
    private transient QuerySearchProviderImpl querySearchProvider;

    @Reference
    private transient ModelFactory modelFactory;

    @Override
    public boolean accepts(final SlingHttpServletRequest request) {
        return StringUtils.isNotBlank(request.getParameter(PARAM_PROMPT))
                && StringUtils.isNotBlank(request.getParameter(PARAM_CONTEXT))
                && discoveryAgentClient.isConfigured();
    }

    @Override
    public Results getResults(final SlingHttpServletRequest request) throws UnsafeSearchException, RepositoryException {
        final String prompt = request.getParameter(PARAM_PROMPT);
        final String context = request.getParameter(PARAM_CONTEXT);

        DiscoveryAgentResponse agentResponse;
        try {
            agentResponse = discoveryAgentClient.call(prompt, context);
        } catch (IOException e) {
            log.error("Discovery agent call failed for prompt [{}]", prompt, e);
            return Results.ERRING_RESULTS;
        }

        if (!agentResponse.isSuccessful()) {
            log.warn("Discovery agent returned a non-successful status [{}] for prompt [{}]",
                    agentResponse.getStatus(), prompt);
            return Results.ERRING_RESULTS;
        }

        final JsonObject responseJson;
        try {
            responseJson = JsonParser.parseString(
                    new String(agentResponse.getBody(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            log.error("Discovery agent response for prompt [{}] was not a valid JSON object", prompt, e);
            return Results.ERRING_RESULTS;
        }

        final Map<String, Predicate> predicatesById = resolvePredicatesById(request);
        final Map<String, String> translatedParams = translate(responseJson, predicatesById);

        final SlingHttpServletRequest wrappedRequest = new DiscoveryTranslatedRequestWrapper(request, translatedParams);

        final Results results = querySearchProvider.getResults(wrappedRequest);

        // Stash the agent's raw (v2) response -- verbatim -- in the extension point Results already
        // exposes for this exact purpose. This lets the results-rendering HTL embed it in the response
        // fragment so the single round trip still carries everything search.js needs to sync the rail
        // UI (checkboxes, query summary) via its existing, unchanged DiscoveryControls.validateResponse
        // / applyDiscoveryControlUpdates client logic -- without a second network call to the agent
        // or to a separate endpoint.
        //
        // Results.ERRING_RESULTS is a shared static singleton (not per-request), so it must never be
        // mutated here -- only stash additional data on a genuine, per-request Results instance.
        if (results != Results.ERRING_RESULTS) {
            results.getAdditionalData().put(ADDITIONAL_DATA_KEY_AGENT_RESPONSE, responseJson.toString());
        }

        return results;
    }

    /**
     * Walks the current page's component tree and resolves every rendered predicate component to
     * its Sling Model, keyed by the exact same id scheme {@code AbstractPredicate#getId()} already
     * uses ({@code "cmp-" + getName() + "_" + resourcePath.hashCode()}). This guarantees that a
     * {@code controlUpdates[].id} value produced by the browser (via
     * {@code data-asset-share-predicate-id}, ultimately sourced from that same {@code getId()})
     * resolves to exactly the resource the user actually saw/interacted with.
     */
    Map<String, Predicate> resolvePredicatesById(final SlingHttpServletRequest request) {
        final Map<String, Predicate> byId = new HashMap<>();

        final ResourceResolver resourceResolver = request.getResourceResolver();
        final PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
        final Page currentPage = pageManager == null ? null : pageManager.getContainingPage(request.getResource());

        if (currentPage == null) {
            log.warn("Unable to resolve the current page for the discovery search request at [{}]",
                    request.getResource().getPath());
            return byId;
        }

        final ComponentModelVisitor<Predicate> visitor =
                new ComponentModelVisitor<>(request, modelFactory, Predicate.class);
        visitor.accept(currentPage.getContentResource());

        for (final Predicate predicate : visitor.getModels()) {
            try {
                final String id = predicate.getId();
                if (StringUtils.isNotBlank(id)) {
                    byId.put(id, predicate);
                }
            } catch (RuntimeException e) {
                // Hidden/page predicates (among others) intentionally do not support getId()/getName()
                // outside of their own internal server-side merge logic; these are not addressable
                // discovery controls and are safely skipped here.
                log.trace("Skipping a predicate resource that does not support getId(): {}", e.getMessage());
            }
        }

        return byId;
    }

    /**
     * Translates the agent's {@code query.*} overrides and {@code controlUpdates[]} into real
     * QueryBuilder request parameters.
     */
    Map<String, String> translate(final JsonObject responseJson, final Map<String, Predicate> predicatesById) {
        final Map<String, String> params = new LinkedHashMap<>();

        final JsonObject query = responseJson.has("query") && responseJson.get("query").isJsonObject()
                ? responseJson.getAsJsonObject("query")
                : null;

        if (query != null) {
            final String fulltext = stringOrNull(query.get("fulltext"));
            if (StringUtils.isNotBlank(fulltext)) {
                params.put("fulltext", fulltext);
            }

            final String path = stringOrNull(query.get("path"));
            if (StringUtils.isNotBlank(path)) {
                params.put(PathPredicateEvaluator.PATH, path);
            }
        }

        final JsonArray controlUpdates = responseJson.has("controlUpdates") && responseJson.get("controlUpdates").isJsonArray()
                ? responseJson.getAsJsonArray("controlUpdates")
                : new JsonArray();

        int group = 0;
        for (final JsonElement element : controlUpdates) {
            if (!element.isJsonObject()) {
                continue;
            }

            final JsonObject update = element.getAsJsonObject();
            final String id = stringOrNull(update.get("id"));
            final JsonObject state = update.has("state") && update.get("state").isJsonObject()
                    ? update.getAsJsonObject("state")
                    : new JsonObject();

            if (StringUtils.isBlank(id)) {
                continue;
            }

            if (SORT_ORDERBY_CONTROL_ID.equals(id) || SORT_DIRECTION_CONTROL_ID.equals(id)) {
                applySortControlUpdate(params, id, state);
                continue;
            }

            final Predicate predicate = predicatesById.get(id);
            if (predicate == null) {
                log.warn("Discovery control update references an unknown/unresolvable control id [{}]; skipping", id);
                continue;
            }

            final String groupName = group + "_group";
            final boolean applied = applyControlUpdate(params, groupName, predicate, state);
            if (applied) {
                group++;
            }
        }

        return params;
    }

    boolean applyControlUpdate(final Map<String, String> params, final String groupName,
                                        final Predicate predicate, final JsonObject state) {
        if (predicate instanceof PropertyPredicate) {
            return applyPropertyPredicateUpdate(params, groupName, (PropertyPredicate) predicate, state);
        }

        if (predicate instanceof DatePredicateImpl) {
            return applyDatePredicateUpdate(params, groupName, (DatePredicateImpl) predicate, state);
        }

        log.warn("Discovery control id [{}] resolved to an unsupported predicate kind [{}]; skipping. "
                        + "This predicate family is not yet translated by DiscoverySearchProviderImpl.",
                predicate.getId(), predicate.getClass().getSimpleName());
        return false;
    }

    boolean applyPropertyPredicateUpdate(final Map<String, String> params, final String groupName,
                                                  final PropertyPredicate predicate, final JsonObject state) {
        final List<String> values = readValues(state);
        if (values.isEmpty()) {
            return false;
        }

        final String prefix = groupName + "." + predicate.getName() + ".";

        params.put(prefix + JcrPropertyPredicateEvaluator.PROPERTY, predicate.getProperty());
        if (predicate.hasOperation() && StringUtils.isNotBlank(predicate.getOperation())) {
            params.put(prefix + JcrPropertyPredicateEvaluator.OPERATION, predicate.getOperation());
        }

        if (PropertyValuesPredicateEvaluator.PREDICATE_NAME.equals(predicate.getName())) {
            int valueIndex = 0;
            for (final String value : values) {
                params.put(prefix + valueIndex + "_" + PropertyValuesPredicateEvaluator.VALUES, value);
                valueIndex++;
            }
        } else {
            params.put(prefix + JcrPropertyPredicateEvaluator.VALUE, values.get(0));
        }

        return true;
    }

    boolean applyDatePredicateUpdate(final Map<String, String> params, final String groupName,
                                              final DatePredicateImpl predicate, final JsonObject state) {
        final String lowerBound = stringOrNull(state.get("lowerBound"));
        final String upperBound = stringOrNull(state.get("upperBound"));

        if (StringUtils.isBlank(lowerBound) && StringUtils.isBlank(upperBound)) {
            return false;
        }

        final String prefix = groupName + "." + predicate.getName() + ".";
        params.put(prefix + RangePropertyPredicateEvaluator.PROPERTY, predicate.getProperty());

        if (StringUtils.isNotBlank(lowerBound)) {
            params.put(prefix + RangePropertyPredicateEvaluator.LOWER_BOUND, lowerBound);
            // Absolute date ranges require an explicit operation; relative date ranges
            // (tokens like "-14d") do not use lowerOperation at all.
            if ("daterange".equals(predicate.getName())) {
                params.put(prefix + RangePropertyPredicateEvaluator.LOWER_OPERATION, DEFAULT_LOWER_OPERATION);
            }
        }

        if (StringUtils.isNotBlank(upperBound)) {
            params.put(prefix + RangePropertyPredicateEvaluator.UPPER_BOUND, upperBound);
        }

        return true;
    }

    /**
     * Applies an agent-driven sort control update ({@link #SORT_ORDERBY_CONTROL_ID} /
     * {@link #SORT_DIRECTION_CONTROL_ID}) directly onto QueryBuilder's own
     * {@code orderby}/{@code orderby.sort} request parameters, mirroring what
     * {@code applyDiscoverySortControlUpdate} does client-side (search-form.js). Unlike the
     * predicate-backed control families above, these two ids have no corresponding rendered
     * {@link Predicate} component, so they cannot be resolved via
     * {@link #resolvePredicatesById(SlingHttpServletRequest)} -- without this special case the
     * update would be silently dropped and the executed search would run with a stale sort order
     * while the sort dropdown UI (updated client-side, independently, from the same response)
     * shows the agent's intended sort -- the two would visibly disagree.
     */
    boolean applySortControlUpdate(final Map<String, String> params, final String id, final JsonObject state) {
        final List<String> values = readValues(state);
        if (values.isEmpty()) {
            return false;
        }

        final String value = values.get(0);
        if (SORT_ORDERBY_CONTROL_ID.equals(id)) {
            params.put(com.day.cq.search.Predicate.ORDER_BY, value);
        } else {
            params.put(com.day.cq.search.Predicate.ORDER_BY + "." + com.day.cq.search.Predicate.PARAM_SORT, value);
        }

        return true;
    }

    List<String> readValues(final JsonObject state) {
        final List<String> values = new ArrayList<>();

        if (state.has("values") && state.get("values").isJsonArray()) {
            for (final JsonElement value : state.getAsJsonArray("values")) {
                final String stringValue = stringOrNull(value);
                if (StringUtils.isNotBlank(stringValue)) {
                    values.add(stringValue);
                }
            }
        } else {
            final String singleValue = stringOrNull(state.get("value"));
            if (StringUtils.isNotBlank(singleValue)) {
                values.add(singleValue);
            }
        }

        return values;
    }

    private String stringOrNull(final JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    /**
     * A minimal request wrapper that overlays the translated discovery QueryBuilder parameters on
     * top of the incoming request's parameters, so that {@code QuerySearchProviderImpl.getParams()}
     * (which reads exclusively via {@link SlingHttpServletRequest#getRequestParameterMap()}) sees
     * them as if they had been submitted directly -- meaning page/hidden predicate merging and any
     * pre/post query processors apply completely unmodified.
     */
    static final class DiscoveryTranslatedRequestWrapper extends SlingHttpServletRequestWrapper {
        private final RequestParameterMap requestParameterMap;

        DiscoveryTranslatedRequestWrapper(final SlingHttpServletRequest wrapped, final Map<String, String> translatedParams) {
            super(wrapped);

            final Map<String, RequestParameter[]> merged = new LinkedHashMap<>();
            for (final Map.Entry<String, RequestParameter[]> entry : wrapped.getRequestParameterMap().entrySet()) {
                if (!PARAM_PROMPT.equals(entry.getKey()) && !PARAM_CONTEXT.equals(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
            for (final Map.Entry<String, String> entry : translatedParams.entrySet()) {
                merged.put(entry.getKey(), new RequestParameter[]{new StringRequestParameter(entry.getKey(), entry.getValue())});
            }

            this.requestParameterMap = new SimpleRequestParameterMap(merged);
        }

        @Override
        public RequestParameterMap getRequestParameterMap() {
            return requestParameterMap;
        }

        @Override
        public RequestParameter getRequestParameter(final String name) {
            return requestParameterMap.getValue(name);
        }

        @Override
        public RequestParameter[] getRequestParameters(final String name) {
            return requestParameterMap.getValues(name);
        }
    }

    private static final class SimpleRequestParameterMap extends AbstractMap<String, RequestParameter[]>
            implements RequestParameterMap {
        private final Map<String, RequestParameter[]> backing;

        private SimpleRequestParameterMap(final Map<String, RequestParameter[]> backing) {
            this.backing = backing;
        }

        @Override
        public RequestParameter[] getValues(final String name) {
            return backing.get(name);
        }

        @Override
        public RequestParameter getValue(final String name) {
            final RequestParameter[] values = backing.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override
        public java.util.Set<Entry<String, RequestParameter[]>> entrySet() {
            return backing.entrySet();
        }
    }

    private static final class StringRequestParameter implements RequestParameter {
        private final String name;
        private final String value;

        private StringRequestParameter(final String name, final String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isFormField() {
            return true;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public long getSize() {
            return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public byte[] get() {
            return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(get());
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public String getString() {
            return value;
        }

        @Override
        public String getString(final String encoding) throws UnsupportedEncodingException {
            return new String(get(), encoding);
        }
    }
}
