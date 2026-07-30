/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import com.adobe.aem.commons.assetshare.components.predicates.FulltextPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.HiddenPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.PagePredicate;
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.SortPredicate;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentClient;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentResponse;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlAdapter;
import com.adobe.cq.wcm.core.components.models.form.OptionItem;
import com.adobe.cq.wcm.core.components.models.form.Options;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Constants;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import org.mockito.ArgumentCaptor;

public class DiscoveryResolverImplTest {
    @Rule
    public final AemContext context = new AemContext();

    @Test
    public void selectsHighestRankingSupportingAdapter() {
        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        final Predicate predicate = mock(Predicate.class);
        final DiscoveryControlAdapter fallback = mock(DiscoveryControlAdapter.class);
        final DiscoveryControlAdapter customer = mock(DiscoveryControlAdapter.class);
        when(fallback.supports(predicate)).thenReturn(true);
        when(customer.supports(predicate)).thenReturn(true);

        resolver.bindDiscoveryControlAdapter(fallback, properties(Integer.MIN_VALUE, 20));
        resolver.bindDiscoveryControlAdapter(customer, properties(500, 30));

        assertSame(customer, resolver.findAdapter(predicate));
    }

    @Test
    public void skipsUnsupportedAndFailingAdapters() {
        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        final Predicate predicate = mock(Predicate.class);
        final DiscoveryControlAdapter failing = mock(DiscoveryControlAdapter.class);
        final DiscoveryControlAdapter unsupported = mock(DiscoveryControlAdapter.class);
        when(failing.supports(predicate)).thenThrow(new IllegalStateException("failure"));
        when(unsupported.supports(predicate)).thenReturn(false);

        resolver.bindDiscoveryControlAdapter(failing, properties(500, 10));
        resolver.bindDiscoveryControlAdapter(unsupported, properties(100, 20));

        assertNull(resolver.findAdapter(predicate));
    }

    @Test
    public void lowerServiceIdWinsRankingTieAndUnbindIsHonored() {
        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        final Predicate predicate = mock(Predicate.class);
        final DiscoveryControlAdapter first = mock(DiscoveryControlAdapter.class);
        final DiscoveryControlAdapter second = mock(DiscoveryControlAdapter.class);
        when(first.supports(predicate)).thenReturn(true);
        when(second.supports(predicate)).thenReturn(true);

        resolver.bindDiscoveryControlAdapter(second, properties(100, 20));
        resolver.bindDiscoveryControlAdapter(first, properties(100, 10));
        assertSame(first, resolver.findAdapter(predicate));

        resolver.unbindDiscoveryControlAdapter(first);
        assertSame(second, resolver.findAdapter(predicate));
    }

