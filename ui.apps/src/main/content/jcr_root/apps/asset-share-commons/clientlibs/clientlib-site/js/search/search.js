/*
 * Asset Share Commons
 *
 * Copyright [2017]  Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global jQuery: false, AssetShare: false, window: false, document: false */

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
        return getDiscoveryCommandInput().filter(function() {
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

    function isSameOrigin(url) {
        var target = document.createElement("a"),
            current = document.createElement("a");

        target.href = url;
        current.href = window.location.href;
        return target.protocol === current.protocol && target.host === current.host;
    }

    function discoverySearch() {
        var prompt = getSearchPrompt(),
            baselineQuery;

        hideDiscoveryQuery();

        if (!form.isDiscoveryEnabled()) {
            searchFailed(EVENT_SEARCH_TYPE_FULL, true);
            return;
        }

        baselineQuery = form.serializeDiscoveryStateFor(
            ACTION_SEARCH,
            getDiscoverySearchFieldNames()
        );

        if (!form.isValid()) {
            searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            return;
        }

        form.submitDiscoveryResolution(prompt, baselineQuery, function(response) {
            try {
                if (typeof response === "string") {
                    response = JSON.parse(response);
                }
            } catch (e) {
                searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                return;
            }

            if (!response || response.version !== 1 || !response.redirectUrl ||
                    !isSameOrigin(response.redirectUrl)) {
                searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                return;
            }

            window.location.assign(response.redirectUrl);
        }).fail(function() {
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
        if (!running) {
            running = true;

            if (hasDiscoveryCommand()) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
                if (getSearchPrompt()) {
                    discoverySearch();
                } else {
                    searchFailed(EVENT_SEARCH_TYPE_FULL, true);
                }
            } else {
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
            if (form.submit(ACTION_LOAD_MORE, false, processLoadMore, function() {
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
            if (form.submit(ACTION_SORT, false, processSearch, function() {
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
            if (form.submit(ACTION_SWITCH_LAYOUT, false, processSearch, function() {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            })) {
                trigger(ns.Events.SEARCH_BEGIN, [EVENT_SEARCH_TYPE_FULL]);
            } else {
                searchFailed(EVENT_SEARCH_TYPE_FULL, false);
            }
        }
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
        $("body").on("click", ns.Elements.selector("discovery-command-option"), insertDiscoveryCommand);
        $("body").on("input", ns.Elements.selector("discovery-search") + " input", updateDiscoveryCommandMenu);
        $("body").on("keydown", ns.Elements.selector("discovery-search") + " input", handleDiscoveryCommandKeydown);
        $("body").on("click", function(e) {
            if (!$(e.target).closest(ns.Elements.selector("discovery-search")).length) {
                hideDiscoveryCommandMenu();
            }
        });
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
