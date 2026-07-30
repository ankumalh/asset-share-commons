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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

final class DiscoveryResponseValidator {
    static final int VERSION = 2;
    private static final int MAX_FULLTEXT_LENGTH = 512;
    private static final int MAX_PATH_LENGTH = 1024;
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    DiscoveryValidatedResponse validate(final JsonObject response,
                                        final List<DiscoveryControl> controls,
                                        final List<String> allowedPathRoots)
            throws DiscoveryValidationException {
        requireExactKeys(response, "response", "version", "query", "controlUpdates");
        if (!response.has("version") || !response.get("version").isJsonPrimitive()
                || !response.getAsJsonPrimitive("version").isNumber()
                || response.get("version").getAsInt() != VERSION) {
            fail("response must use version 2");
        }
        if (!response.get("query").isJsonObject() || !response.get("controlUpdates").isJsonArray()) {
            fail("response must contain query and controlUpdates");
        }

        final JsonObject query = response.getAsJsonObject("query");
        requireExactKeys(query, "query", "fulltext", "path");

        final Map<String, DiscoveryControl> byId = new LinkedHashMap<>();
        final Map<String, DiscoveryControlState> resultingStates = new HashMap<>();
        for (final DiscoveryControl control : controls) {
            if (control == null || control.getKind() == null || control.getState() == null
                    || StringUtils.isBlank(control.getId()) || byId.put(control.getId(), control) != null) {
                fail("context contains a missing or duplicate control id");
            }
            final Set<String> optionValues = new HashSet<>();
            for (final DiscoveryControlOption option : control.getOptions()) {
                if (option == null || StringUtils.isBlank(option.getValue())
                        || !optionValues.add(option.getValue())) {
                    fail(control.getId() + " contains an invalid or duplicate option");
                }
            }
            resultingStates.put(control.getId(), control.getState());
        }

        final Set<String> seen = new HashSet<>();
        final List<DiscoveryValidatedResponse.Update> updates = new ArrayList<>();
        for (final JsonElement element : response.getAsJsonArray("controlUpdates")) {
            if (!element.isJsonObject()) {
                fail("control update must be an object");
            }
            final JsonObject update = element.getAsJsonObject();
            requireExactKeys(update, "control update", "id", "state");
            final String id = string(update.get("id"), "control update id", false, -1);
            final DiscoveryControl control = byId.get(id);
            if (control == null) {
                fail("control update has an unknown id");
            }
            if (!seen.add(id)) {
                fail("duplicate control update id " + id);
            }
            if (!update.get("state").isJsonObject()) {
                fail(id + " state must be an object");
            }

            final DiscoveryControlState state = normalizeState(update.getAsJsonObject("state"), control);
            validateState(control, state);
            resultingStates.put(id, state);
            if (!statesEqual(control.getState(), state, control.getKind())) {
                updates.add(new DiscoveryValidatedResponse.Update(id, state));
            }
        }

        final String fulltext = nullableString(query.get("fulltext"), "query.fulltext", MAX_FULLTEXT_LENGTH);
        final String path = validateResidualPath(
                canonicalizePath(nullableString(query.get("path"), "query.path", MAX_PATH_LENGTH)),
                controls,
                allowedPathRoots,
                resultingStates);

        return new DiscoveryValidatedResponse(fulltext, path, updates);
    }