    @Test
    public void resolvesPatchToSamePageCanonicalUrlAndKeepsHiddenPredicatesPrivate() throws Exception {
        context.create().page("/content/assets", "/apps/test/template",
                "sling:resourceType", "test/page-root");
        context.create().resource("/content/assets/jcr:content/rail",
                "sling:resourceType", "test/rail");
        context.create().resource("/content/assets/jcr:content/rail/page",
                "sling:resourceType", "asset-share-commons/components/search/results");
        context.create().resource("/content/assets/jcr:content/rail/hidden",
                "sling:resourceType", "asset-share-commons/components/search/hidden");
        context.create().resource("/apps/customer/components/custom-property",
                "sling:resourceSuperType", "asset-share-commons/components/search/property");
        context.create().resource("/content/assets/jcr:content/rail/format",
                "sling:resourceType", "customer/components/custom-property");
        context.currentResource("/content/assets");

        final PagePredicate pagePredicate = mock(PagePredicate.class);
        when(pagePredicate.getId()).thenReturn("page-paths");
        when(pagePredicate.getPaths()).thenReturn(Collections.singletonList("/content/dam"));

        final HiddenPredicate hiddenPredicate = mock(HiddenPredicate.class);
        when(hiddenPredicate.getId()).thenReturn("hidden-security");

        final PropertyPredicate propertyPredicate = mock(PropertyPredicate.class);
        final OptionItem jpeg = option("image/jpeg", false);
        final OptionItem png = option("image/png", true);
        when(propertyPredicate.getId()).thenReturn("format");
        when(propertyPredicate.getTitle()).thenReturn("Format");
        when(propertyPredicate.getName()).thenReturn("propertyvalues");
        when(propertyPredicate.getGroup()).thenReturn("4_group");
        when(propertyPredicate.getValuesKey()).thenReturn("values");
        when(propertyPredicate.getProperty()).thenReturn(
                "jcr:content/metadata/customer/privateProperty");
        when(propertyPredicate.isReady()).thenReturn(true);
        when(propertyPredicate.getType()).thenReturn(Options.Type.CHECKBOX);
        when(propertyPredicate.getItems()).thenReturn(Arrays.asList(jpeg, png));

        final ModelFactory modelFactory = mock(ModelFactory.class);
        doAnswer(invocation -> {
            final Resource resource = invocation.getArgument(1);
            final Class<?> modelClass = invocation.getArgument(2);
            if (resource.getPath().endsWith("/page") && modelClass == PagePredicate.class) {
                return pagePredicate;
            }
            if (resource.getPath().endsWith("/hidden") && modelClass == HiddenPredicate.class) {
                return hiddenPredicate;
            }
            if (resource.getPath().endsWith("/format") && modelClass == PropertyPredicate.class) {
                return propertyPredicate;
            }
            return null;
        }).when(modelFactory).getModelFromWrappedRequest(
                eq(context.request()), any(Resource.class), any(Class.class));

        final String agentJson = "{\"version\":2,"
                + "\"query\":{\"fulltext\":\"landscape\",\"path\":null},"
                + "\"controlUpdates\":[{\"id\":\"format\","
                + "\"state\":{\"values\":[\"image/jpeg\"]}}]}";
        final DiscoveryAgentClient agent = mock(DiscoveryAgentClient.class);
        when(agent.isConfigured()).thenReturn(true);
        when(agent.call(eq("find landscape JPEGs"), any(String.class))).thenReturn(
                new DiscoveryAgentResponse(
                        200, "application/json", agentJson.getBytes(StandardCharsets.UTF_8)));

        final Map<String, Object> requestParameters = new HashMap<>();
        requestParameters.put("prompt", "find landscape JPEGs");
        requestParameters.put("context", "browser-context-must-not-survive");
        requestParameters.put("4_group.propertyvalues.0_values", "image/png");
        requestParameters.put("layout", "card");
        requestParameters.put("p.limit", "24");
        requestParameters.put("p.offset", "48");
        requestParameters.put("customer", new String[] {"one", "two"});
        context.request().setParameterMap(requestParameters);

        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        setField(resolver, "modelFactory", modelFactory);
        setField(resolver, "discoveryAgentClient", agent);
        resolver.bindDiscoveryControlAdapter(
                new DefaultDiscoveryControlAdapter(), properties(Integer.MIN_VALUE, 100));

        final DiscoveryResolution resolution = resolver.resolve(
                context.request(), "find landscape JPEGs");

        final ArgumentCaptor<String> agentContext = ArgumentCaptor.forClass(String.class);
        verify(agent).call(eq("find landscape JPEGs"), agentContext.capture());
        assertTrue(agentContext.getValue().contains("\"id\":\"format\""));
        assertTrue(agentContext.getValue().contains("\"allowedPathRoots\":[\"/content/dam\"]"));
        assertTrue(agentContext.getValue().contains("\"fulltext\":null"));
        assertTrue(agentContext.getValue().contains("\"path\":null"));
        assertFalse(agentContext.getValue().contains("hidden-security"));
        assertFalse(agentContext.getValue().contains("privateProperty"));
        assertFalse(agentContext.getValue().contains("4_group"));

        final String redirect = resolution.getRedirectUrl();
        assertTrue(redirect.startsWith("/content/assets.html?"));
        assertTrue(redirect.contains("4_group.propertyvalues.0_values=image%2Fjpeg"));
        assertTrue(redirect.contains("4_group.propertyvalues.property="
                + "jcr%3Acontent%2Fmetadata%2Fcustomer%2FprivateProperty"));
        assertTrue(redirect.contains("fulltext=landscape"));
        assertTrue(redirect.contains("layout=card"));
        assertTrue(redirect.contains("p.limit=24"));
        assertTrue(redirect.contains("p.offset=0"));
        assertTrue(redirect.contains("customer=one&customer=two"));
        assertFalse(redirect.contains("image%2Fpng"));
        assertFalse(redirect.contains("prompt="));
        assertFalse(redirect.contains("context="));
    }

