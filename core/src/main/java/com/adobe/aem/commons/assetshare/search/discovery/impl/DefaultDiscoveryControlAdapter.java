/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery.impl;

import com.adobe.aem.commons.assetshare.components.predicates.DatePredicate;
import com.adobe.aem.commons.assetshare.components.predicates.FreeformTextPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.PathPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.SortPredicate;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControl;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlAdapter;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlOption;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlState;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryParameterUpdate;
import com.adobe.cq.wcm.core.components.models.form.OptionItem;
import com.adobe.cq.wcm.core.components.models.form.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.osgi.framework.Constants.SERVICE_RANKING;

/**
 * Built-in semantic mapping for ASC's public predicate interfaces.
 */
@Component(
        service = DiscoveryControlAdapter.class,
        property = {
                SERVICE_RANKING + ":Integer=" + Integer.MIN_VALUE
        }
)
public class DefaultDiscoveryControlAdapter implements DiscoveryControlAdapter {
    public static final String SORT_ORDERBY_CONTROL_ID = "__asset_share_discovery_sort_orderby";
    public static final String SORT_DIRECTION_CONTROL_ID = "__asset_share_discovery_sort_direction";
    static final String SORT_OPTION_PREFIX = "sort-option-";

    private static final String KIND_RELATIVE_DATE_RANGE = "relativedaterange";

    @Override
    public boolean supports(final Predicate predicate) {
        if (predicate instanceof PropertyPredicate) {
            final PropertyPredicate property = (PropertyPredicate) predicate;
            return hasCommonMapping(property)
                    && StringUtils.isNotBlank(property.getProperty())
                    && StringUtils.isNotBlank(property.getValuesKey())
                    && property.getType() != null;
        }
        if (predicate instanceof DatePredicate) {
            final DatePredicate date = (DatePredicate) predicate;
            return hasCommonMapping(date)
                    && StringUtils.isNotBlank(date.getProperty())
                    && StringUtils.isNotBlank(date.getLowerBoundName())
                    && StringUtils.isNotBlank(date.getUpperBoundName());
        }
        if (predicate instanceof PathPredicate) {
            final PathPredicate path = (PathPredicate) predicate;
            return hasCommonMapping(path) && path.getType() != null;
        }
        if (predicate instanceof FreeformTextPredicate) {
            final FreeformTextPredicate text = (FreeformTextPredicate) predicate;
            return hasCommonMapping(text) && StringUtils.isNotBlank(text.getProperty());
        }
        return predicate instanceof SortPredicate;
    }

    @Override
    public List<DiscoveryControl> describe(final SlingHttpServletRequest request, final Predicate predicate) {
        if (predicate instanceof SortPredicate) {
            return describeSort((SortPredicate) predicate);
        }
        if (predicate instanceof PropertyPredicate) {
            return Collections.singletonList(describeProperty((PropertyPredicate) predicate));
        }
        if (predicate instanceof DatePredicate) {
            return Collections.singletonList(describeDate((DatePredicate) predicate));
        }
        if (predicate instanceof PathPredicate) {
            return Collections.singletonList(describePath((PathPredicate) predicate));
        }
        if (predicate instanceof FreeformTextPredicate) {
            return Collections.singletonList(describeFreeform((FreeformTextPredicate) predicate));
        }
        return Collections.emptyList();
    }

    @Override
    public DiscoveryParameterUpdate toParameterUpdate(final SlingHttpServletRequest request,
                                                      final Predicate predicate,
                                                      final String controlId,
                                                      final DiscoveryControlState state) {
        if (predicate instanceof SortPredicate) {
            return updateSort((SortPredicate) predicate, controlId, state);
        }
        if (predicate instanceof PropertyPredicate) {
            return updateProperty(request, (PropertyPredicate) predicate, state);
        }
        if (predicate instanceof DatePredicate) {
            return updateDate(request, (DatePredicate) predicate, state);
        }
        if (predicate instanceof PathPredicate) {
            return updatePath(request, (PathPredicate) predicate, state);
        }
        if (predicate instanceof FreeformTextPredicate) {
            return updateFreeform(request, (FreeformTextPredicate) predicate, state);
        }
        return new DiscoveryParameterUpdate(Collections.emptySet(), Collections.emptyMap());
    }

