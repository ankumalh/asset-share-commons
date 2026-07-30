/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryAgentClient;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryConfiguration;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;

/**
 * Exposes only the configured/unconfigured state needed by HTL.
 */
@Model(
        adaptables = SlingHttpServletRequest.class,
        adapters = DiscoveryConfiguration.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class DiscoveryConfigurationImpl implements DiscoveryConfiguration {
    @OSGiService
    private DiscoveryAgentClient discoveryAgentClient;

    @Override
    public boolean isEnabled() {
        return discoveryAgentClient != null && discoveryAgentClient.isConfigured();
    }
}
