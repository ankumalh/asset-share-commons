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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete request-parameter replacement produced by a discovery control adapter.
 */
@ProviderType
public final class DiscoveryParameterUpdate {
    private final Set<String> removeParameters;
    private final Map<String, List<String>> parameters;

    public DiscoveryParameterUpdate(final Set<String> removeParameters,
                                    final Map<String, List<String>> parameters) {
        this.removeParameters = removeParameters == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(removeParameters));
        if (parameters == null) {
            this.parameters = Collections.emptyMap();
        } else {
            final Map<String, List<String>> copy = new LinkedHashMap<>();
            parameters.forEach((name, values) -> copy.put(
                    name,
                    values == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(values))));
            this.parameters = Collections.unmodifiableMap(copy);
        }
    }

    public Set<String> getRemoveParameters() {
        return removeParameters;
    }

    public Map<String, List<String>> getParameters() {
        return parameters;
    }
}