    private DiscoveryControl describeProperty(final PropertyPredicate predicate) {
        final List<OptionItem> items = safeItems(predicate.getItems());
        return new DiscoveryControl(
                predicate.getId(),
                title(predicate),
                DiscoveryControl.Kind.CHOICE,
                isSingle(predicate.getType(), predicate.getSubType())
                        ? DiscoveryControl.Cardinality.ONE
                        : DiscoveryControl.Cardinality.MANY,
                DiscoveryControlState.values(selectedValues(items)),
                options(items),
                Collections.emptyMap());
    }

    private DiscoveryControl describeDate(final DatePredicate predicate) {
        if (KIND_RELATIVE_DATE_RANGE.equals(predicate.getName())) {
            final List<OptionItem> items = safeItems(predicate.getItems());
            return new DiscoveryControl(
                    predicate.getId(),
                    title(predicate),
                    DiscoveryControl.Kind.RELATIVE_DATE,
                    DiscoveryControl.Cardinality.ONE,
                    DiscoveryControlState.values(selectedValues(items)),
                    options(items),
                    Collections.emptyMap());
        }

        final Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("format", "YYYY-MM-DD");
        return new DiscoveryControl(
                predicate.getId(),
                title(predicate),
                DiscoveryControl.Kind.DATE_RANGE,
                null,
                DiscoveryControlState.dateRange(
                        semanticDate(predicate.getInitialLowerBound()),
                        semanticDate(predicate.getInitialUpperBound())),
                Collections.emptyList(),
                constraints);
    }

    private DiscoveryControl describePath(final PathPredicate predicate) {
        final List<OptionItem> items = safeItems(predicate.getItems());
        return new DiscoveryControl(
                predicate.getId(),
                title(predicate),
                DiscoveryControl.Kind.PATH,
                isSingle(predicate.getType(), predicate.getSubType())
                        ? DiscoveryControl.Cardinality.ONE
                        : DiscoveryControl.Cardinality.MANY,
                DiscoveryControlState.values(selectedValues(items)),
                options(items),
                Collections.emptyMap());
    }

    private DiscoveryControl describeFreeform(final FreeformTextPredicate predicate) {
        final Map<String, Object> constraints = new LinkedHashMap<>();
        if (predicate.getInputValidationMinLength() != null) {
            constraints.put("minLength", predicate.getInputValidationMinLength());
        }
        if (predicate.getInputValidationMaxLength() != null) {
            constraints.put("maxLength", predicate.getInputValidationMaxLength());
        }
        if (StringUtils.isNotBlank(predicate.getInputValidationPattern())) {
            constraints.put("pattern", predicate.getInputValidationPattern());
        }
        return new DiscoveryControl(
                predicate.getId(),
                StringUtils.defaultIfBlank(predicate.getTitle(), title(predicate)),
                DiscoveryControl.Kind.TEXT,
                DiscoveryControl.Cardinality.ONE,
                DiscoveryControlState.values(singletonIfNotBlank(predicate.getInitialValue())),
                Collections.emptyList(),
                constraints);
    }

    private List<DiscoveryControl> describeSort(final SortPredicate predicate) {
        final List<OptionItem> items = safeItems(predicate.getItems());
        final String orderBy = predicate.getInitialValues().get("orderby", String.class);
        final String direction = predicate.getInitialValues().get("sort", String.class);
        final String selectedSortOption = sortOptionToken(items, orderBy);
        final DiscoveryControl orderByControl = new DiscoveryControl(
                SORT_ORDERBY_CONTROL_ID,
                "SORT BY",
                DiscoveryControl.Kind.CHOICE,
                DiscoveryControl.Cardinality.ONE,
                DiscoveryControlState.values(singletonIfNotBlank(selectedSortOption)),
                sortOptions(items),
                Collections.emptyMap());
        final DiscoveryControl directionControl = new DiscoveryControl(
                SORT_DIRECTION_CONTROL_ID,
                "SORT DIRECTION",
                DiscoveryControl.Kind.CHOICE,
                DiscoveryControl.Cardinality.ONE,
                DiscoveryControlState.values(singletonIfNotBlank(direction)),
                Arrays.asList(
                        new DiscoveryControlOption("asc", "ASC", false),
                        new DiscoveryControlOption("desc", "DESC", false)),
                Collections.emptyMap());
        return Arrays.asList(orderByControl, directionControl);
    }

