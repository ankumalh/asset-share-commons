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

(function readsGeneratedPredicatesBySemanticIdentity() {
    var query = [
            "0_property=.%2Fjcr%3Acontent%2Fmetadata%2Fdc%3Aformat",
            "0_property.1_value=image%2Fjpeg",
            "0_property.2_value=image%2Fpng",
            "1_group.daterange.property=jcr%3Acontent%2Fjcr%3AlastModified",
            "1_group.daterange.lowerBound=2026-07-01",
            "1_group.daterange.upperBound=2026-07-27",
            "2_group.0_path=%2Fcontent%2Fdam%2Fproducts",
            "2_group.1_path=%2Fcontent%2Fdam%2Fcampaigns",
            "fulltext=camera"
        ].join("&"),
        propertyPredicate = discoveryQuery.findPropertyPredicate(
            query,
            "jcr:content/metadata/dc:format"
        ),
        datePredicate = discoveryQuery.findPropertyPredicate(
            query,
            "./jcr:content/jcr:lastModified"
        );

    assert.deepStrictEqual(propertyPredicate.values, ["image/jpeg", "image/png"]);
    assert.strictEqual(propertyPredicate.kind, "property");
    assert.strictEqual(datePredicate.kind, "date");
    assert.strictEqual(datePredicate.lowerBound, "2026-07-01");
    assert.strictEqual(datePredicate.upperBound, "2026-07-27");
    assert.deepStrictEqual(discoveryQuery.getPathValues(query), [
        "/content/dam/products",
        "/content/dam/campaigns"
    ]);
    assert.deepStrictEqual(valuesFor(query, "fulltext"), ["camera"]);
}());

(function replacesGeneratedPathsWithoutDiscardingOtherPredicates() {
    var query = [
            "0_group.p.or=true",
            "0_group.0_path=%2Fcontent%2Fdam%2Fold",
            "0_group.1_path=%2Fcontent%2Fdam%2Farchive",
            "fulltext=camera",
            "p.limit=24"
        ].join("&"),
        merged = discoveryQuery.mergePredicates(query, [{
            kind: "path",
            parameters: [
                {name: "42_group.p.or", value: "true"},
                {name: "42_group.0_path", value: "/content/dam/current"}
            ]
        }]);

    assert.deepStrictEqual(discoveryQuery.getPathValues(merged), ["/content/dam/current"]);
    assert.deepStrictEqual(valuesFor(merged, "fulltext"), ["camera"]);
    assert.deepStrictEqual(valuesFor(merged, "p.limit"), ["24"]);
}());

(function replacesOnlyTheMatchingGeneratedPathGroup() {
    var query = [
            "0_group.p.or=true",
            "0_group.0_path=%2Fcontent%2Fdam%2Fproducts",
            "1_group.p.or=true",
            "1_group.0_path=%2Fcontent%2Fdam%2Frestricted",
            "fulltext=camera"
        ].join("&"),
        merged = discoveryQuery.mergePredicates(query, [{
            kind: "path",
            pathParameterNames: ["42_group.0_path", "42_group.1_path"],
            pathOptionValues: [
                "/content/dam/products",
                "/content/dam/campaigns"
            ],
            parameters: [
                {name: "42_group.p.or", value: "true"},
                {name: "42_group.0_path", value: "/content/dam/campaigns"}
            ]
        }]);

    assert.deepStrictEqual(discoveryQuery.getPathValues(merged), [
        "/content/dam/restricted",
        "/content/dam/campaigns"
    ]);
    assert.deepStrictEqual(valuesFor(merged, "0_group.p.or"), []);
    assert.deepStrictEqual(valuesFor(merged, "1_group.p.or"), ["true"]);
    assert.deepStrictEqual(valuesFor(merged, "fulltext"), ["camera"]);
}());

(function normalizesPropertySortWithALeadingQuestionMark() {
    var normalized = discoveryQuery.normalizeOrderBy(
        "?fulltext=plant&orderby=jcr%3Acontent%2Fmetadata%2Fdam%3Asize&orderby.sort=desc"
    );

    assert.deepStrictEqual(valuesFor(normalized, "fulltext"), ["plant"]);
    assert.deepStrictEqual(valuesFor(normalized, "orderby"), [
        "@jcr:content/metadata/dam:size"
    ]);
    assert.deepStrictEqual(valuesFor(normalized, "orderby.sort"), ["desc"]);
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