    private DiscoveryControlState normalizeState(final JsonObject state, final DiscoveryControl control)
            throws DiscoveryValidationException {
        if (DiscoveryControl.Kind.DATE_RANGE.equals(control.getKind())) {
            requireExactKeys(state, control.getId() + " state", "lowerBound", "upperBound");
            final String lower = nullableDate(state.get("lowerBound"), control.getId() + ".lowerBound");
            final String upper = nullableDate(state.get("upperBound"), control.getId() + ".upperBound");
            if (lower != null && upper != null && LocalDate.parse(lower).isAfter(LocalDate.parse(upper))) {
                fail(control.getId() + " lowerBound must not be after upperBound");
            }
            return DiscoveryControlState.dateRange(lower, upper);
        }

        requireExactKeys(state, control.getId() + " state", "values");
        if (!state.get("values").isJsonArray()) {
            fail(control.getId() + " values must be an array");
        }
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final JsonElement value : state.getAsJsonArray("values")) {
            final String normalized = string(value, control.getId() + " value", true, -1);
            if (!values.add(normalized)) {
                fail(control.getId() + " contains a duplicate value");
            }
        }
        if (DiscoveryControl.Cardinality.ONE.equals(control.getCardinality()) && values.size() > 1) {
            fail(control.getId() + " allows only one value");
        }
        final Object maxValues = control.getConstraints().get("maxValues");
        if (maxValues instanceof Number && values.size() > ((Number) maxValues).intValue()) {
            fail(control.getId() + " exceeds maxValues");
        }
        return DiscoveryControlState.values(new ArrayList<>(values));
    }

    private void validateState(final DiscoveryControl control, final DiscoveryControlState state)
            throws DiscoveryValidationException {
        final boolean empty = DiscoveryControl.Kind.DATE_RANGE.equals(control.getKind())
                ? state.getLowerBound() == null && state.getUpperBound() == null
                : state.getValues().isEmpty();
        if (Boolean.TRUE.equals(control.getConstraints().get("required")) && empty) {
            fail(control.getId() + " is required");
        }

        if (Arrays.asList(
                DiscoveryControl.Kind.CHOICE,
                DiscoveryControl.Kind.PATH,
                DiscoveryControl.Kind.RELATIVE_DATE).contains(control.getKind())) {
            final Map<String, DiscoveryControlOption> options = control.getOptions().stream()
                    .collect(Collectors.toMap(
                            DiscoveryControlOption::getValue,
                            option -> option,
                            (left, right) -> left,
                            LinkedHashMap::new));
            final List<String> current = control.getState().getValues();
            for (final String value : state.getValues()) {
                final DiscoveryControlOption option = options.get(value);
                if (option == null) {
                    fail(control.getId() + " selected an unknown option");
                }
                if (option.isDisabled() && !current.contains(value)) {
                    fail(control.getId() + " selected a disabled option");
                }
            }
        }

        if (DiscoveryControl.Kind.TEXT.equals(control.getKind())) {
            final Integer min = integerConstraint(control, "minLength");
            final Integer max = integerConstraint(control, "maxLength");
            final String patternValue = stringConstraint(control, "pattern");
            final Pattern pattern;
            try {
                pattern = StringUtils.isBlank(patternValue) ? null : Pattern.compile(patternValue);
            } catch (PatternSyntaxException e) {
                fail(control.getId() + " has an invalid authored pattern");
                return;
            }
            for (final String value : state.getValues()) {
                if (StringUtils.isBlank(value)) {
                    fail(control.getId() + " values must not be blank");
                }
                if (min != null && value.length() < min) {
                    fail(control.getId() + " value is shorter than minLength");
                }
                if (max != null && value.length() > max) {
                    fail(control.getId() + " value exceeds maxLength");
                }
                if (pattern != null && !pattern.matcher(value).matches()) {
                    fail(control.getId() + " value does not match pattern");
                }
            }
        }
    }

    private String validateResidualPath(final String path,
                                        final List<DiscoveryControl> controls,
                                        final List<String> allowedPathRoots,
                                        final Map<String, DiscoveryControlState> resultingStates)
            throws DiscoveryValidationException {
        if (path == null) {
            return null;
        }
        if (allowedPathRoots == null || allowedPathRoots.isEmpty()) {
            fail("query.path is not allowed without allowedPathRoots");
        }

        boolean underRoot = false;
        for (final String rootValue : allowedPathRoots) {
            final String root = canonicalizePath(rootValue);
            if ("/".equals(root) || path.equals(root) || path.startsWith(root + "/")) {
                underRoot = true;
                break;
            }
        }
        if (!underRoot) {
            fail("query.path is outside the allowed roots");
        }

        final Set<String> pathOptions = new HashSet<>();
        for (final DiscoveryControl control : controls) {
            if (!DiscoveryControl.Kind.PATH.equals(control.getKind())) {
                continue;
            }
            control.getOptions().stream().map(DiscoveryControlOption::getValue).forEach(pathOptions::add);
            final DiscoveryControlState state = resultingStates.get(control.getId());
            if (state != null && !state.getValues().isEmpty()) {
                fail("query.path cannot coexist with selected path controls");
            }
        }
        if (pathOptions.contains(path)) {
            fail("query.path must be represented by the path control option");
        }
        return path;
    }

    private String canonicalizePath(final String raw) throws DiscoveryValidationException {
        if (raw == null) {
            return null;
        }
        if (!raw.startsWith("/") || raw.contains("?") || raw.contains("#") || raw.contains("\\")) {
            fail("query.path must be an absolute repository path");
        }
        final List<String> clean = new ArrayList<>();
        for (final String segment : raw.split("/")) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                fail("query.path must not contain traversal segments");
            }
            clean.add(segment);
        }
        return "/" + String.join("/", clean);
    }

    private String nullableDate(final JsonElement value, final String label) throws DiscoveryValidationException {
        final String date = nullableString(value, label, 10);
        if (date != null) {
            if (!DATE_PATTERN.matcher(date).matches()) {
                fail(label + " must use YYYY-MM-DD or null");
            }
            try {
                LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                fail(label + " must be a valid calendar date");
            }
        }
        return date;
    }

    private String nullableString(final JsonElement value, final String label, final int maxLength)
            throws DiscoveryValidationException {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return string(value, label, true, maxLength);
    }

    private String string(final JsonElement value,
                          final String label,
                          final boolean allowEmpty,
                          final int maxLength) throws DiscoveryValidationException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            fail(label + " must be a string");
        }
        final String result = value.getAsString();
        if (!allowEmpty && StringUtils.isBlank(result)) {
            fail(label + " is required");
        }
        if (maxLength >= 0 && result.length() > maxLength) {
            fail(label + " exceeds maximum length");
        }
        return result;
    }

    private Integer integerConstraint(final DiscoveryControl control, final String name) {
        final Object value = control.getConstraints().get(name);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private String stringConstraint(final DiscoveryControl control, final String name) {
        final Object value = control.getConstraints().get(name);
        return value instanceof String ? (String) value : null;
    }

    private boolean statesEqual(final DiscoveryControlState left,
                                final DiscoveryControlState right,
                                final DiscoveryControl.Kind kind) {
        if (DiscoveryControl.Kind.DATE_RANGE.equals(kind)) {
            return StringUtils.equals(left.getLowerBound(), right.getLowerBound())
                    && StringUtils.equals(left.getUpperBound(), right.getUpperBound());
        }
        return left.getValues().equals(right.getValues());
    }

    private void requireExactKeys(final JsonObject object,
                                  final String label,
                                  final String... names) throws DiscoveryValidationException {
        final Set<String> actual = object.keySet();
        final Set<String> expected = new HashSet<>(Arrays.asList(names));
        if (!actual.equals(expected)) {
            fail(label + " contains unexpected or missing fields");
        }
    }

    private void fail(final String message) throws DiscoveryValidationException {
        throw new DiscoveryValidationException(message);
    }
}
