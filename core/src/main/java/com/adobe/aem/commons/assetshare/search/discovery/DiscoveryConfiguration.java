/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Page-rendering view of discovery availability.
 */
@ProviderType
public interface DiscoveryConfiguration {
    /**
     * @return true when the discovery agent endpoint is explicitly configured.
     */
    boolean isEnabled();
}