    @Test
    public void resolvesSearchOnlyAiFulltextUsingTheRenderedPredicateName() throws Exception {
        context.create().page("/content/assets", "/apps/test/template",
                "sling:resourceType", "test/page-root");
        context.create().resource("/content/assets/jcr:content/search",
                "sling:resourceType", "asset-share-commons/components/search/search-bar");
        context.currentResource("/content/assets");

        final FulltextPredicate fulltextPredicate = mock(FulltextPredicate.class);
        when(fulltextPredicate.getName()).thenReturn("ai-fulltext");

        final ModelFactory modelFactory = mock(ModelFactory.class);
        doAnswer(invocation -> {
            final Resource resource = invocation.getArgument(1);
            final Class<?> modelClass = invocation.getArgument(2);
            if (resource.getPath().endsWith("/search") && modelClass == FulltextPredicate.class) {
                return fulltextPredicate;
            }
            return null;
        }).when(modelFactory).getModelFromWrappedRequest(
                eq(context.request()), any(Resource.class), any(Class.class));

        final DiscoveryAgentClient agent = mock(DiscoveryAgentClient.class);
        when(agent.isConfigured()).thenReturn(true);
        when(agent.call(eq("find semantic images"), any(String.class))).thenReturn(
                new DiscoveryAgentResponse(
                        200,
                        "application/json",
                        ("{\"version\":2,\"query\":{\"fulltext\":\"semantic landscape\","
                                + "\"path\":null},\"controlUpdates\":[]}")
                                .getBytes(StandardCharsets.UTF_8)));

        final Map<String, Object> requestParameters = new HashMap<>();
        requestParameters.put("ai-fulltext", "old semantic query");
        requestParameters.put("fulltext", "stale classic query");
        requestParameters.put("prompt", "find semantic images");
        context.request().setParameterMap(requestParameters);

        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        setField(resolver, "modelFactory", modelFactory);
        setField(resolver, "discoveryAgentClient", agent);
        resolver.bindDiscoveryControlAdapter(
                new DefaultDiscoveryControlAdapter(), properties(Integer.MIN_VALUE, 100));

        final DiscoveryResolution resolution = resolver.resolve(
                context.request(), "find semantic images");

        final ArgumentCaptor<String> agentContext = ArgumentCaptor.forClass(String.class);
        verify(agent).call(eq("find semantic images"), agentContext.capture());
        assertTrue(agentContext.getValue().contains("\"fulltext\":\"old semantic query\""));
        assertTrue(agentContext.getValue().contains("\"controls\":[]"));
        assertTrue(resolution.getRedirectUrl().contains("ai-fulltext=semantic+landscape"));
        assertFalse(resolution.getRedirectUrl().contains("fulltext=stale"));
        assertFalse(resolution.getRedirectUrl().contains("prompt="));
    }

