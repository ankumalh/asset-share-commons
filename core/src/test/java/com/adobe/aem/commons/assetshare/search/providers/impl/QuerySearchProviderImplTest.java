/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.providers.impl;

import com.adobe.aem.commons.assetshare.components.predicates.PagePredicate;
import com.adobe.aem.commons.assetshare.search.SearchSafety;
import com.adobe.aem.commons.assetshare.search.providers.QuerySearchPostProcessor;
import com.adobe.aem.commons.assetshare.search.providers.QuerySearchPreProcessor;
import com.adobe.aem.commons.assetshare.search.results.Results;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuerySearchProviderImplTest {
    @Rule
    public final AemContext context = new AemContext();

    @Test
    public void canonicalGetMergesVisiblePageAndHiddenPredicatesAndRunsProcessors() throws Exception {
        final Map<String, Object> requestParameters = new HashMap<>();
        requestParameters.put("4_group.propertyvalues.property",
                "jcr:content/metadata/dc:format");
        requestParameters.put("4_group.propertyvalues.0_values", "image/jpeg");
        requestParameters.put("p.offset", "0");
        requestParameters.put("p.limit", "24");
        requestParameters.put("layout", "card");
        context.request().setParameterMap(requestParameters);

        final Map<String, String> serverParameters = new HashMap<>();
        serverParameters.put("type", "dam:Asset");
        serverParameters.put("1_group.0_path", "/content/dam/allowed");
        serverParameters.put("2_group.property",
                "jcr:content/metadata/customer/securityClassification");
        serverParameters.put("2_group.property.value", "public");
        final PagePredicate pagePredicate = mock(PagePredicate.class);
        when(pagePredicate.getPaths()).thenReturn(Collections.singletonList("/content/dam/allowed"));
        when(pagePredicate.getPredicateGroup(any(PagePredicate.ParamTypes[].class)))
                .thenReturn(PredicateGroup.create(serverParameters));
        context.registerAdapter(
                SlingHttpServletRequest.class, PagePredicate.class, pagePredicate);

        final QuerySearchPreProcessor preProcessor = mock(QuerySearchPreProcessor.class);
        final ArgumentCaptor<Map<String, String>> parameters =
                ArgumentCaptor.forClass((Class) Map.class);
        when(preProcessor.process(eq(context.request()), parameters.capture()))
                .thenAnswer(invocation -> PredicateGroup.create(invocation.getArgument(1)));

        final QueryBuilder queryBuilder = mock(QueryBuilder.class);
        final Query query = mock(Query.class);
        final SearchResult searchResult = mock(SearchResult.class);
        when(queryBuilder.createQuery(any(PredicateGroup.class), any())).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getHits()).thenReturn(Collections.emptyList());

        final SearchSafety searchSafety = mock(SearchSafety.class);
        when(searchSafety.isSafe(eq(context.resourceResolver()), any(PredicateGroup.class)))
                .thenReturn(true);

        final Results processedResults = mock(Results.class);
        final QuerySearchPostProcessor postProcessor = mock(QuerySearchPostProcessor.class);
        when(postProcessor.process(
                eq(context.request()), eq(query), any(Results.class), eq(searchResult)))
                .thenReturn(processedResults);

        final QuerySearchProviderImpl provider = new QuerySearchProviderImpl();
        setField(provider, "querySearchPreProcessor", preProcessor);
        setField(provider, "querySearchPostProcessor", postProcessor);
        setField(provider, "queryBuilder", queryBuilder);
        setField(provider, "searchSafety", searchSafety);
        setField(provider, "modelFactory", mock(ModelFactory.class));

        assertSame(processedResults, provider.getResults(context.request()));

        final Map<String, String> canonicalParameters = parameters.getValue();
        assertTrue(canonicalParameters.containsValue("image/jpeg"));
        assertTrue(canonicalParameters.containsValue("/content/dam/allowed"));
        assertTrue(canonicalParameters.containsValue(
                "jcr:content/metadata/customer/securityClassification"));
        assertTrue(canonicalParameters.containsValue("public"));
        assertTrue(canonicalParameters.containsValue("dam:Asset"));
        verify(preProcessor).process(eq(context.request()), any(Map.class));
        verify(postProcessor).process(
                eq(context.request()), eq(query), any(Results.class), eq(searchResult));
    }

    private void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
