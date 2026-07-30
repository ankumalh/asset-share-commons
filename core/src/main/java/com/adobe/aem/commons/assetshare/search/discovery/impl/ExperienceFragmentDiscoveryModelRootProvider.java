/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryModelRootProvider;
import com.adobe.cq.wcm.core.components.models.ExperienceFragment;
import com.day.cq.wcm.api.Page;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;
import java.util.Collections;

/**
 * Adds the component trees rendered by Core Component Experience Fragments.
 */
@Component(service = DiscoveryModelRootProvider.class)
public class ExperienceFragmentDiscoveryModelRootProvider implements DiscoveryModelRootProvider {
    @Reference
    private transient ModelFactory modelFactory;

    @Override
    public Collection<Resource> getModelRoots(final SlingHttpServletRequest request,
                                              final Page currentPage,
                                              final Resource renderedComponent) {
        if (currentPage == null || renderedComponent == null) {
            return Collections.emptyList();
        }

        final ExperienceFragment fragment = modelFactory.getModelFromWrappedRequest(
                request, renderedComponent, ExperienceFragment.class);
        if (fragment == null) {
            return Collections.emptyList();
        }
        final Resource fragmentRoot = resolveFragmentRoot(
                request, fragment.getLocalizedFragmentVariationPath());
        return fragmentRoot == null
                ? Collections.emptyList()
                : Collections.singletonList(fragmentRoot);
    }

    private Resource resolveFragmentRoot(final SlingHttpServletRequest request, final String path) {
        if (StringUtils.isBlank(path) || !StringUtils.startsWith(path, "/content/experience-fragments/")) {
            return null;
        }
        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            return null;
        }
        final Resource contentResource = resource.getChild("jcr:content");
        if (contentResource != null) {
            return contentResource;
        }
        if (StringUtils.isNotBlank(resource.getResourceType())) {
            return resource;
        }
        return null;
    }
}
