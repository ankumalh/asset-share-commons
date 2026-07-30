/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.util.ServletHelper;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.methods=POST",
                "sling.servlet.resourceTypes=cq:Page",
                "sling.servlet.selectors=discovery",
                "sling.servlet.extensions=json"
        }
)
public class DiscoveryPageServlet extends SlingAllMethodsServlet {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryPageServlet.class);
    private static final Gson GSON = new Gson();
    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final int MAX_PARAMETER_COUNT = 512;
    private static final int MAX_PARAMETER_NAME_LENGTH = 256;
    private static final int MAX_PARAMETER_VALUE_LENGTH = 4096;
    private static final int MAX_PARAMETER_CHARACTERS = 64 * 1024;

    @Reference
    private transient DiscoveryResolver discoveryResolver;

    @Reference
    private transient ServletHelper servletHelper;

    @Override
    protected void doPost(final SlingHttpServletRequest request,
                          final SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        if (!hasAcceptableParameters(request)) {
            writeError(response, 413, "request_too_large",
                    "The discovery request contains too many or oversized parameters.");
            return;
        }

        final String prompt = StringUtils.trimToNull(request.getParameter("prompt"));
        if (prompt == null || prompt.length() > MAX_PROMPT_LENGTH) {
            writeError(response, 400, "invalid_request",
                    "The prompt parameter is required and must not exceed 2000 characters.");
            return;
        }

        try {
            servletHelper.addSlingBindings(request, response);
            final DiscoveryResolution resolution = discoveryResolver.resolve(request, prompt);
            final Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", 1);
            body.put("redirectUrl", resolution.getRedirectUrl());
            response.setStatus(SlingHttpServletResponse.SC_OK);
            response.getWriter().write(GSON.toJson(body));
        } catch (DiscoveryResolutionException e) {
            writeError(response, e.getStatus(), e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected discovery resolution failure", e);
            writeError(response, 500, "internal_error",
                    "The discovery request could not be resolved.");
        }
    }

    private boolean hasAcceptableParameters(final SlingHttpServletRequest request) {
        int parameterCount = 0;
        int characterCount = 0;
        for (final Map.Entry<String, RequestParameter[]> entry
                : request.getRequestParameterMap().entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() > MAX_PARAMETER_NAME_LENGTH) {
                return false;
            }
            characterCount += entry.getKey().length();
            final RequestParameter[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            parameterCount += values.length;
            if (parameterCount > MAX_PARAMETER_COUNT) {
                return false;
            }
            for (final RequestParameter value : values) {
                final String stringValue = value == null ? null : value.getString();
                if (stringValue == null || stringValue.length() > MAX_PARAMETER_VALUE_LENGTH) {
                    return false;
                }
                characterCount += stringValue.length();
                if (characterCount > MAX_PARAMETER_CHARACTERS) {
                    return false;
                }
            }
        }
        return true;
    }

    private void writeError(final SlingHttpServletResponse response,
                            final int status,
                            final String code,
                            final String message) throws IOException {
        final Map<String, String> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", 1);
        body.put("error", Collections.unmodifiableMap(error));
        response.setStatus(status);
        response.getWriter().write(GSON.toJson(body));
    }
}