    private DiscoveryParameterUpdate updateProperty(final SlingHttpServletRequest request,
                                                    final PropertyPredicate predicate,
                                                    final DiscoveryControlState state) {
        final String prefix = predicate.getGroup() + "." + predicate.getName() + ".";
        final Set<String> remove = matchingNames(request, prefix,
                Pattern.compile("^" + Pattern.quote(prefix) + ".*$"));
        final Map<String, List<String>> add = new LinkedHashMap<>();
        final List<String> values = orderedValues(predicate.getItems(), state.getValues());
        addPropertyValues(add, predicate, prefix, values);
        if (!state.getValues().isEmpty()) {
            putIfNotBlank(add, prefix + "property", predicate.getProperty());
            if (predicate.hasOperation()) {
                putIfNotBlank(add, prefix + "operation", predicate.getOperation());
            }
            if (predicate.hasAnd() && predicate.getAnd() != null) {
                putSingle(add, prefix + "and", String.valueOf(predicate.getAnd()));
            }
        }
        return new DiscoveryParameterUpdate(remove, add);
    }

    private DiscoveryParameterUpdate updateDate(final SlingHttpServletRequest request,
                                                final DatePredicate predicate,
                                                final DiscoveryControlState state) {
        final String prefix = predicate.getGroup() + "." + predicate.getName() + ".";
        final String lowerName = predicate.getGroup() + "." + predicate.getLowerBoundName();
        final String upperName = predicate.getGroup() + "." + predicate.getUpperBoundName();
        final Set<String> remove = matchingNames(request, prefix,
                Pattern.compile("^" + Pattern.quote(prefix) + ".*$"));
        remove.addAll(Arrays.asList(lowerName, upperName));
        final Map<String, List<String>> add = new LinkedHashMap<>();

        if (KIND_RELATIVE_DATE_RANGE.equals(predicate.getName())) {
            if (!state.getValues().isEmpty()) {
                putSingle(add, lowerName, state.getValues().get(0));
                putIfNotBlank(add, prefix + "property", predicate.getProperty());
            }
        } else {
            if (StringUtils.isNotBlank(state.getLowerBound())) {
                putSingle(add, lowerName, state.getLowerBound());
            }
            if (StringUtils.isNotBlank(state.getUpperBound())) {
                putSingle(add, upperName, state.getUpperBound() + "T23:59:59.999Z");
            }
            if (StringUtils.isNotBlank(state.getLowerBound())
                    || StringUtils.isNotBlank(state.getUpperBound())) {
                putIfNotBlank(add, prefix + "property", predicate.getProperty());
            }
        }
        return new DiscoveryParameterUpdate(remove, add);
    }

    private DiscoveryParameterUpdate updatePath(final SlingHttpServletRequest request,
                                                final PathPredicate predicate,
                                                final DiscoveryControlState state) {
        final String prefix = predicate.getGroup() + ".";
        final Set<String> remove = matchingNames(request, prefix,
                Pattern.compile("^" + Pattern.quote(prefix) + "(?:\\d+_)?" +
                        Pattern.quote(predicate.getName()) + "$"));
        remove.add(prefix + "p.or");
        final Map<String, List<String>> add = new LinkedHashMap<>();
        final List<String> values = orderedValues(predicate.getItems(), state.getValues());
        addPathValues(add, predicate, prefix, values);
        if (!state.getValues().isEmpty()) {
            putSingle(add, prefix + "p.or", "true");
        }
        return new DiscoveryParameterUpdate(remove, add);
    }

    private DiscoveryParameterUpdate updateFreeform(final SlingHttpServletRequest request,
                                                    final FreeformTextPredicate predicate,
                                                    final DiscoveryControlState state) {
        final String prefix = predicate.getGroup() + "." + predicate.getName() + ".";
        final String name = prefix + "values";
        final Set<String> remove = matchingNames(request, prefix,
                Pattern.compile("^" + Pattern.quote(prefix) + ".*$"));
        remove.addAll(Arrays.asList(name, prefix + "property", prefix + "operation"));
        final Map<String, List<String>> add = new LinkedHashMap<>();
        if (!state.getValues().isEmpty()) {
            putSingle(add, name, state.getValues().get(0));
            putIfNotBlank(add, prefix + "property", predicate.getProperty());
            if (predicate.hasOperation()) {
                putIfNotBlank(add, prefix + "operation", predicate.getOperation());
            }
            final List<String> delimiters = predicate.getDelimiters() == null
                    ? Collections.emptyList()
                    : predicate.getDelimiters();
            for (int index = 0; index < delimiters.size(); index++) {
                putIfNotBlank(add, prefix + index + "_delimiter", delimiters.get(index));
            }
        }
        return new DiscoveryParameterUpdate(remove, add);
    }

