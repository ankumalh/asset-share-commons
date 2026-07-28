/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

"use strict";

var assert = require("assert"),
    fs = require("fs"),
    path = require("path"),
    vm = require("vm"),
    FORM_ID = "asset-share-commons__form-id__1";

function Collection(elements) {
    this.elements = elements || [];
    this.length = this.elements.length;
}

Collection.prototype.attr = function (name) {
    return this.length ? this.elements[0].attributes[name] : undefined;
};

Collection.prototype.val = function () {
    return this.length ? this.elements[0].value : undefined;
};

Collection.prototype.is = function (selector) {
    if (!this.length) {
        return false;
    }
    return selector.split(",").map(function (part) {
        return part.trim();
    }).indexOf(":" + this.elements[0].type) > -1;
};

Collection.prototype.prop = function (name, value) {
    if (!this.length) {
        return this;
    }
    if (value === undefined) {
        return this.elements[0][name];
    }
    this.elements[0][name] = value;
    return this;
};

Collection.prototype.each = function (fn) {
    this.elements.forEach(function (element, index) {
        fn.call(element, index, element);
    });
    return this;
};

Collection.prototype.filter = function (fn) {
    return new Collection(this.elements.filter(function (element) {
        return fn.call(element);
    }));
};

Collection.prototype.first = function () {
    return new Collection(this.elements.slice(0, 1));
};

// No-op jQuery affordances exercised only while search.js wires up its
// document-level event handlers at load time - not relevant to this test.
Collection.prototype.on = function () {
    return this;
};
Collection.prototype.keypress = function () {
    return this;
};

/**
 * Builds a minimal jQuery-like selector engine over a fixed set of rail
 * elements, supporting only the attribute selectors search.js actually uses
 * to correlate a discovery query with the rendered search-rail predicate.
 */
function jqueryFor(elements) {
    var jquery = function (selector) {
        var match;

        if (typeof selector !== "string") {
            return new Collection(selector ? [selector] : []);
        }

        match = /^\[data-asset-share-predicate-id\]\[form="([^"]+)"\]$/.exec(selector);
        if (match) {
            return new Collection(elements.filter(function (element) {
                return element.attributes["data-asset-share-predicate-id"] && element.attributes.form === match[1];
            }));
        }

        match = /^\[data-asset-share-predicate-id="([^"]+)"\]\[form="([^"]+)"\]$/.exec(selector);
        if (match) {
            return new Collection(elements.filter(function (element) {
                return element.attributes["data-asset-share-predicate-id"] === match[1] && element.attributes.form === match[2];
            }));
        }

        match = /^:input\[for="([^"]+)"\]\[form="([^"]+)"\]$/.exec(selector);
        if (match) {
            return new Collection(elements.filter(function (element) {
                return element.attributes["for"] === match[1] && element.attributes.form === match[2];
            }));
        }

        return new Collection([]);
    };

    jquery.each = function (arr, fn) {
        arr.forEach(function (item, index) {
            fn.call(item, index, item);
        });
    };

    return jquery;
}

function loadSearch(elements) {
    var jquery = jqueryFor(elements),
        context = {
            window: {
                location: { pathname: "/content/search.html", search: "" },
                addEventListener: function () {}
            },
            $: jquery,
            jQuery: jquery,
            AssetShare: {
                Data: {
                    attr: function (collection, key) {
                        return collection.attr("data-asset-share-" + key);
                    },
                    val: function () {
                        return "";
                    }
                },
                Elements: {
                    element: function () {
                        return new Collection([]);
                    },
                    selector: function () {
                        return "[data-asset-share-id]";
                    }
                },
                FormData: function () {
                    var entries = [];

                    this.add = function (name, value) {
                        entries.push({ name: name, value: value });
                    };
                    this.getAll = function (name) {
                        if (name === undefined) {
                            return entries;
                        }
                        return entries.filter(function (entry) {
                            return entry.name === name;
                        }).map(function (entry) {
                            return entry.value;
                        });
                    };
                },
                Search: {}
            }
        };

    context.window.top = context.window;
    context.AssetShare.Ajax = {};

    vm.createContext(context);

    // search.js calls `ns.Search.Form(ns)` while loading, so the real
    // search-form.js factory must exist on the namespace first.
    vm.runInContext(
        fs.readFileSync(path.resolve(
            __dirname,
            "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search-form.js"
        ), "utf8"),
        context
    );

    vm.runInContext(
        fs.readFileSync(path.resolve(
            __dirname,
            "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search.js"
        ), "utf8"),
        context
    );

    return context.AssetShare.Search;
}

(function selectsThePropertyValuesFacetOptionMatchingTheDiscoveryQuery() {
    var propertyField = {
            attributes: {
                "data-asset-share-predicate-id": "cmp-propertyvalues_-1466105409",
                form: FORM_ID,
                name: "3_group.propertyvalues.property"
            },
            value: "./jcr:content/metadata/cq:tags"
        },
        grayscaleRadio = {
            attributes: { "for": "cmp-propertyvalues_-1466105409", form: FORM_ID, name: "3_group.propertyvalues.0_values" },
            type: "radio",
            value: "properties:style/monochrome/grayscale",
            checked: false
        },
        monochromeRadio = {
            attributes: { "for": "cmp-propertyvalues_-1466105409", form: FORM_ID, name: "3_group.propertyvalues.0_values" },
            type: "radio",
            value: "properties:style/monochrome",
            checked: false
        },
        search = loadSearch([propertyField, grayscaleRadio, monochromeRadio]),
        query = [
            "1_propertyvalues.property=jcr%3Acontent%2Fmetadata%2Fcq%3Atags",
            "1_propertyvalues.operation=equals",
            "1_propertyvalues.0_values=properties%3Astyle%2Fmonochrome%2Fgrayscale"
        ].join("&");

    search.applyDiscoveryQueryToRail(query);

    assert.strictEqual(grayscaleRadio.checked, true, "grayscale radio should be checked for the matching discovery query value");
    assert.strictEqual(monochromeRadio.checked, false, "monochrome radio should remain unchecked");
}());

console.log("search-discovery-rail tests passed");
