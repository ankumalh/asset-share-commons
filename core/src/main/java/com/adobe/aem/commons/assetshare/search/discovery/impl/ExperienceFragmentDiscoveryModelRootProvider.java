/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryModelRootProvider;
import com.adobe.aem.commons.assetshare.util.ComponentModelVisitor;
import com.adobe.cq.wcm.core.components.models.ExperienceFragment;
import com.day.cq.wcm.api.Page;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds the component trees rendered by Core Component Experience Fragments.
 */
@Component(service = DiscoveryModelRootProvider.class)
public class ExperienceFragmentDiscoveryModelRootProvider implements DiscoveryModelRootProvider {
    private static final int MAX_FRAGMENT_ROOTS = 20;

    @Reference
    private transient ModelFactory modelFactory;

    @Override
    public Collection<Resource> getModelRoots(final SlingHttpServletRequest request,
                                              final Page currentPage) {
        if (currentPage == null || currentPage.getContentResource() == null) {
            return Collections.emptyList();
        }

        final List<Resource> roots = new ArrayList<>();
        final Deque<Resource> pending = new ArrayDeque<>();
        final Set<String> visitedRoots = new LinkedHashSet<>();
        pending.add(currentPage.getContentResource());
        visitedRoots.add(currentPage.getContentResource().getPath());

        while (!pending.isEmpty() && roots.size() < MAX_FRAGMENT_ROOTS) {
            final Resource root = pending.removeFirst();
            final ComponentModelVisitor<ExperienceFragment> visitor =
                    new ComponentModelVisitor<>(
                            request,
                            modelFactory,
                            ExperienceFragment.class);
            visitor.accept(root);
            for (final ExperienceFragment fragment : visitor.getModels()) {
                final Resource fragmentRoot = resolveFragmentRoot(
                        request, fragment.getLocalizedFragmentVariationPath());
                if (fragmentRoot != null && visitedRoots.add(fragmentRoot.getPath())) {
                    roots.add(fragmentRoot);
                    pending.addLast(fragmentRoot);
                    if (roots.size() >= MAX_FRAGMENT_ROOTS) {
                        break;
                    }
                }
            }
        }
        return roots;
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
