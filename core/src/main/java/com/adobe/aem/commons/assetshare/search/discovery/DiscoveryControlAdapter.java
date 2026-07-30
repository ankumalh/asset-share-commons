/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.adobe.aem.commons.assetshare.search.discovery;

import com.adobe.aem.commons.assetshare.components.predicates.Predicate;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.annotation.versioning.ConsumerType;

import java.util.List;

/**
 * Extension point that maps an ASC predicate model to safe discovery controls and back to
 * the predicate's normal HTTP request parameters.
 */
@ConsumerType
public interface DiscoveryControlAdapter {
    /**
     * Determines whether this adapter can describe and translate the supplied model. When multiple
     * adapters support a model, the OSGi service with the highest service ranking is selected.
     *
     * @param predicate current page-scoped predicate model.
     * @return true when this adapter owns the model.
     */
    boolean supports(Predicate predicate);

    /**
     * Produces semantic, agent-safe controls from the model's current server-resolved state.
     * Control IDs and option values must be semantic or opaque; they must not expose component
     * resource paths, JCR property paths, or QueryBuilder parameter names.
     *
     * @param request current discovery POST request.
     * @param predicate current page-scoped predicate model.
     * @return one or more controls with request-unique IDs.
     */
    List<DiscoveryControl> describe(SlingHttpServletRequest request, Predicate predicate);

    /**
     * Completely replaces one mentioned control by removing all of its existing request
     * parameters and adding the validated replacement parameters.
     *
     * @param request current discovery POST request.
     * @param predicate model that produced the control.
     * @param controlId ID returned by {@link #describe(SlingHttpServletRequest, Predicate)}.
     * @param state fully validated replacement state.
     * @return parameter names to remove and canonical replacement values to add.
     */
    DiscoveryParameterUpdate toParameterUpdate(SlingHttpServletRequest request,
                                               Predicate predicate,
                                               String controlId,
                                               DiscoveryControlState state);
}
