/*
 * Asset Share Commons
 *
 * Copyright [2017]  Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
        DISCOVERY_COMMAND_INSERT = DISCOVERY_COMMAND + " ",

        running = false,
        activeDiscoveryState = null,
        activeRequestQuery = null,

        form = ns.Search.Form(ns);

    function getForm() {
        return form;
    }

    function trigger(eventType, params) {
        $("body").trigger(eventType, params);
    }

    function setAddressBar(queryParams) {
        if (ns.Util.isSameOrigin()) {
            ns.Navigation.addressBar(window.top.location.pathname + "?" + queryParams);
        } else {
            ns.Navigation.addressBar(window.location.pathname + "?" + queryParams);
        }

        ns.Navigation.returnUrl(window.location.pathname + "?" + queryParams);
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

    function getDiscoveryCommandInput() {
        return ns.Elements.element("discovery-search").find(":input").filter(function() {
            return $(this).is("input,textarea");
        }).first();
    }

    function getDiscoveryCommandMenu() {
        return ns.Elements.element("discovery-command-menu");
    }

    function showDiscoveryCommandMenu() {
        getDiscoveryCommandMenu().removeClass("hidden");
    }

    function hideDiscoveryCommandMenu() {
        getDiscoveryCommandMenu().addClass("hidden");
    }

    function shouldShowDiscoveryCommandMenu(value) {
        var typed = $.trim(value || "");

        return typed &&
            typed.charAt(0) === "/" &&
            typed.indexOf(" ") === -1 &&
            DISCOVERY_COMMAND.indexOf(typed) === 0 &&
            typed !== DISCOVERY_COMMAND;
    }

    function updateDiscoveryCommandMenu() {
        var input = getDiscoveryCommandInput();

        if (input.length && shouldShowDiscoveryCommandMenu(input.val())) {
            showDiscoveryCommandMenu();
        } else {
            hideDiscoveryCommandMenu();
        }
    }

    function insertDiscoveryCommand(e) {
        var input = getDiscoveryCommandInput();

        if (e) {
            e.preventDefault();
        }
        if (!input.length) {
            return;
        }

        input.val(DISCOVERY_COMMAND_INSERT);
        hideDiscoveryCommandMenu();
        input.focus();
    }

    function handleDiscoveryCommandKeydown(e) {
        var key = e.key || "",
            menuVisible = !getDiscoveryCommandMenu().hasClass("hidden");

        if (key === "Escape") {
            hideDiscoveryCommandMenu();
            return;
        }

        if (menuVisible && (key === "Enter" || key === "Tab" || key === "ArrowDown")) {
            insertDiscoveryCommand(e);
        }
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
        activeDiscoveryState = null;
    }

    function getResidualQuery() {
        return activeDiscoveryState && activeDiscoveryState.residual || {
            fulltext: null,
            path: null
        };
    }

    function showDiscoveryState(residualQuery) {
        var queryElement = getDiscoveryQueryElement(),
            outputElement = getDiscoveryQueryOutputElement(),
            titleElement = getDiscoveryQueryTitleElement(),
            residual = residualQuery || getResidualQuery(),
            hasResidual = residual.fulltext !== null || residual.path !== null;

        outputElement.empty();
        if (!hasResidual) {
            queryElement.addClass("hidden").removeClass("negative");
            return;
        }

        titleElement.text(ns.Data.attr(queryElement, "residual-title") ||
            ns.Data.attr(queryElement, "query-title"));

        ["fulltext", "path"].forEach(function(name) {
            var value = residual[name],
                item,
                button;

            if (value === null) {
                return;
            }

            item = $("<div>").addClass("item");
            $("<span>").addClass("asset-share-commons__discovery-residual-label")
                .text(name + ": " + value)
                .appendTo(item);
            button = $("<button>")
                .attr("type", "button")
                .attr("data-asset-share-discovery-residual-clear", name)
                .attr("aria-label", "Clear discovery " + name)
                .attr("title", "Clear discovery " + name)
                .addClass("ui compact basic button")
                .text("Clear");
            button.appendTo(item);
            item.appendTo(outputElement);
        });

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

    function submitDiscoveryState(action, success, searchType) {
        var query = form.serializeDiscoveryStateFor(
            getResidualQuery(),
            action,
            getDiscoverySearchFieldNames()
        );

        if (!form.isValid()) {
            searchFailed(searchType, false);
            return;
        }

        activeRequestQuery = query;
        form.submitQuery(query, success).fail(function() {
            searchFailed(searchType, true);
        });
    }

    function extractDiscoveryAgentResponse(fragmentHtml) {
        var parsed = $("<div></div>").html(fragmentHtml),
            responseElement = ns.Elements.element("discovery-agent-response", parsed),
            raw;

        if (!responseElement.length) {
            return null;
        }

        raw = ns.Data.attr(responseElement, "discovery-agent-response");

        return raw || null;
    }

    function discoverySearch() {
        var prompt = getSearchPrompt(),
            contextJson,
            context,
            baselineQuery;

        clearDiscoveryState();
        form.clearDiscoveryControls();
        contextJson = form.serializeDiscoveryContextFor(
                ACTION_SEARCH,
                true,
                getDiscoverySearchFieldNames(),
                getResidualQuery()
            );
        context = JSON.parse(contextJson);

        hideDiscoveryQuery();

        baselineQuery = form.serializeDiscoveryStateFor(
            getResidualQuery(),
            ACTION_SEARCH,
            getDiscoverySearchFieldNames()
        );

        if (!form.isValid()) {
            searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            return;
        }

        form.submitDiscoveryQuery(prompt, contextJson, baselineQuery, function(fragmentHtml) {
            var agentResponse = extractDiscoveryAgentResponse(fragmentHtml),
                validated;

            if (!agentResponse) {
                clearDiscoveryState();
                searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                return;
            }

            try {
                validated = ns.Search.DiscoveryControls.validateResponse(agentResponse, context);
            } catch (e) {
                clearDiscoveryState();
                searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                return;
            }

            form.applyDiscoveryControlUpdates(validated.controlUpdates);
            activeDiscoveryState = {
                prompt: prompt,
                residual: validated.query
            };
            showDiscoveryState(validated.query);

            // The actual asset search already ran server-side (DiscoverySearchProviderImpl), so
            // fragmentHtml already reflects the agent-resolved query -- no second request is made.
            // Only the address-bar deep-link query is recomputed here, from the now rail-synced DOM.
            activeRequestQuery = form.serializeDiscoveryStateFor(
                getResidualQuery(),
                ACTION_SEARCH,
                getDiscoverySearchFieldNames()
            );

            processSearch(fragmentHtml);
        }).fail(function() {
            clearDiscoveryState();
            searchFailed(EVENT_SEARCH_TYPE_FULL, true);
        });
    }

    function processLoadMore(fragmentHtml) {
        ns.Elements.update(fragmentHtml, ACTION_LOAD_MORE);
        setAddressBar(activeRequestQuery || form.serializeFor(ACTION_DEEP_LINK));
        activeRequestQuery = null;

        trigger(ns.Events.SEARCH_END, [EVENT_SEARCH_TYPE_LOAD_MORE]);
        running = false;
    }

    function search(e) {
        if (e) {
            e.preventDefault();
        }
        if (form.isApplyingDiscoveryControls()) {
            return;
        }
        if (!running) {
            running = true;

            if (hasDiscoveryCommand()) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
                if (getSearchPrompt()) {
                    discoverySearch();
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
            if (activeDiscoveryState) {
                submitDiscoveryState(ACTION_LOAD_MORE, processLoadMore, EVENT_SEARCH_TYPE_LOAD_MORE);
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
            if (activeDiscoveryState) {
                submitDiscoveryState(ACTION_SORT, processSearch, EVENT_SEARCH_TYPE_FULL);
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
            if (activeDiscoveryState) {
                submitDiscoveryState(ACTION_SWITCH_LAYOUT, processSearch, EVENT_SEARCH_TYPE_FULL);
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

    function markDiscoveryInputChanged() {
        var predicateId = $(this).attr("for");

        if (activeDiscoveryState && predicateId && form.isPathControl(predicateId) &&
                !form.isApplyingDiscoveryControls()) {
            activeDiscoveryState.residual.path = null;
            showDiscoveryState();
        }
    }

    function clearResidual(e) {
        var name = $(this).attr("data-asset-share-discovery-residual-clear");

        if (e) {
            e.preventDefault();
        }
        if (!activeDiscoveryState || (name !== "fulltext" && name !== "path")) {
            return;
        }

        activeDiscoveryState.residual[name] = null;
        showDiscoveryState();
        search(e);
    }

    (function() {
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
        $("body").on("click", "[data-asset-share-discovery-residual-clear]", clearResidual);
        $("body").on("click", ns.Elements.selector("discovery-command-option"), insertDiscoveryCommand);
        $("body").on("input", ns.Elements.selector("discovery-search") + " input", updateDiscoveryCommandMenu);
        $("body").on("keydown", ns.Elements.selector("discovery-search") + " input", handleDiscoveryCommandKeydown);
        $("body").on("click", function(e) {
            if (!$(e.target).closest(ns.Elements.selector("discovery-search")).length) {
                hideDiscoveryCommandMenu();
            }
        });

        $("body").on("input change", "[for][form=\"" + formId + "\"]", markDiscoveryInputChanged);
        $("body").on("change", "[data-asset-share-search-on='change']", search);
        $("body").on("click", "[data-asset-share-search-on='click']", search);

        $("button[form='" + formId + "']").on("click", search);
        $("input[form='" + formId + "']").keypress(function(e) {
            if ((e.keyCode || e.which) === 13) {
                search(e);
            }
        });

        window.addEventListener("popstate", function(event) {
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
