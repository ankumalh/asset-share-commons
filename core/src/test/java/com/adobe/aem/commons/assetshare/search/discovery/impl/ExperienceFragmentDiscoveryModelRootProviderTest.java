/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.cq.wcm.core.components.models.ExperienceFragment;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExperienceFragmentDiscoveryModelRootProviderTest {
    @Rule
    public final AemContext context = new AemContext();

    @Test
    public void resolvesRenderedExperienceFragmentVariationRoot() throws Exception {
        context.create().page("/content/search", "/apps/test/template",
                "sling:resourceType", "test/page");
        final Resource fragmentComponent = context.create().resource(
                "/content/search/jcr:content/fragment",
                "sling:resourceType",
                "core/wcm/components/experiencefragment/v1/experiencefragment");
        context.create().page(
                "/content/experience-fragments/site/search-rail/master",
                "/apps/test/xf-template",
                "sling:resourceType", "test/xf-page");

        final ExperienceFragment fragment = mock(ExperienceFragment.class);
        when(fragment.getLocalizedFragmentVariationPath()).thenReturn(
                "/content/experience-fragments/site/search-rail/master");
        final ModelFactory modelFactory = mock(ModelFactory.class);
        doAnswer(invocation -> {
            final Resource resource = invocation.getArgument(1);
            final Class<?> modelClass = invocation.getArgument(2);
            return resource.getPath().equals(fragmentComponent.getPath())
                    && modelClass == ExperienceFragment.class
                    ? fragment
                    : null;
        }).when(modelFactory).getModelFromWrappedRequest(
                eq(context.request()), org.mockito.ArgumentMatchers.any(Resource.class),
                eq(ExperienceFragment.class));

        final ExperienceFragmentDiscoveryModelRootProvider provider =
                new ExperienceFragmentDiscoveryModelRootProvider();
        setField(provider, "modelFactory", modelFactory);
        final Page currentPage = context.resourceResolver()
                .adaptTo(PageManager.class).getPage("/content/search");

        final Collection<Resource> roots = provider.getModelRoots(context.request(), currentPage);

        assertEquals(1, roots.size());
        assertTrue(roots.iterator().next().getPath().endsWith("/master/jcr:content"));
    }

    private void setField(final Object target, final String name, final Object value)
            throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
