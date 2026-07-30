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
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentClient;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentResponse;
import com.adobe.aem.commons.assetshare.search.results.Results;
import com.google.gson.JsonParser;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DiscoverySearchProviderImplTest {

    private DiscoverySearchProviderImpl provider;
    private DiscoveryAgentClient discoveryAgentClient;
    private SlingHttpServletRequest request;

    @Before
    public void setUp() throws Exception {
        provider = new DiscoverySearchProviderImpl();
        discoveryAgentClient = mock(DiscoveryAgentClient.class);
        request = mock(SlingHttpServletRequest.class);
        setField(provider, "discoveryAgentClient", discoveryAgentClient);
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /* accepts() */

    @Test
    public void accepts_rejectsBlankPrompt() {
        when(request.getParameter("prompt")).thenReturn(" ");
        when(request.getParameter("context")).thenReturn("{}");
        when(discoveryAgentClient.isConfigured()).thenReturn(true);

        assertFalse(provider.accepts(request));
    }

    @Test
    public void accepts_rejectsBlankContext() {
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn(null);
        when(discoveryAgentClient.isConfigured()).thenReturn(true);

        assertFalse(provider.accepts(request));
    }

    @Test
    public void accepts_rejectsWhenAgentNotConfigured() {
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{\"controls\":[]}");
        when(discoveryAgentClient.isConfigured()).thenReturn(false);

        assertFalse(provider.accepts(request));
    }

    @Test
    public void accepts_trueWhenPromptAndContextPresentAndAgentConfigured() {
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{\"controls\":[]}");
        when(discoveryAgentClient.isConfigured()).thenReturn(true);

        assertTrue(provider.accepts(request));
    }

    /* getResults() -- agent failure paths (do not require predicate resolution) */

    @Test
    public void getResults_returnsErringResultsWhenAgentCallThrows() throws Exception {
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{}");
        when(discoveryAgentClient.call(anyString(), anyString())).thenThrow(new IOException("boom"));

        assertEquals(Results.ERRING_RESULTS, provider.getResults(request));
    }

    @Test
    public void getResults_returnsErringResultsWhenAgentRespondsWithError() throws Exception {
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{}");
        when(discoveryAgentClient.call(anyString(), anyString()))
                .thenReturn(new DiscoveryAgentResponse(503, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Results.ERRING_RESULTS, provider.getResults(request));
    }

    /* translate() -- the core id-resolution + QueryBuilder param translation logic */

    @Test
    public void translate_propertyValuesControlUpdate_producesPropertyValuesParams() {
        final PropertyPredicate propertyValuesPredicate = mock(PropertyPredicate.class);
        when(propertyValuesPredicate.getId()).thenReturn("cmp-propertyvalues_472475771");
        when(propertyValuesPredicate.getName()).thenReturn("propertyvalues");
        when(propertyValuesPredicate.getProperty()).thenReturn("./jcr:content/metadata/cq:tags");
        when(propertyValuesPredicate.hasOperation()).thenReturn(true);
        when(propertyValuesPredicate.getOperation()).thenReturn("equals");

        final Map<String, Predicate> predicatesById = new HashMap<>();
        predicatesById.put("cmp-propertyvalues_472475771", propertyValuesPredicate);

        final String responseJson = "{"
                + "\"query\":{\"fulltext\":null,\"path\":null},"
                + "\"controlUpdates\":[{"
                + "  \"id\":\"cmp-propertyvalues_472475771\","
                + "  \"state\":{\"values\":[\"properties:style/monochrome/grayscale\"]}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), predicatesById);

        assertEquals("./jcr:content/metadata/cq:tags", params.get("0_group.propertyvalues.property"));
        assertEquals("equals", params.get("0_group.propertyvalues.operation"));
        assertEquals("properties:style/monochrome/grayscale", params.get("0_group.propertyvalues.0_values"));
    }

    @Test
    public void translate_dateRangeControlUpdate_producesAbsoluteDateRangeParamsWithOperation() {
        final DatePredicateImpl dateRangePredicate = mock(DatePredicateImpl.class);
        when(dateRangePredicate.getId()).thenReturn("cmp-daterange_1408081816");
        when(dateRangePredicate.getName()).thenReturn("daterange");
        when(dateRangePredicate.getProperty()).thenReturn("jcr:created");

        final Map<String, Predicate> predicatesById = new HashMap<>();
        predicatesById.put("cmp-daterange_1408081816", dateRangePredicate);

        final String responseJson = "{"
                + "\"query\":{\"fulltext\":null,\"path\":null},"
                + "\"controlUpdates\":[{"
                + "  \"id\":\"cmp-daterange_1408081816\","
                + "  \"state\":{\"lowerBound\":\"2026-07-17T00:00:00.000Z\"}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), predicatesById);

        assertEquals("jcr:created", params.get("0_group.daterange.property"));
        assertEquals("2026-07-17T00:00:00.000Z", params.get("0_group.daterange.lowerBound"));
        assertEquals(">=", params.get("0_group.daterange.lowerOperation"));
    }

    @Test
    public void translate_relativeDateRangeControlUpdate_omitsLowerOperation() {
        final DatePredicateImpl relativeDateRangePredicate = mock(DatePredicateImpl.class);
        when(relativeDateRangePredicate.getId()).thenReturn("cmp-relativedaterange_581962386");
        when(relativeDateRangePredicate.getName()).thenReturn("relativedaterange");
        when(relativeDateRangePredicate.getProperty()).thenReturn("jcr:content/jcr:lastModified");

        final Map<String, Predicate> predicatesById = new HashMap<>();
        predicatesById.put("cmp-relativedaterange_581962386", relativeDateRangePredicate);

        final String responseJson = "{"
                + "\"query\":{\"fulltext\":null,\"path\":null},"
                + "\"controlUpdates\":[{"
                + "  \"id\":\"cmp-relativedaterange_581962386\","
                + "  \"state\":{\"lowerBound\":\"-14d\"}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), predicatesById);

        assertEquals("jcr:content/jcr:lastModified", params.get("0_group.relativedaterange.property"));
        assertEquals("-14d", params.get("0_group.relativedaterange.lowerBound"));
        assertNull("relativedaterange must not carry a lowerOperation key",
                params.get("0_group.relativedaterange.lowerOperation"));
    }

    @Test
    public void translate_unknownControlId_isSkippedAndDoesNotConsumeAGroupNumber() {
        final PropertyPredicate resolvable = mock(PropertyPredicate.class);
        when(resolvable.getId()).thenReturn("cmp-propertyvalues_1");
        when(resolvable.getName()).thenReturn("propertyvalues");
        when(resolvable.getProperty()).thenReturn("./jcr:content/metadata/cq:tags");

        final Map<String, Predicate> predicatesById = new HashMap<>();
        predicatesById.put("cmp-propertyvalues_1", resolvable);

        final String responseJson = "{"
                + "\"query\":{\"fulltext\":null,\"path\":null},"
                + "\"controlUpdates\":[{"
                + "  \"id\":\"cmp-does-not-exist\","
                + "  \"state\":{\"values\":[\"x\"]}"
                + "},{"
                + "  \"id\":\"cmp-propertyvalues_1\","
                + "  \"state\":{\"values\":[\"y\"]}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), predicatesById);

        // The resolvable control still lands in group 0, proving the unresolvable
        // control was skipped rather than silently consuming a group slot.
        assertEquals("y", params.get("0_group.propertyvalues.0_values"));
    }

    @Test
    public void translate_topLevelFulltextAndPath_areMappedToPlainParams() {
        final String responseJson = "{"
                + "\"query\":{\"fulltext\":\"grayscale\",\"path\":\"/content/dam/we-retail\"},"
                + "\"controlUpdates\":[]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), new HashMap<>());

        assertEquals("grayscale", params.get("fulltext"));
        assertEquals("/content/dam/we-retail", params.get("path"));
    }

    /*
     * Sort control updates: __asset_share_discovery_sort_orderby/__asset_share_discovery_sort_direction
     * are synthetic client-side ids (search-form.js#getDiscoverySortControls) with no backing
     * Predicate resource, so they must be special-cased directly onto QueryBuilder's orderby/
     * orderby.sort params rather than resolved via predicatesById -- otherwise the executed search
     * runs with the stale sort order while the (client-synced) dropdown UI shows the new one.
     */

    @Test
    public void translate_sortOrderByControlUpdate_setsOrderByParam() {
        final String responseJson = "{"
                + "\"controlUpdates\":[{"
                + "\"id\":\"__asset_share_discovery_sort_orderby\","
                + "\"state\":{\"values\":[\"@jcr:content/jcr:lastModified\"]}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), new HashMap<>());

        assertEquals("@jcr:content/jcr:lastModified", params.get("orderby"));
        assertNull("sort control updates are not predicate groups", params.get("0_group.orderby"));
    }

    @Test
    public void translate_sortDirectionControlUpdate_setsOrderBySortParam() {
        final String responseJson = "{"
                + "\"controlUpdates\":[{"
                + "\"id\":\"__asset_share_discovery_sort_direction\","
                + "\"state\":{\"values\":[\"desc\"]}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), new HashMap<>());

        assertEquals("desc", params.get("orderby.sort"));
    }

    @Test
    public void translate_sortControlUpdates_doNotRequireAResolvablePredicate() {
        // The whole point: these ids never appear in predicatesById (no rendered Predicate backs
        // them), yet they must still be translated -- confirms the fix does not depend on
        // resolvePredicatesById() finding a matching component.
        final String responseJson = "{"
                + "\"controlUpdates\":["
                + "{\"id\":\"__asset_share_discovery_sort_orderby\",\"state\":{\"values\":[\"@jcr:content/jcr:lastModified\"]}},"
                + "{\"id\":\"__asset_share_discovery_sort_direction\",\"state\":{\"values\":[\"desc\"]}}"
                + "]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), new HashMap<>());

        assertEquals("@jcr:content/jcr:lastModified", params.get("orderby"));
        assertEquals("desc", params.get("orderby.sort"));
    }

    @Test
    public void translate_sortControlUpdateWithNoValues_isSkipped() {
        final String responseJson = "{"
                + "\"controlUpdates\":[{"
                + "\"id\":\"__asset_share_discovery_sort_orderby\","
                + "\"state\":{\"values\":[]}"
                + "}]}";

        final Map<String, String> params = provider.translate(
                JsonParser.parseString(responseJson).getAsJsonObject(), new HashMap<>());

        assertNull(params.get("orderby"));
    }

    /* DiscoveryTranslatedRequestWrapper */

    @Test
    public void requestWrapper_removesPromptAndContext_andOverlaysTranslatedParams() throws Exception {
        final SlingHttpServletRequest wrapped = mock(SlingHttpServletRequest.class);

        final Map<String, RequestParameter[]> originalParams = new LinkedHashMap<>();
        originalParams.put("prompt", new RequestParameter[]{stringRequestParameter("prompt", "find grayscale assets")});
        originalParams.put("context", new RequestParameter[]{stringRequestParameter("context", "{}")});
        originalParams.put("p.offset", new RequestParameter[]{stringRequestParameter("p.offset", "0")});

        final RequestParameterMap originalMap = mock(RequestParameterMap.class);
        when(originalMap.entrySet()).thenReturn(originalParams.entrySet());
        when(wrapped.getRequestParameterMap()).thenReturn(originalMap);

        final Map<String, String> translated = new LinkedHashMap<>();
        translated.put("0_group.propertyvalues.property", "./jcr:content/metadata/cq:tags");
        translated.put("0_group.propertyvalues.0_values", "properties:style/monochrome/grayscale");

        final DiscoverySearchProviderImpl.DiscoveryTranslatedRequestWrapper wrapper =
                new DiscoverySearchProviderImpl.DiscoveryTranslatedRequestWrapper(wrapped, translated);

        final RequestParameterMap resultMap = wrapper.getRequestParameterMap();

        assertNull("prompt must not leak into the QueryBuilder execution params", resultMap.getValue("prompt"));
        assertNull("context must not leak into the QueryBuilder execution params", resultMap.getValue("context"));
        assertEquals("0", resultMap.getValue("p.offset").getString());
        assertEquals("./jcr:content/metadata/cq:tags",
                resultMap.getValue("0_group.propertyvalues.property").getString());
        assertEquals("properties:style/monochrome/grayscale",
                resultMap.getValue("0_group.propertyvalues.0_values").getString());
    }

    @Test
    public void getResults_stashesRawAgentResponseInAdditionalData_andDelegatesToQuerySearchProvider() throws Exception {
        final QuerySearchProviderImpl querySearchProvider = mock(QuerySearchProviderImpl.class);
        setField(provider, "querySearchProvider", querySearchProvider);

        final org.apache.sling.api.resource.ResourceResolver resourceResolver =
                mock(org.apache.sling.api.resource.ResourceResolver.class);
        final org.apache.sling.api.resource.Resource resource = mock(org.apache.sling.api.resource.Resource.class);
        when(resource.getPath()).thenReturn("/content/search/results");
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(request.getResource()).thenReturn(resource);
        when(resourceResolver.adaptTo(com.day.cq.wcm.api.PageManager.class)).thenReturn(null);
        final RequestParameterMap emptyParamMap = mock(RequestParameterMap.class);
        when(emptyParamMap.entrySet()).thenReturn(new HashMap<String, RequestParameter[]>().entrySet());
        when(request.getRequestParameterMap()).thenReturn(emptyParamMap);

        final String responseJson = "{\"query\":{\"fulltext\":\"grayscale\"},\"controlUpdates\":[]}";
        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{}");
        when(discoveryAgentClient.call(anyString(), anyString())).thenReturn(
                new DiscoveryAgentResponse(200, "application/json", responseJson.getBytes(StandardCharsets.UTF_8)));

        final org.apache.sling.api.wrappers.ValueMapDecorator additionalData =
                new org.apache.sling.api.wrappers.ValueMapDecorator(new HashMap<>());
        final Results delegateResults = mock(Results.class);
        when(delegateResults.getAdditionalData()).thenReturn(additionalData);
        when(querySearchProvider.getResults(org.mockito.ArgumentMatchers.any())).thenReturn(delegateResults);

        final Results actual = provider.getResults(request);

        assertEquals("the delegate's Results instance is returned as-is (same reference)",
                delegateResults, actual);
        assertEquals(responseJson, additionalData.get(
                DiscoverySearchProviderImpl.ADDITIONAL_DATA_KEY_AGENT_RESPONSE, String.class));
    }

    @Test
    public void getResults_neverMutatesTheSharedErringResultsSingleton() throws Exception {
        // Guard against a regression where a translated-but-still-erroring delegate call could
        // accidentally write additional data onto the shared Results.ERRING_RESULTS singleton,
        // which is reused across every request and must stay pristine.
        final QuerySearchProviderImpl querySearchProvider = mock(QuerySearchProviderImpl.class);
        setField(provider, "querySearchProvider", querySearchProvider);

        final org.apache.sling.api.resource.ResourceResolver resourceResolver =
                mock(org.apache.sling.api.resource.ResourceResolver.class);
        final org.apache.sling.api.resource.Resource resource = mock(org.apache.sling.api.resource.Resource.class);
        when(resource.getPath()).thenReturn("/content/search/results");
        when(request.getResourceResolver()).thenReturn(resourceResolver);
        when(request.getResource()).thenReturn(resource);
        when(resourceResolver.adaptTo(com.day.cq.wcm.api.PageManager.class)).thenReturn(null);
        final RequestParameterMap emptyParamMap = mock(RequestParameterMap.class);
        when(emptyParamMap.entrySet()).thenReturn(new HashMap<String, RequestParameter[]>().entrySet());
        when(request.getRequestParameterMap()).thenReturn(emptyParamMap);

        when(request.getParameter("prompt")).thenReturn("find grayscale assets");
        when(request.getParameter("context")).thenReturn("{}");
        when(discoveryAgentClient.call(anyString(), anyString())).thenReturn(
                new DiscoveryAgentResponse(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
        when(querySearchProvider.getResults(org.mockito.ArgumentMatchers.any())).thenReturn(Results.ERRING_RESULTS);

        final Results actual = provider.getResults(request);

        assertEquals(Results.ERRING_RESULTS, actual);
        assertNull(Results.ERRING_RESULTS.getAdditionalData()
                .get(DiscoverySearchProviderImpl.ADDITIONAL_DATA_KEY_AGENT_RESPONSE, String.class));
    }

    private static RequestParameter stringRequestParameter(final String name, final String value) {
        final RequestParameter parameter = mock(RequestParameter.class);
        when(parameter.getName()).thenReturn(name);
        when(parameter.getString()).thenReturn(value);
        return parameter;
    }
}
