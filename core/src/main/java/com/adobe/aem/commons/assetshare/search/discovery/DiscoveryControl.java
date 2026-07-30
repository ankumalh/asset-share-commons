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
import java.util.List;
import java.util.Map;

/**
 * Safe semantic description of a search-rail control sent to the discovery agent.
 */
@ProviderType
public final class DiscoveryControl {
    public enum Kind {
        CHOICE("choice"),
        TEXT("text"),
        DATE_RANGE("date-range"),
        RELATIVE_DATE("relative-date"),
        PATH("path");

        private final String wireName;

        Kind(final String wireName) {
            this.wireName = wireName;
        }

        public String getWireName() {
            return wireName;
        }
    }

    public enum Cardinality {
        ONE("one"),
        MANY("many");

        private final String wireName;

        Cardinality(final String wireName) {
            this.wireName = wireName;
        }

        public String getWireName() {
            return wireName;
        }
    }

    private final String id;
    private final String title;
    private final Kind kind;
    private final Cardinality cardinality;
    private final DiscoveryControlState state;
    private final List<DiscoveryControlOption> options;
    private final Map<String, Object> constraints;

    public DiscoveryControl(final String id,
                            final String title,
                            final Kind kind,
                            final Cardinality cardinality,
                            final DiscoveryControlState state,
                            final List<DiscoveryControlOption> options,
                            final Map<String, Object> constraints) {
        this.id = id;
        this.title = title;
        this.kind = kind;
        this.cardinality = cardinality;
        this.state = state;
        this.options = options == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(options));
        this.constraints = constraints == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Kind getKind() {
        return kind;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    public DiscoveryControlState getState() {
        return state;
    }

    public List<DiscoveryControlOption> getOptions() {
        return options;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }
}
