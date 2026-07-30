package com.adobe.aem.commons.assetshare.util;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility visitor that walks a Page and collects the models for resources matching at least one of the provided resource Types.
 *
 * This visitor only visits resources with a sling:resourceType.
 *
 * @param <T> The Model type to collect.
 */
public final class ComponentModelVisitor<T> extends ResourceTypeVisitor {
    final Collection<T> models = new ArrayList<>();

    private final SlingHttpServletRequest request;
    private final ModelFactory modelFactory;
    private final List<Class<? extends T>> modelClasses;
    private final Map<String, Class<? extends T>> resourceTypeModelClasses;

    /**
     * @param request the SlingHttpServletRequest object
     * @param modelFactory the ModelFactory object used to construct the Model
     * @param resourceTypes the resource types that will be attempted to be resolved to the T type.
     * @param clazz the Model class the resources should be made into.
     */
    public ComponentModelVisitor(SlingHttpServletRequest request,
                                 ModelFactory modelFactory,
                                 String[] resourceTypes,
                                 Class<T> clazz) {
        super(resourceTypes);
        this.request = request;
        this.modelFactory = modelFactory;
        this.modelClasses = clazz == null
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(clazz));
        this.resourceTypeModelClasses = Collections.emptyMap();
    }

    public ComponentModelVisitor(SlingHttpServletRequest request,
                                 ModelFactory modelFactory,
                                 Class<T> clazz) {
        super(null);
        this.request = request;
        this.modelFactory = modelFactory;
        this.modelClasses = clazz == null
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(clazz));
        this.resourceTypeModelClasses = Collections.emptyMap();
    }

    /**
     * Creates a visitor that selects Sling Model adapter types by component
     * resource type. Resource super types are honored, allowing overlays of a
     * standard component to use its standard model interface.
     *
     * @param request the SlingHttpServletRequest object
     * @param modelFactory the ModelFactory object used to construct the Model
     * @param fallbackClass Model type to try when no resource type matches
     * @param resourceTypeModelClasses resource types and their Model interfaces
     */
    public ComponentModelVisitor(SlingHttpServletRequest request,
                                 ModelFactory modelFactory,
                                 Class<T> fallbackClass,
                                 Map<String, Class<? extends T>> resourceTypeModelClasses) {
        super(null);
        this.request = request;
        this.modelFactory = modelFactory;
        this.modelClasses = fallbackClass == null
                ? new ArrayList<>()
                : new ArrayList<>(Arrays.asList(fallbackClass));
        this.resourceTypeModelClasses = resourceTypeModelClasses == null
                ? Collections.emptyMap()
                : resourceTypeModelClasses;
    }

    /**
     * Note that getModels() may return a SUBSET of getResources(). If a resource matches the resource type check but cannot be turned into a model, the resources will be in getResources() but not in getModels().
     * @return a list of Models representing the visited resources (assuming they match the resourceTypes and can be made into the clazz model type.
     */
    public final Collection<T> getModels() {
        return models;
    }

    @Override
    protected final void visit(Resource resource) {
        if (resourceTypes != null && !ArrayUtils.isEmpty(resourceTypes)) {
            for (final String resourceType : resourceTypes) {
                if (handleResourceVisit(resource, resourceType)) {
                    handleModelVisit(resource);
                    break;
                }
            }
        } else {
            // No resource types specified, so visit all resources and try to turn them into the specified Sling Model.
            handleModelVisit(resource);
        }
    }

    private void handleModelVisit(Resource resource) {
        for (final Map.Entry<String, Class<? extends T>> entry : resourceTypeModelClasses.entrySet()) {
            if (resource.getResourceResolver().isResourceType(resource, entry.getKey())) {
                final T model = modelFactory.getModelFromWrappedRequest(request, resource, entry.getValue());
                if (model != null) {
                    models.add(model);
                    return;
                }
            }
        }

        for (final Class<? extends T> modelClass : modelClasses) {
            if (modelClass != null) {
                final T model = modelFactory.getModelFromWrappedRequest(request, resource, modelClass);

                if (model != null) {
                    models.add(model);
                    break;
                }
            }
        }
    }
}