    private DiscoveryParameterUpdate updateSort(final SortPredicate predicate,
                                                final String controlId,
                                                final DiscoveryControlState state) {
        final Map<String, List<String>> add = new LinkedHashMap<>();
        if (SORT_ORDERBY_CONTROL_ID.equals(controlId)) {
            if (!state.getValues().isEmpty()) {
                final String orderBy = sortValueForToken(predicate.getItems(), state.getValues().get(0));
                if (StringUtils.isBlank(orderBy)) {
                    throw new IllegalArgumentException("Unknown discovery sort option");
                }
                putSingle(add, "orderby", orderBy);
                final OptionItem selected = safeItems(predicate.getItems()).stream()
                        .filter(item -> StringUtils.equals(item.getValue(), orderBy))
                        .findFirst()
                        .orElse(null);
                if (selected instanceof SortPredicate.SortOptionItem
                        && ((SortPredicate.SortOptionItem) selected).isCaseSensitive()) {
                    putSingle(add, "orderby.case", "");
                } else {
                    putSingle(add, "orderby.case", "ignore");
                }
            }
            return new DiscoveryParameterUpdate(
                    new LinkedHashSet<>(Arrays.asList("orderby", "orderby.case")), add);
        }

        if (!state.getValues().isEmpty()) {
            putSingle(add, "orderby.sort", state.getValues().get(0));
        }
        return new DiscoveryParameterUpdate(Collections.singleton("orderby.sort"), add);
    }

    private void addPropertyValues(final Map<String, List<String>> parameters,
                                   final PropertyPredicate predicate,
                                   final String prefix,
                                   final List<String> values) {
        if (isDropDown(predicate.getType())) {
            for (final String value : values) {
                addValue(parameters, prefix + predicate.getValuesKey(), value);
            }
            return;
        }

        if (isIndexedCheckbox(predicate.getType(), predicate.getSubType())) {
            final Set<String> selected = new LinkedHashSet<>(values);
            final List<OptionItem> items = safeItems(predicate.getItems());
            for (int index = 0; index < items.size(); index++) {
                if (selected.contains(items.get(index).getValue())) {
                    putSingle(parameters, prefix + index + "_" + predicate.getValuesKey(),
                            items.get(index).getValue());
                }
            }
            return;
        }

        if (!values.isEmpty()) {
            putSingle(parameters, prefix + "0_" + predicate.getValuesKey(), values.get(0));
        }
    }

    private void addPathValues(final Map<String, List<String>> parameters,
                               final PathPredicate predicate,
                               final String prefix,
                               final List<String> values) {
        if (isDropDown(predicate.getType())) {
            for (final String value : values) {
                addValue(parameters, prefix + predicate.getName(), value);
            }
            return;
        }

        if (isIndexedCheckbox(predicate.getType(), predicate.getSubType())) {
            final Set<String> selected = new LinkedHashSet<>(values);
            final List<OptionItem> items = safeItems(predicate.getItems());
            for (int index = 0; index < items.size(); index++) {
                if (selected.contains(items.get(index).getValue())) {
                    putSingle(parameters, prefix + index + "_" + predicate.getName(),
                            items.get(index).getValue());
                }
            }
            return;
        }

        if (!values.isEmpty()) {
            putSingle(parameters, prefix + "0_" + predicate.getName(), values.get(0));
        }
    }

    private boolean isDropDown(final Options.Type type) {
        return Options.Type.DROP_DOWN.equals(type) || Options.Type.MULTI_DROP_DOWN.equals(type);
    }

    private boolean isIndexedCheckbox(final Options.Type type, final String subType) {
        return StringUtils.equals("checkbox", subType)
                || (StringUtils.isBlank(subType) && Options.Type.CHECKBOX.equals(type));
    }

    private List<String> orderedValues(final List<OptionItem> items, final List<String> values) {
        final Set<String> selected = new LinkedHashSet<>(values);
        final List<String> ordered = safeItems(items).stream()
                .map(OptionItem::getValue)
                .filter(selected::contains)
                .collect(Collectors.toCollection(ArrayList::new));
        values.stream().filter(value -> !ordered.contains(value)).forEach(ordered::add);
        return ordered;
    }

