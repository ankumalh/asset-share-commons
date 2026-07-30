/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

final class DiscoveryResolutionException extends Exception {
    private final int status;
    private final String code;

    DiscoveryResolutionException(final int status, final String code, final String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    DiscoveryResolutionException(final int status, final String code, final String message, final Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    int getStatus() {
        return status;
    }

    String getCode() {
        return code;
    }
}
