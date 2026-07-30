/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.util.ServletHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DiscoveryPageServletTest {
    @Rule
    public final AemContext context = new AemContext();

    private DiscoveryResolver resolver;
    private ServletHelper servletHelper;
    private DiscoveryPageServlet servlet;

    @Before
    public void setUp() {
        resolver = mock(DiscoveryResolver.class);
        servletHelper = mock(ServletHelper.class);
        context.registerService(DiscoveryResolver.class, resolver);
        context.registerService(ServletHelper.class, servletHelper);
        servlet = context.registerInjectActivateService(new DiscoveryPageServlet());
    }

    @Test
    public void returnsCanonicalRedirect() throws Exception {
        context.request().setParameterMap(Collections.<String, Object>singletonMap(
                "prompt", "find JPEGs"));
        when(resolver.resolve(context.request(), "find JPEGs")).thenReturn(
                new DiscoveryResolution("/content/assets.html?format=image%2Fjpeg&p.offset=0"));

        servlet.doPost(context.request(), context.response());

        assertEquals(200, context.response().getStatus());
        assertEquals("no-store", context.response().getHeader("Cache-Control"));
        final JsonObject body = JsonParser.parseString(
                context.response().getOutputAsString()).getAsJsonObject();
        assertEquals(1, body.get("version").getAsInt());
        assertEquals("/content/assets.html?format=image%2Fjpeg&p.offset=0",
                body.get("redirectUrl").getAsString());
        verify(servletHelper).addSlingBindings(context.request(), context.response());
        verify(resolver).resolve(context.request(), "find JPEGs");
    }

    @Test
    public void rejectsMissingPromptWithoutCallingResolver() throws Exception {
        servlet.doPost(context.request(), context.response());

        assertEquals(400, context.response().getStatus());
        final JsonObject body = JsonParser.parseString(
                context.response().getOutputAsString()).getAsJsonObject();
        assertEquals("invalid_request",
                body.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    public void rejectsOversizedPrompt() throws Exception {
        context.request().setParameterMap(Collections.<String, Object>singletonMap(
                "prompt", String.join("", Collections.nCopies(2001, "x"))));

        servlet.doPost(context.request(), context.response());

        assertEquals(400, context.response().getStatus());
    }

    @Test
    public void rejectsOversizedSearchStateBeforeCallingTheAgent() throws Exception {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("prompt", "find JPEGs");
        parameters.put("customer", String.join("", Collections.nCopies(4097, "x")));
        context.request().setParameterMap(parameters);

        servlet.doPost(context.request(), context.response());

        assertEquals(413, context.response().getStatus());
        final JsonObject body = JsonParser.parseString(
                context.response().getOutputAsString()).getAsJsonObject();
        assertEquals("request_too_large",
                body.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    public void returnsStableResolverError() throws Exception {
        context.request().setParameterMap(Collections.<String, Object>singletonMap(
                "prompt", "find JPEGs"));
        when(resolver.resolve(context.request(), "find JPEGs")).thenThrow(
                new DiscoveryResolutionException(
                        502, "invalid_agent_response", "Agent response was invalid."));

        servlet.doPost(context.request(), context.response());

        assertEquals(502, context.response().getStatus());
        final JsonObject body = JsonParser.parseString(
                context.response().getOutputAsString()).getAsJsonObject();
        assertEquals(1, body.get("version").getAsInt());
        assertEquals("invalid_agent_response",
                body.getAsJsonObject("error").get("code").getAsString());
    }

    @Test
    public void returnsStableUnexpectedError() throws Exception {
        context.request().setParameterMap(Collections.<String, Object>singletonMap(
                "prompt", "find JPEGs"));
        when(resolver.resolve(context.request(), "find JPEGs")).thenThrow(
                new IllegalStateException("unexpected"));

        servlet.doPost(context.request(), context.response());

        assertEquals(500, context.response().getStatus());
        final JsonObject body = JsonParser.parseString(
                context.response().getOutputAsString()).getAsJsonObject();
        assertEquals("internal_error",
                body.getAsJsonObject("error").get("code").getAsString());
    }
}
