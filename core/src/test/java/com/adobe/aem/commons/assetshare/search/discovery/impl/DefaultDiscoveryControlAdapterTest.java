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
import com.adobe.aem.commons.assetshare.components.predicates.PropertyPredicate;
import com.adobe.aem.commons.assetshare.components.predicates.SortPredicate;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControl;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryControlState;
import com.adobe.aem.commons.assetshare.search.discovery.DiscoveryParameterUpdate;
import com.adobe.cq.wcm.core.components.models.form.OptionItem;
import com.adobe.cq.wcm.core.components.models.form.Options;
import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultDiscoveryControlAdapterTest {
    @Rule
    public final AemContext context = new AemContext();

    private DefaultDiscoveryControlAdapter adapter;

    @Before
    public void setUp() {
        adapter = new DefaultDiscoveryControlAdapter();
    }

    @Test
    public void describesAndReplacesPropertyPredicate() {
        final PropertyPredicate predicate = mock(PropertyPredicate.class);
        final OptionItem jpeg = option("image/jpeg", false, false);
        final OptionItem png = option("image/png", true, false);
        when(predicate.getId()).thenReturn("format");
        when(predicate.getTitle()).thenReturn("Format");
        when(predicate.getName()).thenReturn("propertyvalues");
        when(predicate.getGroup()).thenReturn("4_group");
        when(predicate.getValuesKey()).thenReturn("values");
        when(predicate.getProperty()).thenReturn("jcr:content/metadata/dc:format");
        when(predicate.hasOperation()).thenReturn(true);
        when(predicate.getOperation()).thenReturn("equals");
        when(predicate.hasAnd()).thenReturn(true);
        when(predicate.getAnd()).thenReturn(true);
        when(predicate.getType()).thenReturn(Options.Type.CHECKBOX);
        when(predicate.getSubType()).thenReturn("checkbox");
        when(predicate.getItems()).thenReturn(Arrays.asList(jpeg, png));

        final Map<String, Object> requestParameters = new HashMap<>();
        requestParameters.put("4_group.propertyvalues.0_values", "image/jpeg");
        requestParameters.put("4_group.propertyvalues.1_values", "image/png");
        requestParameters.put("4_group.propertyvalues.property",
                "jcr:content/metadata/dc:format");
        requestParameters.put("customer", "preserved");
        context.request().setParameterMap(requestParameters);

        final DiscoveryControl control = adapter.describe(context.request(), predicate).get(0);
        assertEquals(DiscoveryControl.Kind.CHOICE, control.getKind());
        assertEquals(DiscoveryControl.Cardinality.MANY, control.getCardinality());
        assertEquals(Collections.singletonList("image/png"), control.getState().getValues());

        final DiscoveryParameterUpdate update = adapter.toParameterUpdate(
                context.request(), predicate, "format",
                DiscoveryControlState.values(Collections.singletonList("image/png")));
        assertTrue(update.getRemoveParameters().contains("4_group.propertyvalues.0_values"));
        assertTrue(update.getRemoveParameters().contains("4_group.propertyvalues.1_values"));
        assertFalse(update.getParameters().containsKey("4_group.propertyvalues.0_values"));
        assertEquals(Collections.singletonList("image/png"),
                update.getParameters().get("4_group.propertyvalues.1_values"));
        assertEquals("jcr:content/metadata/dc:format",
                value(update, "4_group.propertyvalues.property"));
        assertEquals("equals", value(update, "4_group.propertyvalues.operation"));
        assertEquals("true", value(update, "4_group.propertyvalues.and"));
        assertFalse(update.getRemoveParameters().contains("customer"));

        final DiscoveryParameterUpdate clear = adapter.toParameterUpdate(
                context.request(), predicate, "format",
                DiscoveryControlState.values(Collections.emptyList()));
        assertTrue(clear.getRemoveParameters().contains("4_group.propertyvalues.property"));
        assertTrue(clear.getParameters().isEmpty());
    }

    @Test
    public void reproducesPropertyDropDownAndMultiDropDownParameterShapes() {
        final PropertyPredicate predicate = propertyPredicate(Options.Type.DROP_DOWN);
        final DiscoveryParameterUpdate single = adapter.toParameterUpdate(
                context.request(), predicate, "format",
                DiscoveryControlState.values(Collections.singletonList("image/png")));
        assertEquals(Collections.singletonList("image/png"),
                single.getParameters().get("4_group.propertyvalues.values"));
        assertFalse(single.getParameters().containsKey("4_group.propertyvalues.0_values"));

        when(predicate.getType()).thenReturn(Options.Type.MULTI_DROP_DOWN);
        final DiscoveryParameterUpdate multiple = adapter.toParameterUpdate(
                context.request(), predicate, "format",
                DiscoveryControlState.values(Arrays.asList("image/png", "image/jpeg")));
        assertEquals(Arrays.asList("image/jpeg", "image/png"),
                multiple.getParameters().get("4_group.propertyvalues.values"));
    }

    @Test
    public void derivesToggleSliderAndCheckboxCardinalityFromSubtype() {
        final PropertyPredicate property = propertyPredicate(Options.Type.CHECKBOX);
        when(property.getSubType()).thenReturn("toggle");
        assertEquals(DiscoveryControl.Cardinality.ONE,
                adapter.describe(context.request(), property).get(0).getCardinality());

        when(property.getSubType()).thenReturn("slider");
        assertEquals(DiscoveryControl.Cardinality.ONE,
                adapter.describe(context.request(), property).get(0).getCardinality());

        when(property.getSubType()).thenReturn("checkbox");
        assertEquals(DiscoveryControl.Cardinality.MANY,
                adapter.describe(context.request(), property).get(0).getCardinality());

        final PathPredicate path = mock(PathPredicate.class);
        when(path.getId()).thenReturn("location");
        when(path.getTitle()).thenReturn("Location");
        when(path.getName()).thenReturn("path");
        when(path.getGroup()).thenReturn("9_group");
        when(path.getType()).thenReturn(Options.Type.CHECKBOX);
        when(path.getSubType()).thenReturn("radio");
        final OptionItem products = option("/content/dam/products", false, false);
        final OptionItem campaigns = option("/content/dam/campaigns", false, false);
        when(path.getItems()).thenReturn(Arrays.asList(products, campaigns));
        assertEquals(DiscoveryControl.Cardinality.ONE,
                adapter.describe(context.request(), path).get(0).getCardinality());
    }

    @Test
    public void describesAndReplacesAbsoluteAndRelativeDates() {
        final DatePredicate absolute = mock(DatePredicate.class);
        when(absolute.getId()).thenReturn("created");
        when(absolute.getTitle()).thenReturn("Created");
        when(absolute.getName()).thenReturn("daterange");
        when(absolute.getGroup()).thenReturn("7_group");
        when(absolute.getProperty()).thenReturn("jcr:content/metadata/dc:created");
        when(absolute.getLowerBoundName()).thenReturn("daterange.lowerBound");
        when(absolute.getUpperBoundName()).thenReturn("daterange.upperBound");
        when(absolute.getInitialLowerBound()).thenReturn("2026-07-01");
        when(absolute.getInitialUpperBound()).thenReturn("2026-07-31T23:59:59.999Z");

        final DiscoveryControl absoluteControl = adapter.describe(context.request(), absolute).get(0);
        assertEquals("2026-07-31", absoluteControl.getState().getUpperBound());

        final DiscoveryParameterUpdate absoluteUpdate = adapter.toParameterUpdate(
                context.request(), absolute, "created",
                DiscoveryControlState.dateRange("2026-07-10", "2026-07-20"));
        assertEquals("2026-07-10", value(absoluteUpdate, "7_group.daterange.lowerBound"));
        assertEquals("2026-07-20T23:59:59.999Z",
                value(absoluteUpdate, "7_group.daterange.upperBound"));
        assertEquals("jcr:content/metadata/dc:created",
                value(absoluteUpdate, "7_group.daterange.property"));

        final DatePredicate relative = mock(DatePredicate.class);
        final OptionItem lastDay = option("-1d", false, false);
        final OptionItem lastMonth = option("-1M", true, false);
        when(relative.getId()).thenReturn("recent");
        when(relative.getTitle()).thenReturn("Modified");
        when(relative.getName()).thenReturn("relativedaterange");
        when(relative.getGroup()).thenReturn("8_group");
        when(relative.getProperty()).thenReturn("jcr:content/jcr:lastModified");
        when(relative.getLowerBoundName()).thenReturn("relativedaterange.lowerBound");
        when(relative.getUpperBoundName()).thenReturn("relativedaterange.upperBound");
        when(relative.getItems()).thenReturn(Arrays.asList(lastDay, lastMonth));

        assertEquals(DiscoveryControl.Kind.RELATIVE_DATE,
                adapter.describe(context.request(), relative).get(0).getKind());
        final DiscoveryParameterUpdate relativeUpdate = adapter.toParameterUpdate(
                context.request(), relative, "recent",
                DiscoveryControlState.values(Collections.singletonList("-1d")));
        assertEquals("-1d",
                value(relativeUpdate, "8_group.relativedaterange.lowerBound"));
        assertEquals("jcr:content/jcr:lastModified",
                value(relativeUpdate, "8_group.relativedaterange.property"));
    }

    @Test
    public void rejectsDatePredicateWithoutAPropertyMapping() {
        final DatePredicate predicate = mock(DatePredicate.class);
        when(predicate.getId()).thenReturn("created");
        when(predicate.getName()).thenReturn("daterange");
        when(predicate.getGroup()).thenReturn("7_group");
        when(predicate.getLowerBoundName()).thenReturn("daterange.lowerBound");
        when(predicate.getUpperBoundName()).thenReturn("daterange.upperBound");

        assertFalse(adapter.supports(predicate));

        when(predicate.getProperty()).thenReturn("jcr:content/jcr:created");
        assertTrue(adapter.supports(predicate));
    }

    @Test
    public void replacesPathAndFreeformParameters() {
        final PathPredicate path = mock(PathPredicate.class);
        final OptionItem products = option("/content/dam/products", true, false);
        final OptionItem campaigns = option("/content/dam/campaigns", false, false);
        when(path.getId()).thenReturn("location");
        when(path.getTitle()).thenReturn("Location");
        when(path.getName()).thenReturn("path");
        when(path.getGroup()).thenReturn("9_group");
        when(path.getType()).thenReturn(Options.Type.DROP_DOWN);
        when(path.getItems()).thenReturn(Arrays.asList(products, campaigns));
        context.request().setParameterMap(Collections.<String, Object>singletonMap(
                "9_group.path", "/content/dam/products"));

        final DiscoveryParameterUpdate pathUpdate = adapter.toParameterUpdate(
                context.request(), path, "location",
                DiscoveryControlState.values(Collections.singletonList("/content/dam/campaigns")));
        assertTrue(pathUpdate.getRemoveParameters().contains("9_group.path"));
        assertEquals("/content/dam/campaigns", value(pathUpdate, "9_group.path"));
        assertFalse(pathUpdate.getParameters().containsKey("9_group.0_path"));
        assertEquals("true", value(pathUpdate, "9_group.p.or"));

        final FreeformTextPredicate text = mock(FreeformTextPredicate.class);
        when(text.getId()).thenReturn("keywords");
        when(text.getTitle()).thenReturn("Keywords");
        when(text.getName()).thenReturn("propertyvalues");
        when(text.getGroup()).thenReturn("10_group");
        when(text.getProperty()).thenReturn("jcr:content/metadata/customer/keywords");
        when(text.hasOperation()).thenReturn(true);
        when(text.getOperation()).thenReturn("equals");
        when(text.getInitialValue()).thenReturn("summer|legal");
        when(text.getDelimiters()).thenReturn(Arrays.asList("|", "__WS"));
        when(text.getInputValidationMinLength()).thenReturn(2);
        when(text.getInputValidationMaxLength()).thenReturn(20);
        when(text.getInputValidationPattern()).thenReturn("^[a-z|]+$");
        final Map<String, Object> textRequestParameters = new HashMap<>();
        textRequestParameters.put("10_group.propertyvalues.values", "summer|legal");
        textRequestParameters.put("10_group.propertyvalues.0_delimiter", "|");
        textRequestParameters.put("10_group.propertyvalues.1_delimiter", "__WS");
        textRequestParameters.put("10_group.propertyvalues.property",
                "jcr:content/metadata/customer/keywords");
        context.request().setParameterMap(textRequestParameters);

        final DiscoveryControl textControl = adapter.describe(context.request(), text).get(0);
        assertEquals(DiscoveryControl.Cardinality.ONE, textControl.getCardinality());
        assertEquals(Collections.singletonList("summer|legal"), textControl.getState().getValues());
        final DiscoveryParameterUpdate textUpdate = adapter.toParameterUpdate(
                context.request(), text, "keywords",
                DiscoveryControlState.values(Collections.singletonList("red|blue")));
        assertEquals("red|blue",
                value(textUpdate, "10_group.propertyvalues.values"));
        assertEquals("jcr:content/metadata/customer/keywords",
                value(textUpdate, "10_group.propertyvalues.property"));
        assertEquals("equals",
                value(textUpdate, "10_group.propertyvalues.operation"));
        assertEquals("|", value(textUpdate, "10_group.propertyvalues.0_delimiter"));
        assertEquals("__WS", value(textUpdate, "10_group.propertyvalues.1_delimiter"));

        final DiscoveryParameterUpdate clear = adapter.toParameterUpdate(
                context.request(), text, "keywords",
                DiscoveryControlState.values(Collections.emptyList()));
        assertTrue(clear.getRemoveParameters().contains("10_group.propertyvalues.0_delimiter"));
        assertTrue(clear.getRemoveParameters().contains("10_group.propertyvalues.1_delimiter"));
        assertTrue(clear.getParameters().isEmpty());
    }

    @Test
    public void reproducesPathCheckboxAndMultiDropDownParameterShapes() {
        final PathPredicate path = mock(PathPredicate.class);
        when(path.getId()).thenReturn("location");
        when(path.getTitle()).thenReturn("Location");
        when(path.getName()).thenReturn("path");
        when(path.getGroup()).thenReturn("9_group");
        when(path.getType()).thenReturn(Options.Type.CHECKBOX);
        when(path.getSubType()).thenReturn("checkbox");
        final List<OptionItem> pathItems = Arrays.asList(
                option("/content/dam/products", false, false),
                option("/content/dam/campaigns", false, false));
        when(path.getItems()).thenReturn(pathItems);

        final DiscoveryParameterUpdate checkbox = adapter.toParameterUpdate(
                context.request(), path, "location",
                DiscoveryControlState.values(Collections.singletonList(
                        "/content/dam/campaigns")));
        assertEquals("/content/dam/campaigns", value(checkbox, "9_group.1_path"));
        assertFalse(checkbox.getParameters().containsKey("9_group.0_path"));

        when(path.getType()).thenReturn(Options.Type.MULTI_DROP_DOWN);
        final DiscoveryParameterUpdate multi = adapter.toParameterUpdate(
                context.request(), path, "location",
                DiscoveryControlState.values(Arrays.asList(
                        "/content/dam/campaigns", "/content/dam/products")));
        assertEquals(Arrays.asList("/content/dam/products", "/content/dam/campaigns"),
                multi.getParameters().get("9_group.path"));
    }

    @Test
    public void describesAndReplacesSortFieldAndDirection() {
        final SortPredicate sort = mock(SortPredicate.class);
        final SortPredicate.SortOptionItem lastModified = mock(SortPredicate.SortOptionItem.class);
        final SortPredicate.SortOptionItem size = mock(SortPredicate.SortOptionItem.class);
        when(lastModified.getValue()).thenReturn("@jcr:content/jcr:lastModified");
        when(lastModified.getText()).thenReturn("Last modified");
        when(lastModified.isSelected()).thenReturn(true);
        when(size.getValue()).thenReturn("jcr:content/metadata/dam:size");
        when(size.getText()).thenReturn("Size");
        when(size.isCaseSensitive()).thenReturn(true);
        when(sort.getItems()).thenReturn(Arrays.<OptionItem>asList(lastModified, size));

        final Map<String, Object> initial = new HashMap<>();
        initial.put("orderby", "@jcr:content/jcr:lastModified");
        initial.put("sort", "desc");
        final ValueMap initialValues = new ValueMapDecorator(initial);
        when(sort.getInitialValues()).thenReturn(initialValues);

        final List<DiscoveryControl> controls = adapter.describe(context.request(), sort);
        assertEquals(2, controls.size());
        assertEquals(DefaultDiscoveryControlAdapter.SORT_ORDERBY_CONTROL_ID, controls.get(0).getId());
        assertEquals(Collections.singletonList(
                DefaultDiscoveryControlAdapter.SORT_OPTION_PREFIX + "0"),
                controls.get(0).getState().getValues());
        assertEquals(DefaultDiscoveryControlAdapter.SORT_OPTION_PREFIX + "1",
                controls.get(0).getOptions().get(1).getValue());
        assertFalse(controls.get(0).getOptions().toString().contains("jcr:content"));
        assertEquals(Collections.singletonList("desc"), controls.get(1).getState().getValues());

        final DiscoveryParameterUpdate fieldUpdate = adapter.toParameterUpdate(
                context.request(), sort, DefaultDiscoveryControlAdapter.SORT_ORDERBY_CONTROL_ID,
                DiscoveryControlState.values(Collections.singletonList(
                        DefaultDiscoveryControlAdapter.SORT_OPTION_PREFIX + "1")));
        assertEquals("jcr:content/metadata/dam:size", value(fieldUpdate, "orderby"));
        assertEquals("", value(fieldUpdate, "orderby.case"));

        final DiscoveryParameterUpdate directionUpdate = adapter.toParameterUpdate(
                context.request(), sort, DefaultDiscoveryControlAdapter.SORT_DIRECTION_CONTROL_ID,
                DiscoveryControlState.values(Collections.singletonList("asc")));
        assertEquals("asc", value(directionUpdate, "orderby.sort"));
    }

    private PropertyPredicate propertyPredicate(final Options.Type type) {
        final PropertyPredicate predicate = mock(PropertyPredicate.class);
        when(predicate.getId()).thenReturn("format");
        when(predicate.getTitle()).thenReturn("Format");
        when(predicate.getName()).thenReturn("propertyvalues");
        when(predicate.getGroup()).thenReturn("4_group");
        when(predicate.getValuesKey()).thenReturn("values");
        when(predicate.getProperty()).thenReturn("jcr:content/metadata/dc:format");
        when(predicate.getType()).thenReturn(type);
        final List<OptionItem> items = Arrays.asList(
                option("image/jpeg", false, false),
                option("image/png", false, false));
        when(predicate.getItems()).thenReturn(items);
        return predicate;
    }

    private String value(final DiscoveryParameterUpdate update, final String name) {
        return update.getParameters().get(name).get(0);
    }

    private OptionItem option(final String value, final boolean selected, final boolean disabled) {
        final OptionItem item = mock(OptionItem.class);
        when(item.getValue()).thenReturn(value);
        when(item.getText()).thenReturn(value);
        when(item.isSelected()).thenReturn(selected);
        when(item.isDisabled()).thenReturn(disabled);
        return item;
    }
}