    private Set<String> matchingNames(final SlingHttpServletRequest request,
                                      final String prefix,
                                      final Pattern pattern) {
        return request.getRequestParameterMap().keySet().stream()
                .filter(name -> name.startsWith(prefix) && pattern.matcher(name).matches())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<DiscoveryControlOption> options(final List<OptionItem> items) {
        final Map<String, DiscoveryControlOption> byValue = new LinkedHashMap<>();
        for (final OptionItem item : items) {
            if (StringUtils.isBlank(item.getValue()) || byValue.containsKey(item.getValue())) {
                continue;
            }
            byValue.put(item.getValue(), new DiscoveryControlOption(
                    item.getValue(),
                    StringUtils.defaultString(item.getText(), item.getValue()),
                    item.isDisabled()));
        }
        return new ArrayList<>(byValue.values());
    }

    private List<DiscoveryControlOption> sortOptions(final List<OptionItem> items) {
        final List<DiscoveryControlOption> options = new ArrayList<>();
        final List<OptionItem> safe = safeItems(items);
        for (int index = 0; index < safe.size(); index++) {
            final OptionItem item = safe.get(index);
            if (StringUtils.isNotBlank(item.getValue())) {
                options.add(new DiscoveryControlOption(
                        SORT_OPTION_PREFIX + index,
                        StringUtils.defaultString(item.getText(), "Sort option " + (index + 1)),
                        item.isDisabled()));
            }
        }
        return options;
    }

    private String sortOptionToken(final List<OptionItem> items, final String value) {
        final List<OptionItem> safe = safeItems(items);
        for (int index = 0; index < safe.size(); index++) {
            if (StringUtils.equals(safe.get(index).getValue(), value)) {
                return SORT_OPTION_PREFIX + index;
            }
        }
        return null;
    }

    private String sortValueForToken(final List<OptionItem> items, final String token) {
        if (!StringUtils.startsWith(token, SORT_OPTION_PREFIX)) {
            return null;
        }
        final String indexValue = StringUtils.substringAfter(token, SORT_OPTION_PREFIX);
        if (!StringUtils.isNumeric(indexValue)) {
            return null;
        }
        final int index;
        try {
            index = Integer.parseInt(indexValue);
        } catch (NumberFormatException e) {
            return null;
        }
        final List<OptionItem> safe = safeItems(items);
        if (index < 0 || index >= safe.size()) {
            return null;
        }
        return safe.get(index).getValue();
    }

    private List<String> selectedValues(final List<OptionItem> items) {
        return items.stream()
                .filter(OptionItem::isSelected)
                .map(OptionItem::getValue)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private boolean isSingle(final Options.Type type, final String subType) {
        if (Options.Type.RADIO.equals(type) || Options.Type.DROP_DOWN.equals(type)) {
            return true;
        }
        if (Options.Type.MULTI_DROP_DOWN.equals(type)) {
            return false;
        }
        return Options.Type.CHECKBOX.equals(type)
                && StringUtils.isNotBlank(subType)
                && !StringUtils.equals("checkbox", subType);
    }

    private List<String> singletonIfNotBlank(final String value) {
        return StringUtils.isBlank(value) ? Collections.emptyList() : Collections.singletonList(value);
    }

    private List<OptionItem> safeItems(final List<OptionItem> items) {
        return items == null ? Collections.emptyList() : items;
    }

    private boolean hasCommonMapping(final Predicate predicate) {
        return StringUtils.isNotBlank(predicate.getId())
                && StringUtils.isNotBlank(predicate.getGroup())
                && StringUtils.isNotBlank(predicate.getName());
    }

    private void putIfNotBlank(final Map<String, List<String>> parameters,
                               final String name,
                               final String value) {
        if (StringUtils.isNotBlank(value)) {
            putSingle(parameters, name, value);
        }
    }

    private void putSingle(final Map<String, List<String>> parameters,
                           final String name,
                           final String value) {
        parameters.put(name, Collections.singletonList(value));
    }

    private void addValue(final Map<String, List<String>> parameters,
                          final String name,
                          final String value) {
        parameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }

    private String semanticDate(final String value) {
        final String trimmed = StringUtils.trimToNull(value);
        if (trimmed != null && trimmed.length() > 10
                && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
            return trimmed.substring(0, 10);
        }
        return trimmed;
    }

    private String title(final Predicate predicate) {
        return StringUtils.defaultIfBlank(predicate.getTitle(), predicate.getName());
    }
}
