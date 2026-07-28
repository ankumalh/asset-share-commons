/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global AssetShare: false, module: false */

(function (root, factory) {
    "use strict";

    var discoveryQuery = factory();

    if (typeof module === "object" && module.exports) {
        module.exports = discoveryQuery;
    }

    if (root && root.AssetShare) {
        root.AssetShare.Search.DiscoveryQuery = discoveryQuery;
    }
}(typeof window !== "undefined" ? window : this, function () {
    "use strict";

    function decode(value) {
        return decodeURIComponent((value || "").replace(/\+/g, " "));
    }

    function parse(query) {
        var parameters = [];

        (query || "").replace(/^\?/, "").split("&").forEach(function(pair) {
            var separator,
                name,
                value;

            if (!pair) {
                return;
            }

            separator = pair.indexOf("=");

            try {
                name = decode(separator > -1 ? pair.substring(0, separator) : pair);
                value = decode(separator > -1 ? pair.substring(separator + 1) : "");
            } catch (e) {
                return;
            }

            if (name) {
                parameters.push({name: name, value: value});
            }
        });

        return parameters;
    }

    function serialize(parameters) {
        return parameters.map(function(parameter) {
            return encodeURIComponent(parameter.name) + "=" +
                encodeURIComponent(parameter.value).replace(/%20/g, "+");
        }).join("&");
    }

    function normalizePropertyPath(propertyPath) {
        return (propertyPath || "").trim().replace(/^\.\/+/, "");
    }

    function getPropertyPredicateRoot(parameter) {
        var segments = parameter.name.split("."),
            leaf = segments[segments.length - 1],
            parent = segments.length > 1 ? segments[segments.length - 2] : "";

        if (!/^(?:\d+_)?property$/.test(leaf)) {
            return null;
        }

        if (segments.length === 1 || /^(?:\d+_)?group$/.test(parent)) {
            return parameter.name;
        }

        return segments.slice(0, -1).join(".");
    }

    function getMatchingPropertyRoots(parameters, propertyPath) {
        var normalizedPropertyPath = normalizePropertyPath(propertyPath),
            roots = [];

        parameters.forEach(function(parameter) {
            var root = getPropertyPredicateRoot(parameter);

            if (root &&
                    normalizePropertyPath(parameter.value) === normalizedPropertyPath &&
                    roots.indexOf(root) === -1) {
                roots.push(root);
            }
        });

        return roots;
    }

    function belongsToRoot(parameter, root) {
        return parameter.name === root || parameter.name.indexOf(root + ".") === 0;
    }

    function mergePropertyPredicates(query, replacements) {
        var parameters = parse(query);

        (replacements || []).forEach(function(replacement) {
            var roots = getMatchingPropertyRoots(parameters, replacement.propertyPath);

            parameters = parameters.filter(function(parameter) {
                return !roots.some(function(root) {
                    return belongsToRoot(parameter, root);
                });
            });

            (replacement.parameters || []).forEach(function(parameter) {
                parameters.push({
                    name: parameter.name,
                    value: parameter.value
                });
            });
        });

        return serialize(parameters);
    }

    return {
        mergePropertyPredicates: mergePropertyPredicates,
        normalizePropertyPath: normalizePropertyPath,
        parse: parse,
        serialize: serialize
    };
}));
