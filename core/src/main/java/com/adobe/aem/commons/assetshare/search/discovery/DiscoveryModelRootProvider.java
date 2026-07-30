/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery;

import com.day.cq.wcm.api.Page;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.osgi.annotation.versioning.ProviderType;

import java.util.Collection;

/**
 * Supplies additional rendered component roots whose predicate Sling Models
 * should participate in discovery resolution.
 *
 * <p>The current page content root is always visited by ASC. Implementations
 * are intended for reference/include components that render search controls
 * from resources outside that page subtree. Returned resources remain
 * server-side and are never included in the agent context.</p>
 */
@ProviderType
public interface DiscoveryModelRootProvider {

    /**
     * Returns additional component roots rendered as part of the current page.
     *
     * @param request current discovery request
     * @param currentPage current ASC page
     * @return additional roots, or an empty collection
     */
    Collection<Resource> getModelRoots(SlingHttpServletRequest request, Page currentPage);
}
