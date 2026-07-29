/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

"use strict";

var assert = require("assert"),
    http = require("http"),
    https = require("https"),
    path = require("path"),
    discoveryControls = require(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/discovery-controls.js"
    )),
    endpoint = process.env.ASC_DISCOVERY_AGENT_URL,
    prompt = process.env.ASC_DISCOVERY_PROMPT ||
        "Find landscape JPEGs from Campaigns modified in the last month";

if (!endpoint) {
    console.log("skipping discovery agent contract test; set ASC_DISCOVERY_AGENT_URL");
    process.exit(0);
}

function postJson(url, body) {
    return new Promise(function(resolve, reject) {
        var target = new URL(url),
            transport = target.protocol === "https:" ? https : http,
            request = transport.request({
                method: "POST",
                hostname: target.hostname,
                port: target.port,
                path: target.pathname + target.search,
                headers: {
                    "Accept": "application/json",
                    "Content-Type": "application/json"
                }
            }, function(response) {
                var chunks = [];

                response.on("data", function(chunk) {
                    chunks.push(chunk);
                });
                response.on("end", function() {
                    resolve({
                        statusCode: response.statusCode,
                        body: Buffer.concat(chunks).toString("utf8")
                    });
                });
            });

        request.on("error", reject);
        request.write(JSON.stringify(body));
        request.end();
    });
}

var context = {
    query: {
        fulltext: null,
        path: null,
        allowedPathRoots: ["/content/dam"]
    },
    controls: [
            {
                id: "cmp-format",
                title: "File format",
                kind: "choice",
                cardinality: "many",
                state: {values: ["image/png"]},
                options: [
                    {value: "image/jpeg", label: "JPEG", disabled: false},
                    {value: "image/png", label: "PNG", disabled: false}
                ]
            },
            {
                id: "cmp-orientation",
                title: "Orientation",
                kind: "choice",
                cardinality: "one",
                state: {values: []},
                options: [
                    {value: "landscape", label: "Landscape", disabled: false},
                    {value: "portrait", label: "Portrait", disabled: false}
                ]
            },
            {
                id: "cmp-recency",
                title: "Last modified",
                kind: "relative-date",
                cardinality: "one",
                state: {values: []},
                options: [
                    {value: "-1d", label: "Last day", disabled: false},
                    {value: "-1M", label: "Last month", disabled: false},
                    {value: "-1y", label: "Last year", disabled: false}
                ]
            },
            {
                id: "cmp-location",
                title: "Location",
                kind: "path",
                cardinality: "one",
                state: {values: ["/content/dam/products"]},
                options: [
                    {value: "/content/dam/products", label: "Products", disabled: false},
                    {value: "/content/dam/campaigns", label: "Campaigns", disabled: false}
                ]
            },
            {
                id: "__asset_share_discovery_sort_orderby",
                title: "SORT BY",
                kind: "choice",
                cardinality: "one",
                state: {values: ["@jcr:content/jcr:lastModified"]},
                options: [
                    {value: "@jcr:content/jcr:lastModified", label: "Last Modified", disabled: false},
                    {value: "jcr:content/metadata/dam:size", label: "Size", disabled: false},
                    {value: "jcr:content/metadata/tiff:ImageWidth", label: "Width", disabled: false}
                ]
            },
            {
                id: "__asset_share_discovery_sort_direction",
                title: "SORT DIRECTION",
                kind: "choice",
                cardinality: "one",
                state: {values: ["desc"]},
                options: [
                    {value: "asc", label: "ASC", disabled: false},
                    {value: "desc", label: "DESC", disabled: false}
                ]
            }
    ]
};

postJson(endpoint, {
    version: 2,
    prompt: prompt,
    context: context
}).then(function(response) {
    var validated;

    assert.ok(response.statusCode >= 200 && response.statusCode < 300,
        "agent returned HTTP " + response.statusCode + ": " + response.body);

    validated = discoveryControls.validateResponse(response.body, context);
    assert.strictEqual(validated.version, 2);
    assert.ok(Array.isArray(validated.controlUpdates));
    assert.ok(validated.query.fulltext === null || typeof validated.query.fulltext === "string");
    assert.ok(validated.query.path === null || typeof validated.query.path === "string");

    console.log("discovery agent contract test passed");
}).catch(function(error) {
    console.error(error.stack || error.message || error);
    process.exit(1);
});
