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
import org.osgi.annotation.versioning.ConsumerType;

import java.util.Collection;

/**
 * Supplies external component roots rendered by a reference/include component
 * whose predicate Sling Models should participate in discovery resolution.
 *
 * <p>ASC invokes providers as it walks each rendered component in repository
 * order. Returned roots are visited immediately at that component's position,
 * before its local child resources. This preserves the request-scoped
 * predicate group IDs produced by normal server rendering. Returned resources
 * remain server-side and are never included in the agent context.</p>
 */
@ConsumerType
public interface DiscoveryModelRootProvider {

    /**
     * Returns external roots rendered by the supplied component.
     *
     * @param request current discovery request
     * @param currentPage current ASC page
     * @param renderedComponent component at the current render-order position
     * @return roots rendered at that position, or an empty collection
     */
    Collection<Resource> getModelRoots(SlingHttpServletRequest request,
                                       Page currentPage,
                                       Resource renderedComponent);
}
