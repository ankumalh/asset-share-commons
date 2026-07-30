/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

"use strict";

var assert = require("assert"),
    fs = require("fs"),
    path = require("path"),
    vm = require("vm");

function Collection(elements) {
    this.elements = elements || [];
    this.length = this.elements.length;
    this[0] = this.elements[0];
}

Collection.prototype.attr = function(name) {
    return this.length ? this.elements[0].attributes[name] : undefined;
};
Collection.prototype.each = function(callback) {
    this.elements.forEach(function(element, index) {
        callback.call(element, index, element);
    });
    return this;
};
Collection.prototype.serializeArray = function() {
    return this.elements.reduce(function(values, element) {
        if (element.value !== "") {
            values.push({name: element.attributes.name, value: element.value});
        }
        return values;
    }, []);
};

(function submitsCurrentStateToPageResolver() {
    var formId = "asset-share-commons__form-id__1",
        formElement = {
            attributes: {
                id: formId,
                action: "/content/search.html",
                "data-asset-share-action": "/content/search.results.html"
            }
        },
        fields = [
            {
                attributes: {name: "fulltext", form: formId},
                value: "/discovery find landscape JPEGs"
            },
            {
                attributes: {
                    name: "4_group.propertyvalues.property",
                    form: formId,
                    "data-asset-share-predicate-id": "format"
                },
                value: "jcr:content/metadata/dc:format"
            },
            {
                attributes: {
                    name: "4_group.propertyvalues.0_values",
                    form: formId,
                    "for": "format"
                },
                value: "image/png"
            },
            {attributes: {name: "customer", form: formId}, value: "one"},
            {attributes: {name: "customer", form: formId}, value: "two"},
            {attributes: {name: "p.offset", form: formId}, value: "24"}
        ],
        posts = [],
        context,
        form,
        currentState;

    function jquery(value) {
        var nameMatch,
            forMatch;

        if (typeof value !== "string") {
            return new Collection(value ? [value] : []);
        }
        if (value === "form[id=\"" + formId + "\"]") {
            return new Collection([formElement]);
        }
        if (value === "[form=\"" + formId + "\"]") {
            return new Collection(fields);
        }
        if (value === "[data-asset-share-search-actions]" ||
                value.indexOf("[data-asset-share-search-actions*=") === 0) {
            return new Collection([]);
        }
        nameMatch = value.match(/^\[name="([^"]+)"\]\[form="([^"]+)"\]$/);
        if (nameMatch) {
            return new Collection(fields.filter(function(field) {
                return field.attributes.name === nameMatch[1] &&
                    field.attributes.form === nameMatch[2];
            }));
        }
        forMatch = value.match(/^\[for="([^"]+)"\]$/);
        if (forMatch) {
            return new Collection(fields.filter(function(field) {
                return field.attributes["for"] === forMatch[1];
            }));
        }
        return new Collection([]);
    }

    jquery.each = function(values, callback) {
        values.forEach(callback);
    };
    jquery.trim = function(value) {
        return (value || "").trim();
    };
    jquery.param = function(values) {
        return values.map(function(value) {
            return encodeURIComponent(value.name) + "=" + encodeURIComponent(value.value);
        }).join("&");
    };
    jquery.post = function(url, body) {
        posts.push({url: url, body: body});
        return {
            then: function(success) {
                success({version: 1, redirectUrl: "/content/search.html?p.offset=0"});
                return this;
            },
            fail: function() {
                return this;
            }
        };
    };
    jquery.when = function(value) {
        return value;
    };

    context = {
        $: jquery,
        jQuery: jquery,
        AssetShare: {
            Search: {},
            Data: {
                attr: function(element, name) {
                    return element.attr("data-asset-share-" + name) || "";
                },
                val: function() {
                    return "";
                }
            },
            Elements: {
                element: function(name) {
                    return name === "form" ? new Collection([formElement]) : new Collection([]);
                }
            }
        }
    };

    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/form-data.js"
    ), "utf8"), context);
    vm.runInContext(fs.readFileSync(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search-form.js"
    ), "utf8"), context);

    form = context.AssetShare.Search.Form(context.AssetShare);
    assert.strictEqual(form.isDiscoveryEnabled(), true,
        "an older results overlay derives the page-scoped discovery action");
    currentState = form.serializeDiscoveryStateFor("search", ["fulltext"]);

    assert.strictEqual(currentState.indexOf("fulltext"), -1);
    assert.ok(currentState.indexOf("4_group.propertyvalues.0_values=image%2Fpng") > -1);
    assert.ok(currentState.indexOf("customer=one&customer=two") > -1);
    assert.ok(currentState.indexOf("p.offset=24") > -1);

    form.submitDiscoveryResolution("find landscape JPEGs", currentState, function() {});
    assert.strictEqual(posts[0].url, "/content/search.discovery.json");
    assert.ok(posts[0].body.indexOf("prompt=find%20landscape%20JPEGs") > -1);
    assert.strictEqual(posts[0].body.indexOf("context="), -1,
        "semantic control context is built by AEM, not submitted by the browser");

    formElement.attributes["data-asset-share-discovery-enabled"] = "false";
    form = context.AssetShare.Search.Form(context.AssetShare);
    assert.strictEqual(form.isDiscoveryEnabled(), false,
        "an explicitly unconfigured base component does not derive a fallback");

    delete formElement.attributes["data-asset-share-discovery-enabled"];
    formElement.attributes["data-asset-share-discovery-contract"] = "1";
    form = context.AssetShare.Search.Form(context.AssetShare);
    assert.strictEqual(form.isDiscoveryEnabled(), false,
        "the current results contract does not derive a fallback when no resolver is configured");

    formElement.attributes["data-asset-share-discovery-enabled"] = "true";
    delete formElement.attributes["data-asset-share-discovery-contract"];
    formElement.attributes["data-asset-share-discovery-action"] =
        "https://attacker.example/collect";
    formElement.attributes.action = "https://attacker.example/content/search.html";
    form = context.AssetShare.Search.Form(context.AssetShare);
    assert.strictEqual(form.isDiscoveryEnabled(), false,
        "cross-origin explicit and derived actions are rejected before submission");
}());

console.log("discovery resolver submission tests passed");
