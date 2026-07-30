/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DiscoveryValidatedResponse {
    static final class Update {
        private final String id;
        private final DiscoveryControlState state;

        Update(final String id, final DiscoveryControlState state) {
            this.id = id;
            this.state = state;
        }

        String getId() {
            return id;
        }

        DiscoveryControlState getState() {
            return state;
        }
    }

    private final String fulltext;
    private final String path;
    private final List<Update> updates;

    DiscoveryValidatedResponse(final String fulltext, final String path, final List<Update> updates) {
        this.fulltext = fulltext;
        this.path = path;
        this.updates = Collections.unmodifiableList(new ArrayList<>(updates));
    }

    String getFulltext() {
        return fulltext;
    }

    String getPath() {
        return path;
    }

    List<Update> getUpdates() {
        return updates;
    }
}
