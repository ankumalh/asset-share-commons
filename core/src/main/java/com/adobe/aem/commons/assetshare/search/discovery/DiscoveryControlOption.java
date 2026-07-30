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
 * A value from the closed vocabulary exposed for a discovery control.
 */
@ProviderType
public final class DiscoveryControlOption {
    private final String value;
    private final String label;
    private final boolean disabled;

    public DiscoveryControlOption(final String value, final String label, final boolean disabled) {
        this.value = value;
        this.label = label;
        this.disabled = disabled;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDisabled() {
        return disabled;
    }
}
