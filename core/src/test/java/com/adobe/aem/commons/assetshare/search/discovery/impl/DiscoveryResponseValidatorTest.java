/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControl;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlOption;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DiscoveryResponseValidatorTest {
    private DiscoveryResponseValidator validator;
    private List<DiscoveryControl> controls;

    @Before
    public void setUp() {
        validator = new DiscoveryResponseValidator();

        final Map<String, Object> textConstraints = new LinkedHashMap<>();
        textConstraints.put("minLength", 2);
        textConstraints.put("maxLength", 12);
        textConstraints.put("pattern", "^[A-Za-z ]+$");

        controls = Arrays.asList(
                new DiscoveryControl(
                        "format", "Format", DiscoveryControl.Kind.CHOICE,
                        DiscoveryControl.Cardinality.ONE,
                        DiscoveryControlState.values(Collections.singletonList("image/png")),
                        Arrays.asList(
                                option("image/jpeg", false),
                                option("image/png", false),
                                option("image/gif", true)),
                        Collections.emptyMap()),
                new DiscoveryControl(
                        "keywords", "Keywords", DiscoveryControl.Kind.TEXT,
                        DiscoveryControl.Cardinality.ONE,
                        DiscoveryControlState.values(Collections.singletonList("summer")),
                        Collections.emptyList(), textConstraints),
                new DiscoveryControl(
                        "created", "Created", DiscoveryControl.Kind.DATE_RANGE, null,
                        DiscoveryControlState.dateRange(null, null),
                        Collections.emptyList(),
                        Collections.<String, Object>singletonMap("format", "YYYY-MM-DD")),
                new DiscoveryControl(
                        "location", "Location", DiscoveryControl.Kind.PATH,
                        DiscoveryControl.Cardinality.ONE,
                        DiscoveryControlState.values(Collections.singletonList("/content/dam/products")),
                        Arrays.asList(
                                option("/content/dam/products", false),
                                option("/content/dam/campaigns", false)),
                        Collections.emptyMap()),
                new DiscoveryControl(
                        DefaultDiscoveryControlAdapter.SORT_DIRECTION_CONTROL_ID,
                        "Sort direction", DiscoveryControl.Kind.CHOICE,
                        DiscoveryControl.Cardinality.ONE,
                        DiscoveryControlState.values(Collections.singletonList("desc")),
                        Arrays.asList(option("asc", false), option("desc", false)),
                        Collections.emptyMap()));
    }

    @Test
    public void validatesPatchSemanticsAndExplicitClear() throws Exception {
        final DiscoveryValidatedResponse response = validator.validate(json(
                "{\"version\":2,\"query\":{\"fulltext\":\"landscape\",\"path\":null},"
                        + "\"controlUpdates\":["
                        + "{\"id\":\"format\",\"state\":{\"values\":[\"image/jpeg\"]}},"
                        + "{\"id\":\"created\",\"state\":{\"lowerBound\":\"2026-07-01\","
                        + "\"upperBound\":\"2026-07-31\"}},"
                        + "{\"id\":\"location\",\"state\":{\"values\":[]}}]}"),
                controls,
                Collections.singletonList("/content/dam"));

        assertEquals("landscape", response.getFulltext());
        assertNull(response.getPath());
        assertEquals(3, response.getUpdates().size());
        assertEquals(Collections.singletonList("image/jpeg"),
                response.getUpdates().get(0).getState().getValues());
        assertEquals(Collections.emptyList(),
                response.getUpdates().get(2).getState().getValues());
    }

    @Test
    public void preservesOmittedAndUnchangedControls() throws Exception {
        final DiscoveryValidatedResponse response = validator.validate(json(
                "{\"version\":2,\"query\":{\"fulltext\":null,\"path\":null},"
                        + "\"controlUpdates\":["
                        + "{\"id\":\"format\",\"state\":{\"values\":[\"image/png\"]}}]}"),
                controls,
                Collections.singletonList("/content/dam"));

        assertEquals(0, response.getUpdates().size());
    }

    @Test
    public void rejectsSchemaIdsDuplicatesOptionsAndCardinality() {
        reject("{\"version\":1,\"query\":{\"fulltext\":null,\"path\":null},\"controlUpdates\":[]}");
        reject("{\"version\":2,\"query\":{\"fulltext\":null,\"path\":null,\"diagnostic\":\"x\"},"
                + "\"controlUpdates\":[]}");
        reject(validUpdate("missing", "{\"values\":[]}"));
        reject("{\"version\":2,\"query\":{\"fulltext\":null,\"path\":null},\"controlUpdates\":["
                + "{\"id\":\"format\",\"state\":{\"values\":[]}},"
                + "{\"id\":\"format\",\"state\":{\"values\":[]}}]}");
        reject(validUpdate("format", "{\"values\":[\"image/tiff\"]}"));
        reject(validUpdate("format", "{\"values\":[\"image/gif\"]}"));
        reject(validUpdate("format", "{\"values\":[\"image/jpeg\",\"image/jpeg\"]}"));
        reject(validUpdate("format", "{\"values\":[\"image/jpeg\",\"image/png\"]}"));
        reject(validUpdate(DefaultDiscoveryControlAdapter.SORT_DIRECTION_CONTROL_ID,
                "{\"values\":[\"down\"]}"));
    }

    @Test
    public void rejectsInvalidTextDatesAndResiduals() {
        reject(validUpdate("keywords", "{\"values\":[\"!\"]}"));
        reject(validUpdate("keywords", "{\"values\":[\"\"]}"));
        reject(validUpdate("created",
                "{\"lowerBound\":\"2026-02-30\",\"upperBound\":null}"));
        reject(validUpdate("created",
                "{\"lowerBound\":\"2026-08-01\",\"upperBound\":\"2026-07-01\"}"));
        reject(query(repeat("x", 513), null, "[]"));
        reject(query(null, "../../etc", "[]"));
        reject(query(null, "/etc/tags", locationClear()));
        reject(query(null, "/content/dam/campaigns", locationClear()));
        reject(query(null, "/content/dam/legal", "[]"));
    }

    @Test
    public void acceptsCanonicalResidualPathWhenPathControlIsCleared() throws Exception {
        final DiscoveryValidatedResponse response = validator.validate(
                json(query(null, "/content//dam/legal/2026/", locationClear())),
                controls,
                Collections.singletonList("/content/dam"));

        assertEquals("/content/dam/legal/2026", response.getPath());
    }

    private void reject(final String response) {
        try {
            validator.validate(json(response), controls, Collections.singletonList("/content/dam"));
            fail("Expected response rejection: " + response);
        } catch (DiscoveryValidationException expected) {
            // expected
        }
    }

    private String validUpdate(final String id, final String state) {
        return "{\"version\":2,\"query\":{\"fulltext\":null,\"path\":null},"
                + "\"controlUpdates\":[{\"id\":\"" + id + "\",\"state\":" + state + "}]}";
    }

    private String query(final String fulltext, final String path, final String updates) {
        return "{\"version\":2,\"query\":{\"fulltext\":" + stringOrNull(fulltext)
                + ",\"path\":" + stringOrNull(path) + "},\"controlUpdates\":" + updates + "}";
    }

    private String locationClear() {
        return "[{\"id\":\"location\",\"state\":{\"values\":[]}}]";
    }

    private String stringOrNull(final String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String repeat(final String value, final int count) {
        return String.join("", Collections.nCopies(count, value));
    }

    private JsonObject json(final String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private DiscoveryControlOption option(final String value, final boolean disabled) {
        return new DiscoveryControlOption(value, value, disabled);
    }
}
