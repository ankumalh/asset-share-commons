/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global AssetShare: false, jQuery: false, module: false */

(function (root, factory) {
    "use strict";

    var createSearchLoading = factory();

    if (typeof module === "object" && module.exports) {
        module.exports = createSearchLoading;
    }

    if (root && root.AssetShare && root.jQuery) {
        createSearchLoading(root.jQuery, root.AssetShare);
    }
}(typeof window !== "undefined" ? window : this, function () {
    "use strict";

    return function ($, ns) {
        var FULL_SEARCH = "search",
            PAGE_SELECTOR = "body.page",
            LOADER_ID = "search-loader-text",
            active = false;

        function show(event, searchType) {
            var page;

            if (searchType !== FULL_SEARCH || active) {
                return;
            }

            active = true;
            page = $(PAGE_SELECTOR);
            page.attr("aria-busy", "true")
                .dimmer({closable: false})
                .dimmer("add content", ns.Elements.element(LOADER_ID))
                .dimmer("show");
        }

        function hide(event, searchType) {
            var page;

            if (searchType !== FULL_SEARCH || !active) {
                return;
            }

            active = false;
            page = $(PAGE_SELECTOR);
            page.attr("aria-busy", "false")
                .dimmer("hide", function() {
                    page.append(ns.Elements.element(LOADER_ID).detach());
                });
        }

        $("body").on(ns.Events.SEARCH_BEGIN, show);
        $("body").on(ns.Events.SEARCH_END, hide);
        $("body").on(ns.Events.SEARCH_INVALID, hide);

        return {
            hide: hide,
            show: show
        };
    };
}));
