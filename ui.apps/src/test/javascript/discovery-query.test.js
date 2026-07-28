/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

"use strict";

var assert = require("assert"),
    fs = require("fs"),
    path = require("path"),
    vm = require("vm"),
    discoveryQuery = require(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/discovery-query.js"
    ));

function valuesFor(query, name) {
    return discoveryQuery.parse(query).filter(function(parameter) {
        return parameter.name === name;
    }).map(function(parameter) {
        return parameter.value;
    });
}

(function replacesMatchingPropertyByCanonicalJcrPath() {
    var baseQuery = [
            "0_property=.%2Fjcr%3Acontent%2Fmetadata%2Fdc%3Aformat",
            "0_property.operation=equals",
            "0_property.1_value=image%2Fjpeg",
            "1_property=jcr%3Acontent%2Fmetadata%2Fstyle",
            "1_property.1_value=portrait",
            "fulltext=camera",
            "orderby=%40jcr%3Acontent%2Fmetadata%2Fsize"
        ].join("&"),
        merged = discoveryQuery.mergePropertyPredicates(baseQuery, [{
            propertyPath: "jcr:content/metadata/dc:format",
            parameters: [
                {name: "2_group.propertyvalues.property", value: "jcr:content/metadata/dc:format"},
                {name: "2_group.propertyvalues.operation", value: "equals"},
                {name: "2_group.propertyvalues.0_values", value: "image/png"}
            ]
        }]);

    assert.deepStrictEqual(valuesFor(merged, "0_property"), []);
    assert.deepStrictEqual(valuesFor(merged, "0_property.1_value"), []);
    assert.deepStrictEqual(valuesFor(merged, "1_property"), ["jcr:content/metadata/style"]);
    assert.deepStrictEqual(valuesFor(merged, "fulltext"), ["camera"]);
    assert.deepStrictEqual(valuesFor(merged, "orderby"), ["@jcr:content/metadata/size"]);
    assert.deepStrictEqual(
        valuesFor(merged, "2_group.propertyvalues.0_values"),
        ["image/png"]
    );
}());

(function removesMatchingPropertyWhenTheUiHasNoSelectedValues() {
    var baseQuery = [
            "0_property=jcr%3Acontent%2Fmetadata%2Fdc%3Aformat",
            "0_property.1_value=image%2Fjpeg",
            "fulltext=camera"
        ].join("&"),
        merged = discoveryQuery.mergePropertyPredicates(baseQuery, [{
            propertyPath: "./jcr:content/metadata/dc:format",
            parameters: []
        }]);

    assert.deepStrictEqual(valuesFor(merged, "0_property"), []);
    assert.deepStrictEqual(valuesFor(merged, "0_property.1_value"), []);
    assert.deepStrictEqual(valuesFor(merged, "fulltext"), ["camera"]);
}());

(function clientlibInitializationPreservesDiscoveryQueryHelper() {
    var clientlibRoot = path.resolve(
            __dirname,
            "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js"
        ),
        searchRoot = path.join(clientlibRoot, "search"),
        searchFiles = fs.readFileSync(path.join(searchRoot, "js.txt"), "utf8")
            .split(/\r?\n/)
            .filter(function(file) {
                return file && file.charAt(0) !== "#";
            }),
        context = {
            console: console
        };

    context.window = context;
    context.window.location = {pathname: "/content/search.html"};
    context.window.top = {location: context.window.location};
    context.window.addEventListener = function() {};
    context.jQuery = function() {
        return {
            keypress: function() {
                return this;
            },
            length: 0,
            on: function() {
                return this;
            }
        };
    };

    vm.createContext(context);
    vm.runInContext(
        fs.readFileSync(path.join(clientlibRoot, "namespace.js"), "utf8"),
        context
    );

    context.AssetShare.Ajax = {};
    context.AssetShare.Elements = {
        element: function() {
            return {length: 0};
        },
        selector: function() {
            return "";
        }
    };
    context.AssetShare.Navigation = {
        returnUrl: function() {}
    };
    context.AssetShare.Search.Form = function() {
        return {
            id: function() {
                return "search-form";
            }
        };
    };

    searchFiles.forEach(function(file) {
        if (file === "discovery-query.js" || file === "search.js") {
            vm.runInContext(fs.readFileSync(path.join(searchRoot, file), "utf8"), context);
        }
    });

    assert.ok(
        context.AssetShare.Search.DiscoveryQuery,
        "discovery query helper must survive clientlib initialization"
    );
    assert.strictEqual(typeof context.AssetShare.Search.DiscoveryQuery.mergePropertyPredicates, "function");
}());

console.log("discovery-query tests passed");
