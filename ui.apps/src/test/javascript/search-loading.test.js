/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

"use strict";

var assert = require("assert"),
    path = require("path"),
    createSearchLoading = require(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search-loading.js"
    ));

(function blocksOnlyFullSearchRequests() {
    var handlers = {},
        calls = [],
        page = {
            append: function() {
                calls.push("append-loader");
                return page;
            },
            attr: function(name, value) {
                calls.push(name + "=" + value);
                return page;
            },
            dimmer: function(command, argument) {
                calls.push(typeof command === "string" ? command : "configure");
                if (command === "hide" && typeof argument === "function") {
                    argument();
                }
                return page;
            }
        },
        loader = {
            detach: function() {
                calls.push("detach-loader");
                return loader;
            }
        },
        $ = function(selector) {
            if (selector === "body") {
                return {
                    on: function(eventName, handler) {
                        handlers[eventName] = handler;
                    }
                };
            }
            return page;
        },
        ns = {
            Events: {
                SEARCH_BEGIN: "search-begin",
                SEARCH_END: "search-end",
                SEARCH_INVALID: "search-invalid"
            },
            Elements: {
                element: function() {
                    return loader;
                }
            }
        };

    createSearchLoading($, ns);

    handlers["search-begin"](null, "load-more");
    assert.deepStrictEqual(calls, []);

    handlers["search-begin"](null, "search");
    assert.deepStrictEqual(calls, [
        "aria-busy=true",
        "configure",
        "add content",
        "show"
    ]);

    handlers["search-end"](null, "load-more");
    assert.strictEqual(calls.indexOf("hide"), -1);

    handlers["search-invalid"](null, "search");
    assert.deepStrictEqual(calls.slice(-4), [
        "aria-busy=false",
        "hide",
        "detach-loader",
        "append-loader"
    ]);
}());

console.log("search-loading tests passed");
