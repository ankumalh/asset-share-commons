/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.components.predicates.HiddenPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.DatePredicate;
import com.adobe.aem.commons.assetshare.components.predicates.FreeformTextPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.FulltextPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.PagePredicate;
import com.adobe.aem.commons.assetshare.components.predicates.PathPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.SortPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.TagsPredicate;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentClient;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentResponse;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControl;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlAdapter;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlOption;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryModelRootProvider;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryParameterUpdate;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(service = DiscoveryResolver.class)
public class DiscoveryResolverImpl implements DiscoveryResolver {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryResolverImpl.class);
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final String PARAM_PROMPT = "discovery.prompt";
    private static final String PARAM_CONTEXT = "discovery.context";
    private static final String PARAM_OFFSET = "p.offset";
    private static final String PARAM_FULLTEXT = "fulltext";
    private static final String PARAM_AI_FULLTEXT = "ai-fulltext";
    private static final int MAX_CONTROLS = 100;
    private static final int MAX_OPTIONS_PER_CONTROL = 1000;
    private static final int MAX_MODEL_ROOTS = 20;
    private static final int MAX_CONTEXT_BYTES = 128 * 1024;
    private static final int MAX_REDIRECT_URL_LENGTH = 16 * 1024;
    private static final String[] FULLTEXT_RESOURCE_TYPES = {
            "asset-share-commons/components/search/search-bar"
    };
    private static final Map<String, Class<? extends Predicate>> PREDICATE_MODEL_CLASSES;

    static {
        final Map<String, Class<? extends Predicate>> modelClasses = new LinkedHashMap<>();
        modelClasses.put("asset-share-commons/components/search/results", PagePredicate.class);
        modelClasses.put("asset-share-commons/components/search/hidden", HiddenPredicate.class);
        modelClasses.put("asset-share-commons/components/search/tags", TagsPredicate.class);
        modelClasses.put("asset-share-commons/components/search/property", PropertyPredicate.class);
        modelClasses.put("asset-share-commons/components/search/date-range", DatePredicate.class);
        modelClasses.put("asset-share-commons/components/search/path", PathPredicate.class);
        modelClasses.put("asset-share-commons/components/search/freeform-text", FreeformTextPredicate.class);
        modelClasses.put("asset-share-commons/components/search/sort", SortPredicate.class);
        PREDICATE_MODEL_CLASSES = Collections.unmodifiableMap(modelClasses);
    }

    private static final class AdapterEntry {
        private final DiscoveryControlAdapter adapter;
        private final int ranking;
        private final long serviceId;

        private AdapterEntry(final DiscoveryControlAdapter adapter, final Map<String, Object> properties) {
            this.adapter = adapter;
            final Object rankingValue = properties.get(Constants.SERVICE_RANKING);
            this.ranking = rankingValue instanceof Number ? ((Number) rankingValue).intValue() : 0;
            final Object serviceIdValue = properties.get(Constants.SERVICE_ID);
            this.serviceId = serviceIdValue instanceof Number ? ((Number) serviceIdValue).longValue() : Long.MAX_VALUE;
        }
    }

    private static final class Binding {
        private final Predicate predicate;
        private final DiscoveryControlAdapter adapter;

        private Binding(final Predicate predicate, final DiscoveryControlAdapter adapter) {
            this.predicate = predicate;
            this.adapter = adapter;
        }
    }

    private static final class DiscoveredModels {
        private final List<Predicate> predicates = new ArrayList<>();
        private final List<FulltextPredicate> fulltextPredicates = new ArrayList<>();
        private final List<Resource> roots = new ArrayList<>();
        private final Set<String> rootPaths = new LinkedHashSet<>();
    }

    @Reference
    private transient DiscoveryAgentClient discoveryAgentClient;

    @Reference
    private transient ModelFactory modelFactory;

    private final List<AdapterEntry> adapterEntries = new CopyOnWriteArrayList<>();
    private final List<DiscoveryModelRootProvider> modelRootProviders = new CopyOnWriteArrayList<>();
    private final DiscoveryResponseValidator validator = new DiscoveryResponseValidator();

    @Reference(
            service = DiscoveryControlAdapter.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    protected void bindDiscoveryControlAdapter(final DiscoveryControlAdapter adapter,
                                               final Map<String, Object> properties) {
        adapterEntries.add(new AdapterEntry(adapter, properties));
        adapterEntries.sort(Comparator
                .comparingInt((AdapterEntry entry) -> entry.ranking).reversed()
                .thenComparingLong(entry -> entry.serviceId));
    }

    protected void unbindDiscoveryControlAdapter(final DiscoveryControlAdapter adapter) {
        adapterEntries.removeIf(entry -> entry.adapter == adapter);
    }

    @Reference(
            service = DiscoveryModelRootProvider.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    protected void bindDiscoveryModelRootProvider(final DiscoveryModelRootProvider provider) {
        modelRootProviders.add(provider);
    }

    protected void unbindDiscoveryModelRootProvider(final DiscoveryModelRootProvider provider) {
        modelRootProviders.remove(provider);
    }

    @Override
    public boolean isConfigured() {
        return discoveryAgentClient.isConfigured();
    }

    @Override
    public DiscoveryResolution resolve(final SlingHttpServletRequest request, final String prompt)
            throws DiscoveryResolutionException {
        if (!isConfigured()) {
            throw failure(503, "not_configured", "Discovery search is not configured.");
        }

        final Page currentPage = getCurrentPage(request);
        if (currentPage == null) {
            throw failure(400, "invalid_page", "The discovery request is not associated with a page.");
        }

        final DiscoveredModels discoveredModels = discoverModels(request, currentPage);
        final List<Predicate> predicateModels = discoveredModels.predicates;
        final List<FulltextPredicate> fulltextModels = discoveredModels.fulltextPredicates;
        if (fulltextModels.isEmpty()
                && predicateModels.stream().allMatch(predicate -> predicate instanceof HiddenPredicate)) {
            throw failure(404, "not_search_page",
                    "The page does not contain an ASC search model.");
        }
        final String fulltextParameterName = fulltextParameterName(fulltextModels);

        final List<DiscoveryControl> controls = new ArrayList<>();
        final Map<String, Binding> bindings = new LinkedHashMap<>();
        final List<String> allowedPathRoots = new ArrayList<>();
        boolean sortDescribed = false;

        for (final Predicate predicate : predicateModels) {
            if (predicate instanceof PagePredicate) {
                final List<String> paths = ((PagePredicate) predicate).getPaths();
                if (paths != null) {
                    paths.stream()
                            .filter(StringUtils::isNotBlank)
                            .filter(path -> !allowedPathRoots.contains(path))
                            .forEach(allowedPathRoots::add);
                }
                continue;
            }
            if (predicate instanceof HiddenPredicate) {
                continue;
            }
            if (!predicate.isReady()) {
                continue;
            }
            if (predicate instanceof SortPredicate && sortDescribed) {
                // Sort controls all own the same global orderby parameters. The first page-order
                // component is the canonical descriptor; later renderings are equivalent views.
                continue;
            }

            final DiscoveryControlAdapter adapter = findAdapter(predicate);
            if (adapter == null) {
                continue;
            }

            final List<DiscoveryControl> described;
            try {
                described = adapter.describe(request, predicate);
            } catch (RuntimeException e) {
                throw failure(500, "adapter_failed", "A discovery control adapter failed.", e);
            }
            if (described == null) {
                throw failure(500, "adapter_failed",
                        "A discovery adapter did not produce control descriptors.");
            }
            for (final DiscoveryControl control : described) {
                if (control == null || control.getKind() == null || control.getState() == null
                        || StringUtils.isBlank(control.getId())
                        || StringUtils.isBlank(control.getTitle())) {
                    throw failure(500, "adapter_failed",
                            "A discovery adapter produced an invalid control descriptor.");
                }
                if (bindings.containsKey(control.getId())) {
                    throw failure(500, "duplicate_control", "A discovery adapter produced a duplicate control id.");
                }
                if (controls.size() >= MAX_CONTROLS) {
                    throw failure(413, "context_too_large",
                            "The page has too many discovery-compatible search controls.");
                }
                if (control.getOptions().size() > MAX_OPTIONS_PER_CONTROL) {
                    throw failure(413, "context_too_large",
                            "A discovery-compatible search control has too many options.");
                }
                controls.add(control);
                bindings.put(control.getId(), new Binding(predicate, adapter));
            }
            if (predicate instanceof SortPredicate && !described.isEmpty()) {
                sortDescribed = true;
            }
        }

        final String context = createContext(
                request, controls, allowedPathRoots, fulltextParameterName);
        if (context.getBytes(StandardCharsets.UTF_8).length > MAX_CONTEXT_BYTES) {
            throw failure(413, "context_too_large", "The discovery context is too large.");
        }
        final DiscoveryAgentResponse agentResponse;
        try {
            agentResponse = discoveryAgentClient.call(prompt, context);
        } catch (IOException e) {
            throw failure(502, "agent_unavailable", "The discovery agent could not be reached.", e);
        }
        if (agentResponse == null) {
            log.warn("Discovery agent returned no response");
            throw failure(502, "agent_error", "The discovery agent returned an unsuccessful response.");
        }
        if (!agentResponse.isSuccessful()) {
            log.warn("Discovery agent returned HTTP [{}] with content type [{}]",
                    agentResponse.getStatus(), agentResponse.getContentType());
            throw failure(502, "agent_error", "The discovery agent returned an unsuccessful response.");
        }

        final JsonObject responseJson;
        try {
            responseJson = JsonParser.parseString(
                    new String(agentResponse.getBody(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw failure(502, "invalid_agent_response", "The discovery agent returned invalid JSON.", e);
        }

        final DiscoveryValidatedResponse validated;
        try {
            validated = validator.validate(responseJson, controls, allowedPathRoots);
        } catch (DiscoveryValidationException e) {
            log.warn("Rejected discovery agent response: {}", e.getMessage());
            throw failure(502, "invalid_agent_response", "The discovery agent returned an invalid control state.", e);
        }

        final Map<String, List<String>> parameters = baselineParameters(request);
        parameters.remove(PARAM_FULLTEXT);
        parameters.remove(PARAM_AI_FULLTEXT);
        parameters.remove(fulltextParameterName);
        parameters.remove("path");
        if (StringUtils.isNotBlank(validated.getFulltext())) {
            parameters.put(fulltextParameterName, Collections.singletonList(validated.getFulltext()));
        }
        if (StringUtils.isNotBlank(validated.getPath())) {
            parameters.put("path", Collections.singletonList(validated.getPath()));
        }

        for (final DiscoveryValidatedResponse.Update update : validated.getUpdates()) {
            final Binding binding = bindings.get(update.getId());
            if (binding == null) {
                throw failure(500, "missing_binding", "A validated discovery control had no server binding.");
            }
            final DiscoveryParameterUpdate parameterUpdate;
            try {
                parameterUpdate = binding.adapter.toParameterUpdate(
                        request, binding.predicate, update.getId(), update.getState());
            } catch (RuntimeException e) {
                throw failure(500, "adapter_failed", "A discovery control adapter failed.", e);
            }
            if (parameterUpdate == null) {
                throw failure(500, "mapping_failed",
                        "A discovery control adapter did not produce a parameter update.");
            }
            for (final String name : parameterUpdate.getRemoveParameters()) {
                if (StringUtils.isBlank(name)) {
                    throw failure(500, "mapping_failed",
                            "A discovery control adapter produced an invalid parameter name.");
                }
                parameters.remove(name);
            }
            for (final Map.Entry<String, List<String>> entry : parameterUpdate.getParameters().entrySet()) {
                if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null
                        || entry.getValue().isEmpty() || entry.getValue().stream().anyMatch(value -> value == null)) {
                    throw failure(500, "mapping_failed",
                            "A discovery control adapter produced an invalid parameter.");
                }
                parameters.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        parameters.put(PARAM_OFFSET, Collections.singletonList("0"));
        sanitizeDestinationParameters(parameters);
        final String redirectUrl = buildRedirectUrl(currentPage, parameters);
        if (redirectUrl.length() > MAX_REDIRECT_URL_LENGTH) {
            throw failure(413, "redirect_too_large", "The canonical search URL is too large.");
        }
        return new DiscoveryResolution(redirectUrl);
    }

    private Page getCurrentPage(final SlingHttpServletRequest request) {
        final PageManager pageManager = request.getResourceResolver().adaptTo(PageManager.class);
        return pageManager == null ? null : pageManager.getContainingPage(request.getResource());
    }

    DiscoveryControlAdapter findAdapter(final Predicate predicate) {
        for (final AdapterEntry entry : adapterEntries) {
            try {
                if (entry.adapter.supports(predicate)) {
                    return entry.adapter;
                }
            } catch (RuntimeException e) {
                log.warn("Discovery control adapter [{}] failed its supports check",
                        entry.adapter.getClass().getName(), e);
            }
        }
        return null;
    }

    private DiscoveredModels discoverModels(final SlingHttpServletRequest request,
                                            final Page currentPage)
            throws DiscoveryResolutionException {
        final Resource pageRoot = currentPage.getContentResource();
        if (pageRoot == null) {
            throw failure(400, "invalid_page", "The discovery page has no content resource.");
        }

        final DiscoveredModels models = new DiscoveredModels();
        models.roots.add(pageRoot);
        models.rootPaths.add(pageRoot.getPath());
        visitModelTree(request, currentPage, pageRoot, false, models);
        return models;
    }

    private void visitModelTree(final SlingHttpServletRequest request,
                                final Page currentPage,
                                final Resource resource,
                                final boolean external,
                                final DiscoveredModels models)
            throws DiscoveryResolutionException {
        if (resource == null
                || resource.getValueMap().get("sling:resourceType", String.class) == null) {
            return;
        }

        final Predicate predicate = predicateModel(request, resource);
        if (predicate != null
                && (!external
                || (!(predicate instanceof PagePredicate)
                && !(predicate instanceof HiddenPredicate)))) {
            models.predicates.add(predicate);
        }
        if (isFulltextResource(resource)) {
            final FulltextPredicate fulltext = modelFactory.getModelFromWrappedRequest(
                    request, resource, FulltextPredicate.class);
            if (fulltext != null) {
                models.fulltextPredicates.add(fulltext);
            }
        }

        for (final DiscoveryModelRootProvider provider : modelRootProviders) {
            final Collection<Resource> providedRoots;
            try {
                providedRoots = provider.getModelRoots(request, currentPage, resource);
            } catch (RuntimeException e) {
                throw failure(500, "model_root_provider_failed",
                        "A discovery model-root provider failed.", e);
            }
            if (providedRoots == null) {
                throw failure(500, "model_root_provider_failed",
                        "A discovery model-root provider returned no collection.");
            }
            for (final Resource root : providedRoots) {
                if (root == null || isCoveredByExistingRoot(models.roots, root)) {
                    continue;
                }
                if (models.roots.size() >= MAX_MODEL_ROOTS) {
                    throw failure(413, "context_too_large",
                            "The rendered page contains too many external model roots.");
                }
                if (!models.rootPaths.add(root.getPath())) {
                    continue;
                }
                models.roots.add(root);
                visitModelTree(request, currentPage, root, true, models);
            }
        }

        for (final Resource child : resource.getChildren()) {
            visitModelTree(request, currentPage, child, external, models);
        }
    }

    private Predicate predicateModel(final SlingHttpServletRequest request,
                                     final Resource resource) {
        for (final Map.Entry<String, Class<? extends Predicate>> entry
                : PREDICATE_MODEL_CLASSES.entrySet()) {
            if (resource.getResourceResolver().isResourceType(resource, entry.getKey())) {
                final Predicate predicate = modelFactory.getModelFromWrappedRequest(
                        request, resource, entry.getValue());
                if (predicate != null) {
                    return predicate;
                }
            }
        }
        return modelFactory.getModelFromWrappedRequest(request, resource, Predicate.class);
    }

    private boolean isFulltextResource(final Resource resource) {
        for (final String resourceType : FULLTEXT_RESOURCE_TYPES) {
            if (resource.getResourceResolver().isResourceType(resource, resourceType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCoveredByExistingRoot(final List<Resource> roots, final Resource candidate) {
        for (final Resource root : roots) {
            if (StringUtils.equals(root.getPath(), candidate.getPath())
                    || StringUtils.startsWith(candidate.getPath(), root.getPath() + "/")) {
                return true;
            }
        }
        return false;
    }

    private String fulltextParameterName(final Collection<FulltextPredicate> fulltextModels) {
        for (final FulltextPredicate predicate : fulltextModels) {
            final String name = StringUtils.trimToNull(predicate.getName());
            if (name != null) {
                return name;
            }
        }
        return PARAM_FULLTEXT;
    }

    private String createContext(final SlingHttpServletRequest request,
                                 final List<DiscoveryControl> controls,
                                 final List<String> allowedPathRoots,
                                 final String fulltextParameterName) {
        final JsonObject context = new JsonObject();
        final JsonObject query = new JsonObject();
        addNullable(query, "fulltext",
                StringUtils.trimToNull(request.getParameter(fulltextParameterName)));
        addNullable(query, "path", StringUtils.trimToNull(request.getParameter("path")));
        final JsonArray roots = new JsonArray();
        allowedPathRoots.forEach(roots::add);
        query.add("allowedPathRoots", roots);
        context.add("query", query);

        final JsonArray controlsJson = new JsonArray();
        for (final DiscoveryControl control : controls) {
            controlsJson.add(toJson(control));
        }
        context.add("controls", controlsJson);
        return GSON.toJson(context);
    }

    private JsonObject toJson(final DiscoveryControl control) {
        final JsonObject json = new JsonObject();
        json.addProperty("id", control.getId());
        json.addProperty("title", control.getTitle());
        json.addProperty("kind", control.getKind().getWireName());
        if (control.getCardinality() != null) {
            json.addProperty("cardinality", control.getCardinality().getWireName());
        }
        json.add("state", stateToJson(control));
        if (!control.getOptions().isEmpty()) {
            final JsonArray options = new JsonArray();
            for (final DiscoveryControlOption option : control.getOptions()) {
                final JsonObject optionJson = new JsonObject();
                optionJson.addProperty("value", option.getValue());
                optionJson.addProperty("label", option.getLabel());
                optionJson.addProperty("disabled", option.isDisabled());
                options.add(optionJson);
            }
            json.add("options", options);
        }
        if (!control.getConstraints().isEmpty()) {
            json.add("constraints", GSON.toJsonTree(control.getConstraints()));
        }
        return json;
    }

    private JsonObject stateToJson(final DiscoveryControl control) {
        final JsonObject state = new JsonObject();
        if (DiscoveryControl.Kind.DATE_RANGE.equals(control.getKind())) {
            addNullable(state, "lowerBound", control.getState().getLowerBound());
            addNullable(state, "upperBound", control.getState().getUpperBound());
        } else {
            final JsonArray values = new JsonArray();
            control.getState().getValues().forEach(values::add);
            state.add("values", values);
        }
        return state;
    }

    private void addNullable(final JsonObject object, final String name, final String value) {
        if (value == null) {
            object.add(name, JsonNull.INSTANCE);
        } else {
            object.addProperty(name, value);
        }
    }

    private Map<String, List<String>> baselineParameters(final SlingHttpServletRequest request) {
        final Map<String, List<String>> parameters = new LinkedHashMap<>();
        final RequestParameterMap parameterMap = request.getRequestParameterMap();
        for (final Map.Entry<String, RequestParameter[]> entry : parameterMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().length > 0) {
                final List<String> values = new ArrayList<>();
                for (final RequestParameter parameter : entry.getValue()) {
                    values.add(parameter.getString());
                }
                parameters.put(entry.getKey(), values);
            }
        }
        sanitizeDestinationParameters(parameters);
        return parameters;
    }

    private void sanitizeDestinationParameters(final Map<String, List<String>> parameters) {
        parameters.remove(PARAM_PROMPT);
        parameters.remove(PARAM_CONTEXT);
        parameters.remove("wcmmode");
        parameters.remove("forceeditcontext");
        parameters.remove("p.guessTotal");
        parameters.keySet().removeIf(name -> StringUtils.startsWith(name, "discovery.")
                || StringUtils.startsWith(name, "agent."));
    }

    private String buildRedirectUrl(final Page currentPage, final Map<String, List<String>> parameters)
            throws DiscoveryResolutionException {
        final StringBuilder url = new StringBuilder(currentPage.getPath()).append(".html");
        boolean first = true;
        for (final Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            for (final String value : entry.getValue()) {
                if (value == null) {
                    continue;
                }
                url.append(first ? '?' : '&');
                first = false;
                url.append(encode(entry.getKey())).append('=').append(encode(value));
            }
        }
        return url.toString();
    }

    private String encode(final String value) throws DiscoveryResolutionException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw failure(500, "url_encoding_failed", "The canonical search URL could not be encoded.", e);
        }
    }

    private DiscoveryResolutionException failure(final int status,
                                                 final String code,
                                                 final String message) {
        return new DiscoveryResolutionException(status, code, message);
    }

    private DiscoveryResolutionException failure(final int status,
                                                 final String code,
                                                 final String message,
                                                 final Throwable cause) {
        return new DiscoveryResolutionException(status, code, message, cause);
    }
}
