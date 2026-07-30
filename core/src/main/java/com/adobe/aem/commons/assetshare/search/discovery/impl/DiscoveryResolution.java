/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

final class DiscoveryResolution {
    private final String redirectUrl;

    DiscoveryResolution(final String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    String getRedirectUrl() {
        return redirectUrl;
    }
}
