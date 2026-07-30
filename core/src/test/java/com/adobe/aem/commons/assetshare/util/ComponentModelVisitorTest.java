/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.util;

import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Rule;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ComponentModelVisitorTest {
    @Rule
    public final AemContext context = new AemContext();

    @Test
    public void fallsBackToGenericModelWhenKnownOverlayDoesNotImplementStandardInterface() {
        context.create().resource("/apps/customer/components/custom-property",
                "sling:resourceSuperType", "asset-share-commons/components/search/property");
        final Resource resource = context.create().resource("/content/page/jcr:content/custom",
                "sling:resourceType", "customer/components/custom-property");
        final Predicate customerPredicate = mock(Predicate.class);
        final ModelFactory modelFactory = mock(ModelFactory.class);

        when(modelFactory.getModelFromWrappedRequest(
                eq(context.request()), eq(resource), eq(PropertyPredicate.class))).thenReturn(null);
        when(modelFactory.getModelFromWrappedRequest(
                eq(context.request()), eq(resource), eq(Predicate.class))).thenReturn(customerPredicate);

        final Map<String, Class<? extends Predicate>> mappings = new LinkedHashMap<>();
        mappings.put("asset-share-commons/components/search/property", PropertyPredicate.class);
        final ComponentModelVisitor<Predicate> visitor = new ComponentModelVisitor<>(
                context.request(), modelFactory, Predicate.class, mappings);

        visitor.accept(resource);

        assertEquals(1, visitor.getModels().size());
        assertSame(customerPredicate, visitor.getModels().iterator().next());
    }
}
