/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import org.apache.sling.api.SlingHttpServletRequest;

interface DiscoveryResolver {
    boolean isConfigured();

    DiscoveryResolution resolve(SlingHttpServletRequest request, String prompt)
            throws DiscoveryResolutionException;
}
