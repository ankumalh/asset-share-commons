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
import io.wcm.testing.mock.aem.junit.AemContext;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DiscoveryConfigurationImplTest {
    @Rule
    public final AemContext context = new AemContext();

    @Test
    public void exposesConfiguredClientStateToRendering() {
        final DiscoveryAgentClient client = mock(DiscoveryAgentClient.class);
        when(client.isConfigured()).thenReturn(true);
        context.registerService(DiscoveryAgentClient.class, client);
        context.addModelsForClasses(DiscoveryConfigurationImpl.class);

        final DiscoveryConfiguration configuration =
                context.request().adaptTo(DiscoveryConfiguration.class);

        assertNotNull(configuration);
        assertTrue(configuration.isEnabled());
    }

    @Test
    public void isDisabledWithoutAClient() {
        context.addModelsForClasses(DiscoveryConfigurationImpl.class);

        final DiscoveryConfiguration configuration =
                context.request().adaptTo(DiscoveryConfiguration.class);

        assertNotNull(configuration);
        assertFalse(configuration.isEnabled());
    }
}
