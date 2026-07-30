/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery;

import org.osgi.annotation.versioning.ProviderType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validated semantic state for a discovery control.
 */
@ProviderType
public final class DiscoveryControlState {
    private final List<String> values;
    private final String lowerBound;
    private final String upperBound;

    private DiscoveryControlState(final List<String> values, final String lowerBound, final String upperBound) {
        this.values = values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public static DiscoveryControlState values(final List<String> values) {
        return new DiscoveryControlState(values, null, null);
    }

    public static DiscoveryControlState dateRange(final String lowerBound, final String upperBound) {
        return new DiscoveryControlState(Collections.emptyList(), lowerBound, upperBound);
    }

    public List<String> getValues() {
        return values;
    }

    public String getLowerBound() {
        return lowerBound;
    }

    public String getUpperBound() {
        return upperBound;
    }
}