    @Test
    public void describesGlobalSortControlsOnlyOnceWhenPageHasMultipleSortComponents()
            throws Exception {
        context.create().page("/content/assets", "/apps/test/template",
                "sling:resourceType", "test/page-root");
        context.create().resource("/content/assets/jcr:content/results",
                "sling:resourceType", "asset-share-commons/components/search/results");
        context.create().resource("/content/assets/jcr:content/sort-one",
                "sling:resourceType", "asset-share-commons/components/search/sort");
        context.create().resource("/content/assets/jcr:content/sort-two",
                "sling:resourceType", "asset-share-commons/components/search/sort");
        context.currentResource("/content/assets");

        final PagePredicate pagePredicate = mock(PagePredicate.class);
        when(pagePredicate.getPaths()).thenReturn(Collections.singletonList("/content/dam"));

        final SortPredicate sortPredicate = mock(SortPredicate.class);
        when(sortPredicate.isReady()).thenReturn(true);
        final SortPredicate.SortOptionItem option = mock(SortPredicate.SortOptionItem.class);
        when(option.getValue()).thenReturn("@jcr:content/jcr:lastModified");
        when(option.getText()).thenReturn("Last modified");
        when(option.isSelected()).thenReturn(true);
        when(sortPredicate.getItems()).thenReturn(Collections.<OptionItem>singletonList(option));
        final Map<String, Object> initial = new HashMap<>();
        initial.put("orderby", "@jcr:content/jcr:lastModified");
        initial.put("sort", "desc");
        final ValueMap initialValues = new ValueMapDecorator(initial);
        when(sortPredicate.getInitialValues()).thenReturn(initialValues);

        final ModelFactory modelFactory = mock(ModelFactory.class);
        doAnswer(invocation -> {
            final Resource resource = invocation.getArgument(1);
            final Class<?> modelClass = invocation.getArgument(2);
            if (resource.getPath().endsWith("/results") && modelClass == PagePredicate.class) {
                return pagePredicate;
            }
            if (resource.getPath().contains("/sort-") && modelClass == SortPredicate.class) {
                return sortPredicate;
            }
            return null;
        }).when(modelFactory).getModelFromWrappedRequest(
                eq(context.request()), any(Resource.class), any(Class.class));

        final DiscoveryAgentClient agent = mock(DiscoveryAgentClient.class);
        when(agent.isConfigured()).thenReturn(true);
        when(agent.call(eq("sort assets"), any(String.class))).thenReturn(
                new DiscoveryAgentResponse(
                        200,
                        "application/json",
                        ("{\"version\":2,\"query\":{\"fulltext\":null,\"path\":null},"
                                + "\"controlUpdates\":[]}").getBytes(StandardCharsets.UTF_8)));

        final DiscoveryResolverImpl resolver = new DiscoveryResolverImpl();
        setField(resolver, "modelFactory", modelFactory);
        setField(resolver, "discoveryAgentClient", agent);
        resolver.bindDiscoveryControlAdapter(
                new DefaultDiscoveryControlAdapter(), properties(Integer.MIN_VALUE, 100));

        resolver.resolve(context.request(), "sort assets");

        final ArgumentCaptor<String> agentContext = ArgumentCaptor.forClass(String.class);
        verify(agent).call(eq("sort assets"), agentContext.capture());
        assertEquals(1, occurrences(
                agentContext.getValue(),
                DefaultDiscoveryControlAdapter.SORT_ORDERBY_CONTROL_ID));
        assertEquals(1, occurrences(
                agentContext.getValue(),
                DefaultDiscoveryControlAdapter.SORT_DIRECTION_CONTROL_ID));
        assertTrue(agentContext.getValue().contains(
                DefaultDiscoveryControlAdapter.SORT_OPTION_PREFIX + "0"));
        assertFalse(agentContext.getValue().contains("@jcr:content/jcr:lastModified"));
    }

    private Map<String, Object> properties(final int ranking, final long serviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(Constants.SERVICE_RANKING, ranking);
        properties.put(Constants.SERVICE_ID, serviceId);
        return properties;
    }

    private OptionItem option(final String value, final boolean selected) {
        final OptionItem item = mock(OptionItem.class);
        when(item.getValue()).thenReturn(value);
        when(item.getText()).thenReturn(value);
        when(item.isSelected()).thenReturn(selected);
        return item;
    }

    private void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private int occurrences(final String value, final String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
