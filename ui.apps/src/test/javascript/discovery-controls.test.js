/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

"use strict";

var assert = require("assert"),
    path = require("path"),
    discoveryControls = require(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/discovery-controls.js"
    ));

function context() {
    return {
        version: 2,
        context: {
            query: {
                fulltext: null,
                path: null,
                allowedPathRoots: ["/content/dam"]
            },
            controls: [
                {
                    id: "format",
                    title: "Format",
                    kind: "choice",
                    cardinality: "many",
                    state: {values: ["image/png"]},
                    options: [
                        {value: "image/jpeg", label: "JPEG", disabled: false},
                        {value: "image/png", label: "PNG", disabled: false},
                        {value: "image/gif", label: "GIF", disabled: true}
                    ]
                },
                {
                    id: "keywords",
                    title: "Keywords",
                    kind: "text",
                    state: {values: ["summer"]},
                    constraints: {
                        minLength: 2,
                        maxLength: 12,
                        maxValues: 3,
                        pattern: "^[A-Za-z0-9 -]+$"
                    }
                },
                {
                    id: "created",
                    title: "Created",
                    kind: "date-range",
                    state: {lowerBound: null, upperBound: null},
                    constraints: {format: "YYYY-MM-DD"}
                },
                {
                    id: "location",
                    title: "Location",
                    kind: "path",
                    cardinality: "many",
                    state: {values: ["/content/dam/products"]},
                    options: [
                        {value: "/content/dam/products", label: "Products", disabled: false},
                        {value: "/content/dam/campaigns", label: "Campaigns", disabled: false},
                        {value: "/content/dam/archive", label: "Archive", disabled: true}
                    ]
                }
            ]
        }
    };
}

function validResponse() {
    return {
        version: 2,
        query: {fulltext: "landscape", path: null},
        controlUpdates: [
            {id: "format", state: {values: ["image/jpeg"]}},
            {id: "keywords", state: {values: ["red", "blue"]}},
            {id: "created", state: {lowerBound: "2026-07-01", upperBound: "2026-07-15"}},
            {id: "location", state: {values: ["/content/dam/campaigns"]}}
        ]
    };
}

function assertRejects(name, mutate) {
    var response = validResponse();

    mutate(response);
    assert.throws(function() {
        discoveryControls.validateResponse(response, context());
    }, Error, name);
}

(function acceptsCompleteV2ControlState() {
    var validated = discoveryControls.validateResponse(validResponse(), context());

    assert.deepStrictEqual(validated.query, {fulltext: "landscape", path: null});
    assert.deepStrictEqual(validated.controlUpdates.map(function(update) {
        return update.id;
    }), ["format", "keywords", "created", "location"]);
}());

[
    ["unknown query key", function(response) {
        response.query.orderby = "@jcr:content/jcr:lastModified";
    }],
    ["unknown option", function(response) {
        response.controlUpdates[0].state.values = ["image/tiff"];
    }],
    ["disabled option", function(response) {
        response.controlUpdates[0].state.values = ["image/gif"];
    }],
    ["duplicate id", function(response) {
        response.controlUpdates.push({id: "format", state: {values: ["image/jpeg"]}});
    }],
    ["bad date", function(response) {
        response.controlUpdates[2].state.lowerBound = "07/01/2026";
    }],
    ["bad text pattern", function(response) {
        response.controlUpdates[1].state.values = ["red!"];
    }],
    ["path outside root", function(response) {
        response.query.path = "/etc/tags";
        response.controlUpdates[3].state.values = [];
    }],
    ["representable residual path", function(response) {
        response.query.path = "/content/dam/campaigns";
        response.controlUpdates[3].state.values = [];
    }],
    ["residual path collides with path control", function(response) {
        response.query.path = "/content/dam/legal";
    }]
].forEach(function(testCase) {
    assertRejects(testCase[0], testCase[1]);
});

(function acceptsAllowedUnrepresentedResidualPathWhenPathControlsAreEmpty() {
    var response = validResponse(),
        validated;

    response.query.path = "/content/dam/legal/2026";
    response.controlUpdates[3].state.values = [];

    validated = discoveryControls.validateResponse(response, context());

    assert.strictEqual(validated.query.path, "/content/dam/legal/2026");
    assert.deepStrictEqual(validated.controlUpdates[3].state.values, []);
}());

console.log("discovery controls tests passed");
