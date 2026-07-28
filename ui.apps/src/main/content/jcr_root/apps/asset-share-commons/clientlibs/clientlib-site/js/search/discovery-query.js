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

    function normalizeOrderByValue(value) {
        var trimmed = (value || "").trim();

        if (trimmed === "" ||
                trimmed.charAt(0) === "@" ||
                trimmed === "path" ||
                trimmed === "nodename") {
            return value;
        }

        return "@" + trimmed;
    }

    function normalizeOrderBy(query) {
        var parameters = parse(query);

        parameters.forEach(function(parameter) {
            if (parameter.name === "orderby") {
                parameter.value = normalizeOrderByValue(parameter.value);
            }
        });

        return serialize(parameters);
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

    function getParameterLeaf(parameterName) {
        var segments = parameterName.split(".");

        return segments[segments.length - 1];
    }

    function getPropertyPredicates(query) {
        var parameters = parse(query),
            predicates = [];

        parameters.forEach(function(parameter) {
            var root = getPropertyPredicateRoot(parameter),
                predicate;

            if (!root || predicates.some(function(candidate) {
                return candidate.root === root;
            })) {
                return;
            }

            predicate = {
                kind: "property",
                root: root,
                propertyPath: normalizePropertyPath(parameter.value),
                values: [],
                lowerBound: null,
                upperBound: null,
                parameters: parameters.filter(function(candidate) {
                    return belongsToRoot(candidate, root);
                })
            };

            predicate.parameters.forEach(function(candidate) {
                var leaf = getParameterLeaf(candidate.name);

                if (/^(?:\d+_)?values?$/.test(leaf)) {
                    predicate.values.push(candidate.value);
                } else if (leaf === "lowerBound") {
                    predicate.kind = "date";
                    predicate.lowerBound = candidate.value;
                } else if (leaf === "upperBound") {
                    predicate.kind = "date";
                    predicate.upperBound = candidate.value;
                }
            });

            predicates.push(predicate);
        });

        return predicates;
    }

    function findPropertyPredicate(query, propertyPath) {
        var normalizedPropertyPath = normalizePropertyPath(propertyPath);

        return getPropertyPredicates(query).filter(function(predicate) {
            return predicate.propertyPath === normalizedPropertyPath;
        })[0] || null;
    }

    function getPathPredicateRoot(parameter) {
        var segments = parameter.name.split("."),
            leaf = segments[segments.length - 1];

        if (!/^(?:\d+_)?path$/.test(leaf)) {
            return null;
        }

        if (segments.length === 1) {
            return leaf === "path" ? "path" : "";
        }

        return segments.slice(0, -1).join(".");
    }

    function getPathPredicatesFromParameters(parameters) {
        var predicates = [];

        parameters.forEach(function(parameter) {
            var root = getPathPredicateRoot(parameter),
                predicate;

            if (root === null) {
                return;
            }

            predicate = predicates.filter(function(candidate) {
                return candidate.root === root;
            })[0];

            if (!predicate) {
                predicate = {
                    kind: "path",
                    root: root,
                    pathNames: [],
                    values: [],
                    parameters: []
                };
                predicates.push(predicate);
            }

            predicate.pathNames.push(parameter.name);
            predicate.values.push(parameter.value);
        });

        predicates.forEach(function(predicate) {
            var groupModifierName = predicate.root ? predicate.root + ".p.or" : "p.or",
                hasOtherGroupParameters = parameters.some(function(parameter) {
                    var belongsToGroup = predicate.root ?
                        parameter.name.indexOf(predicate.root + ".") === 0 :
                        parameter.name.indexOf(".") === -1;

                    return belongsToGroup &&
                        parameter.name !== groupModifierName &&
                        !predicate.pathNames.some(function(pathName) {
                            return parameter.name === pathName ||
                                parameter.name.indexOf(pathName + ".") === 0;
                        });
                });

            predicate.parameters = parameters.filter(function(parameter) {
                if (predicate.pathNames.some(function(pathName) {
                    return parameter.name === pathName ||
                        parameter.name.indexOf(pathName + ".") === 0;
                })) {
                    return true;
                }

                return parameter.name === groupModifierName && !hasOtherGroupParameters;
            });
        });

        return predicates;
    }

    function getPathPredicates(query) {
        return getPathPredicatesFromParameters(parse(query));
    }

    function findMatchingPathPredicates(parameters, pathParameterNames, pathOptionValues) {
        var predicates = getPathPredicatesFromParameters(parameters),
            roots = (pathParameterNames || []).map(function(name) {
                return getPathPredicateRoot({name: name});
            }).filter(function(root, index, allRoots) {
                return root !== null && allRoots.indexOf(root) === index;
            }),
            exactMatches = predicates.filter(function(predicate) {
                return roots.indexOf(predicate.root) > -1;
            });

        if (!(pathParameterNames || []).length && !(pathOptionValues || []).length) {
            return predicates;
        }

        if (exactMatches.length) {
            return exactMatches;
        }

        return predicates.filter(function(predicate) {
            return predicate.values.some(function(value) {
                return (pathOptionValues || []).indexOf(value) > -1;
            });
        });
    }

    function findPathPredicates(query, pathParameterNames, pathOptionValues) {
        return findMatchingPathPredicates(
            parse(query),
            pathParameterNames,
            pathOptionValues
        );
    }

    function getPathValues(query) {
        return getPathPredicates(query).reduce(function(values, predicate) {
            return values.concat(predicate.values);
        }, []);
    }

    function belongsToPathPredicates(parameter, predicates) {
        return predicates.some(function(predicate) {
            return predicate.parameters.indexOf(parameter) > -1;
        });
    }

    function mergePredicates(query, replacements) {
        var parameters = parse(query);

        (replacements || []).forEach(function(replacement) {
            var roots = replacement.propertyPath ?
                    getMatchingPropertyRoots(parameters, replacement.propertyPath) : [],
                pathPredicates = replacement.kind === "path" ?
                    findMatchingPathPredicates(
                        parameters,
                        replacement.pathParameterNames,
                        replacement.pathOptionValues
                    ) : [];

            parameters = parameters.filter(function(parameter) {
                if (replacement.kind === "path" &&
                        belongsToPathPredicates(parameter, pathPredicates)) {
                    return false;
                }

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

    function mergePropertyPredicates(query, replacements) {
        return mergePredicates(query, replacements);
    }

    return {
        findPropertyPredicate: findPropertyPredicate,
        findPathPredicates: findPathPredicates,
        getPathValues: getPathValues,
        getPathPredicates: getPathPredicates,
        getPropertyPredicates: getPropertyPredicates,
        mergePredicates: mergePredicates,
        mergePropertyPredicates: mergePropertyPredicates,
        normalizeOrderBy: normalizeOrderBy,
        normalizePropertyPath: normalizePropertyPath,
        parse: parse,
        serialize: serialize
    };
}));
