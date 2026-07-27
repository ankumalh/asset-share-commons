/*
 * Asset Share Commons
 *
 * Copyright [2017]  Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*global jQuery: false, AssetShare: false, window: false */

AssetShare.Search = (function (window, $, ns, ajax) {
    "use strict";

    var EVENT_SEARCH_TYPE_FULL = "search",
        EVENT_SEARCH_TYPE_LOAD_MORE = "load-more",

        ACTION_SEARCH = "search",
        ACTION_DEEP_LINK = "deep-link",
        ACTION_LOAD_MORE = "load-more",
        ACTION_SORT = "sort",
        ACTION_SWITCH_LAYOUT = "switch-layout",
        DISCOVERY_COMMAND = "/discovery",

        running = false,
        activeDiscoveryQuery = null,
        activeDiscoveryPrompt = null,
        activeRequestQuery = null,
        dirtyDiscoveryPredicateIds = {},
        applyingRailSelections = false,

        form = ns.Search.Form(ns);

    function getForm() {
        return form;
    }

    function trigger(eventType, params) {
        $("body").trigger(eventType, params);
    }

    function setAddressBar(queyParams) {
        if (ns.Util.isSameOrigin()) {
            ns.Navigation.addressBar(window.top.location.pathname + "?" + queyParams);
        } else {
            ns.Navigation.addressBar(window.location.pathname + "?" + queyParams);
        }

        ns.Navigation.returnUrl(window.location.pathname + "?" + queyParams);
    }

    function processSearch(fragmentHtml) {
        ns.Elements.update(fragmentHtml, ACTION_SEARCH);

        ns.Navigation.gotoTop();
        setAddressBar(activeRequestQuery || form.serializeFor(ACTION_DEEP_LINK));
        activeRequestQuery = null;

        trigger(ns.Events.SEARCH_END, [EVENT_SEARCH_TYPE_FULL]);
        running = false;
    }

    function getDiscoveryQueryElement() {
        return ns.Elements.element("discovery-query");
    }

    function getDiscoveryQueryOutputElement() {
        return ns.Elements.element("discovery-query-output");
    }

    function getDiscoveryQueryTitleElement() {
        return ns.Elements.element("discovery-query-title");
    }

    function getDiscoveryEndpoint() {
        return ns.Data.attr(
            ns.Elements.element("discovery-search"),
            "discovery-agent-endpoint"
        ) || "/bin/asset-share-commons/discovery";
    }

    function isDiscoveryCommandValue(value) {
        return value && new RegExp("^" + DISCOVERY_COMMAND + "(\\s|$)").test($.trim(value));
    }

    function getDiscoverySearchInputs() {
        return $("#" + form.id()).find(":input").add($("[form='" + form.id() + "']")).filter(function() {
            return isDiscoveryCommandValue($(this).val());
        });
    }

    function getSearchPrompt() {
        var searchInput = getDiscoverySearchInputs().first();

        return searchInput.length ?
            $.trim(searchInput.val()).substring(DISCOVERY_COMMAND.length).replace(/^\s+/, "") :
            "";
    }

    function getDiscoverySearchFieldNames() {
        return getDiscoverySearchInputs().map(function() {
            return $(this).attr("name");
        }).get();
    }

    function hasDiscoveryCommand() {
        return getDiscoverySearchInputs().length > 0;
    }

    function clearDiscoveryState() {
        activeDiscoveryQuery = null;
        activeDiscoveryPrompt = null;
        dirtyDiscoveryPredicateIds = {};
    }

    function markDiscoveryPredicateDirty() {
        var predicateId = $(this).attr("for");

        if (activeDiscoveryQuery && predicateId) {
            dirtyDiscoveryPredicateIds[predicateId] = true;
        }
    }

    function objectToQueryString(queryObject) {
        var params = [];

        if (!queryObject) {
            return "";
        }

        $.each(queryObject, function(key, value) {
            if (Array.isArray(value)) {
                value.forEach(function(arrayValue) {
                    params.push({name: key, value: arrayValue});
                });
            } else {
                params.push({name: key, value: value});
            }
        });

        return $.param(params);
    }

    function parseDiscoveryResponse(response) {
        var query = response;

        if (!query) {
            return "";
        }

        if (typeof query === "string") {
            try {
                query = JSON.parse(query);
            } catch (e) {
                return query.indexOf("=") > -1 ? query : "";
            }
        }

        if (!query || typeof query !== "object" || Array.isArray(query) || query.error) {
            return "";
        }

        if (typeof query.query !== "undefined") {
            query = query.query;
        } else if (typeof query.queryBuilderQuery !== "undefined") {
            query = query.queryBuilderQuery;
        } else if (typeof query.querybuilder !== "undefined") {
            query = query.querybuilder;
        } else if (typeof query.queryBuilder !== "undefined") {
            query = query.queryBuilder;
        } else if (typeof query.queryParameters !== "undefined") {
            query = query.queryParameters;
        } else if (typeof query.params !== "undefined") {
            query = query.params;
        }

        if (typeof query === "string") {
            return query.indexOf("=") > -1 ? query : "";
        }

        if (!query || typeof query !== "object" || Array.isArray(query)) {
            return "";
        }

        return objectToQueryString(query);
    }

    function normalizeOrderByValue(value) {
        var trimmed = $.trim(value);

        // QueryBuilder sorts by a JCR property only when the value is prefixed with "@"
        // (e.g. "@jcr:content/metadata/dam:size"). The keyword sorts "path" and "nodename",
        // and already-qualified values, are left untouched.
        if (trimmed === "" ||
            trimmed.charAt(0) === "@" ||
            trimmed === "path" ||
            trimmed === "nodename") {
            return value;
        }

        return "@" + trimmed;
    }

    function normalizeDiscoveryQuery(query) {
        // The discovery agent may return an "orderby" property sort without the "@" prefix
        // QueryBuilder requires; add it so the sort is honored. Only the "orderby" param is
        // touched (not orderby.sort / orderby.case).
        if (!query || query.indexOf("orderby=") === -1) {
            return query;
        }

        return $.map(query.split("&"), function(pair) {
            var separator = pair.indexOf("="),
                name = separator > -1 ? pair.substring(0, separator) : pair,
                value = separator > -1 ? pair.substring(separator + 1) : "",
                decodedName,
                decodedValue;

            try {
                decodedName = decodeURIComponent(name.replace(/\+/g, " "));
            } catch (e) {
                return pair;
            }

            if (decodedName !== "orderby") {
                return pair;
            }

            try {
                decodedValue = decodeURIComponent(value.replace(/\+/g, " "));
            } catch (e) {
                return pair;
            }

            return name + "=" + encodeURIComponent(normalizeOrderByValue(decodedValue));
        }).join("&");
    }

    function showDiscoveryQuery(query) {
        var queryElement = getDiscoveryQueryElement(),
            outputElement = getDiscoveryQueryOutputElement(),
            titleElement = getDiscoveryQueryTitleElement(),
            decodedQuery;

        try {
            decodedQuery = decodeURIComponent(query.replace(/\+/g, " "));
        } catch (e) {
            decodedQuery = query;
        }

        titleElement.text(ns.Data.attr(queryElement, "query-title"));
        outputElement.text(decodedQuery);
        queryElement.removeClass("hidden negative");
    }

    function hideDiscoveryQuery() {
        var queryElement = getDiscoveryQueryElement();

        getDiscoveryQueryOutputElement().empty();
        queryElement.addClass("hidden").removeClass("negative");
    }

    function showDiscoveryError() {
        var queryElement = getDiscoveryQueryElement();

        getDiscoveryQueryTitleElement().text(ns.Data.attr(queryElement, "error-title"));
        getDiscoveryQueryOutputElement().text(ns.Data.attr(queryElement, "error-message"));
        queryElement.removeClass("hidden").addClass("negative");
    }

    function searchFailed(searchType, discoveryFailure) {
        activeRequestQuery = null;
        if (discoveryFailure) {
            showDiscoveryError();
        }
        trigger(ns.Events.SEARCH_INVALID, [searchType]);
        running = false;
    }

    function submitDiscoveryQuery(action, success, searchType) {
        var query = form.serializeDiscoveryQueryFor(
            activeDiscoveryQuery,
            action,
            Object.keys(dirtyDiscoveryPredicateIds)
        );

        activeRequestQuery = query;
        form.submitQuery(query, function(fragmentHtml) {
            activeDiscoveryQuery = query;
            dirtyDiscoveryPredicateIds = {};
            success(fragmentHtml);
        }).fail(function() {
            searchFailed(searchType, true);
        });
    }

    function getRailPredicateInputs() {
        var formId = form.id(),
            inputs = $(),
            seenPredicateIds = {};

        $("[data-asset-share-predicate-id][form=\"" + formId + "\"]").each(function() {
            var predicateId = ns.Data.attr($(this), "predicate-id");

            if (!predicateId || seenPredicateIds[predicateId]) {
                return;
            }
            seenPredicateIds[predicateId] = true;

            inputs = inputs.add($(":input[for=\"" + predicateId + "\"][form=\"" + formId + "\"]"));
        });

        return inputs;
    }

    function applyDiscoveryQueryToRail(query) {
        var lookup = {};

        form.deserialize(query).getAll().forEach(function(field) {
            if (!lookup[field.name]) {
                lookup[field.name] = [];
            }
            lookup[field.name].push(field.value);
        });

        // Suppress the auto-search "change"/"click" bindings (search.js:registerEvents) while
        // these rail inputs are set programmatically, since many predicates auto-submit on change.
        applyingRailSelections = true;

        try {
            getRailPredicateInputs().each(function() {
                var input = $(this),
                    values = lookup[input.attr("name")] || [];

                if (input.is(":checkbox, :radio")) {
                    input.prop("checked", values.indexOf(input.val()) > -1);
                } else if (input.is("select") && input.prop("multiple")) {
                    input.find("option").each(function() {
                        $(this).prop("selected", values.indexOf($(this).val()) > -1);
                    });
                } else {
                    input.val(values.length ? values[0] : "");
                }
            });
        } finally {
            applyingRailSelections = false;
        }
    }

    function discoverySearch() {
        var prompt = getSearchPrompt(),
            context = form.serializeDiscoveryContextFor(
                ACTION_SEARCH,
                true,
                getDiscoverySearchFieldNames()
            );

        clearDiscoveryState();
        hideDiscoveryQuery();

        $.when($.post(getDiscoveryEndpoint(), {
            prompt: prompt,
            context: context
        })).then(function(response) {
            var query = normalizeDiscoveryQuery(parseDiscoveryResponse(response));

            if (!query) {
                searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                return;
            }

            form.applyDiscoverySort(query);
            showDiscoveryQuery(query);
            activeDiscoveryQuery = query;
            activeDiscoveryPrompt = prompt;
            applyDiscoveryQueryToRail(query);
            submitDiscoveryQuery(ACTION_SEARCH, processSearch, EVENT_SEARCH_TYPE_FULL);
        }).fail(function() {
            clearDiscoveryState();
            searchFailed(EVENT_SEARCH_TYPE_FULL, true);
        });
    }

    function processLoadMore(fragmentHtml) {
        ns.Elements.update(fragmentHtml, ACTION_LOAD_MORE);

        if (activeDiscoveryQuery) {
            setAddressBar(form.serializeQueryFor(activeDiscoveryQuery, ACTION_DEEP_LINK));
        } else {
            setAddressBar(form.serializeFor(ACTION_DEEP_LINK));
        }
        activeRequestQuery = null;

        trigger(ns.Events.SEARCH_END, [EVENT_SEARCH_TYPE_LOAD_MORE]);
        running = false;
    }

    function search(e) {
        if (applyingRailSelections) {
            // Rail inputs are being synced to a discovery query; this change/click
            // is programmatic, not a user edit, so it must not re-trigger a search.
            return;
        }
        if (e) {
            e.preventDefault();
        }
        if (!running) {
            running = true;

            if (hasDiscoveryCommand()) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
                if (getSearchPrompt()) {
                    if (activeDiscoveryQuery && activeDiscoveryPrompt === getSearchPrompt()) {
                        submitDiscoveryQuery(ACTION_SEARCH, processSearch, EVENT_SEARCH_TYPE_FULL);
                    } else {
                        discoverySearch();
                    }
                } else {
                    clearDiscoveryState();
                    searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                }
            } else {
                clearDiscoveryState();
                hideDiscoveryQuery();
                if (form.submit(ACTION_SEARCH, true, processSearch, function() {
                    searchFailed(EVENT_SEARCH_TYPE_FULL, false);
                })) {
                    trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
                } else {
                    searchFailed(EVENT_SEARCH_TYPE_FULL, false);
                }
            }
        }
    }

    function loadMore(e) {
        if (e) {
            e.preventDefault();
        }
        if (!running) {
            running = true;
            if (activeDiscoveryQuery) {
                submitDiscoveryQuery(ACTION_LOAD_MORE, processLoadMore, EVENT_SEARCH_TYPE_LOAD_MORE);
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_LOAD_MORE]);
            } else if (form.submit(ACTION_LOAD_MORE, false, processLoadMore, function() {
                searchFailed(EVENT_SEARCH_TYPE_LOAD_MORE, false);
            })) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_LOAD_MORE]);
            } else {
                searchFailed(EVENT_SEARCH_TYPE_LOAD_MORE, false);
            }
        }
    }

    function sortResults(e) {
        if (e) {
            e.preventDefault();
        }
        if (!running) {
            running = true;
            if (activeDiscoveryQuery) {
                submitDiscoveryQuery(ACTION_SORT, processSearch, EVENT_SEARCH_TYPE_FULL);
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
            } else if (form.submit(ACTION_SORT, false, processSearch, function() {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            })) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
            } else {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            }
        }
    }

    function switchLayout(e) {
        if (e) {
            e.preventDefault();
        }
        if (!running) {
            running = true;

            ns.Data.val("layout", $(this).val());
            if (activeDiscoveryQuery) {
                submitDiscoveryQuery(ACTION_SWITCH_LAYOUT, processSearch, EVENT_SEARCH_TYPE_FULL);
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
            } else if (form.submit(ACTION_SWITCH_LAYOUT, false, processSearch, function() {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            })) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
            } else {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            }
        }
    }

    (function() {
        // ONLY EXECUTE ON THE SEARCH PAGE
        if (ns.Elements.element("form").length > 0) {
            ns.Navigation.returnUrl(window.location.pathname + window.location.search);
        }
    }());

    (function registerEvents() {
        var formId = getForm().id();

        $("body").on("submit", "#" + formId, search);
        $("body").on("click", ns.Elements.selector("load-more"), loadMore);
        $("body").on("change", ns.Elements.selector("sort"), sortResults);
        $("body").on("click", ns.Elements.selector("switch-layout"), switchLayout);

        $("body").on("input change", "[for][form=\"" + formId + "\"]", markDiscoveryPredicateDirty);
        $("body").on("change", "[data-asset-share-search-on='change']", search);
        $("body").on("click", "[data-asset-share-search-on='click']", search);

        /* Required for IE */
        $("button[form='" + formId + "']").on("click", search);
        $("input[form='" + formId + "']").keypress(function(e) {
            if ((e.keyCode || e.which) === 13) {
                search(e);
            }
        });

        // Handle navigation back/forward on search page
        window.addEventListener('popstate', function(event) {
            if (getForm()) { window.location.reload(); }
        });
    }());

    return {
        loadMore: loadMore,
        search: search,
        sortResults: sortResults,
        switchLayout: switchLayout,
        form: getForm
    };

}(window,
    jQuery,
    AssetShare,
    AssetShare.Ajax));
