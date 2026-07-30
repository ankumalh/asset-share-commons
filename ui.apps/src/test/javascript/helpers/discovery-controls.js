/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global AssetShare: false, module: false */

(function (root, factory) {
    "use strict";

    var discoveryControls = factory();

    if (typeof module === "object" && module.exports) {
        module.exports = discoveryControls;
    }

    if (root && root.AssetShare) {
        root.AssetShare.Search.DiscoveryControls = discoveryControls;
    }
}(typeof window !== "undefined" ? window : this, function () {
    "use strict";

    var VERSION = 2,
        MAX_FULLTEXT_LENGTH = 512,
        MAX_PATH_LENGTH = 1024,
        DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

    function keysOf(object) {
        return Object.keys(object || {}).sort();
    }

    function sameKeys(object, names) {
        var keys = keysOf(object),
            expected = names.slice(0).sort();

        return keys.length === expected.length && keys.every(function(key, index) {
            return key === expected[index];
        });
    }

    function fail(message) {
        throw new Error(message);
    }

    function asObject(value, label) {
        if (!value || typeof value !== "object" || Array.isArray(value)) {
            fail(label + " must be an object");
        }
        return value;
    }

    function parseResponse(response) {
        if (typeof response === "string") {
            try {
                return JSON.parse(response);
            } catch (e) {
                fail("response must be JSON");
            }
        }
        return asObject(response, "response");
    }

    function normalizeStringOrNull(value, label, maxLength) {
        if (value === null) {
            return null;
        }
        if (typeof value !== "string") {
            fail(label + " must be a string or null");
        }
        if (value.length > maxLength) {
            fail(label + " exceeds maximum length");
        }
        return value;
    }

    function normalizeValuesState(state, control) {
        var values;

        asObject(state, "state");
        if (!sameKeys(state, ["values"]) || !Array.isArray(state.values)) {
            fail(control.id + " state must contain only values");
        }

        values = state.values.map(function(value) {
            if (typeof value !== "string") {
                fail(control.id + " values must be strings");
            }
            return value;
        });

        if (control.cardinality === "one" && values.length > 1) {
            fail(control.id + " allows only one value");
        }

        if (control.constraints && typeof control.constraints.maxValues === "number" &&
                values.length > control.constraints.maxValues) {
            fail(control.id + " exceeds maxValues");
        }

        return {values: unique(values)};
    }

    function normalizeDateRangeState(state, control) {
        asObject(state, "state");
        if (!sameKeys(state, ["lowerBound", "upperBound"])) {
            fail(control.id + " state must contain only lowerBound and upperBound");
        }

        return {
            lowerBound: normalizeDateValue(state.lowerBound, control.id + ".lowerBound"),
            upperBound: normalizeDateValue(state.upperBound, control.id + ".upperBound")
        };
    }

    function normalizeDateValue(value, label) {
        if (value === null) {
            return null;
        }
        if (typeof value !== "string" || !DATE_PATTERN.test(value)) {
            fail(label + " must use YYYY-MM-DD or null");
        }
        return value;
    }

    function normalizeState(state, control) {
        if (control.kind === "date-range") {
            return normalizeDateRangeState(state, control);
        }
        return normalizeValuesState(state, control);
    }

    function unique(values) {
        return values.filter(function(value, index) {
            return values.indexOf(value) === index;
        });
    }

    function statesEqual(left, right, kind) {
        if (kind === "date-range") {
            return (left.lowerBound || null) === (right.lowerBound || null) &&
                (left.upperBound || null) === (right.upperBound || null);
        }

        return left.values.length === right.values.length && left.values.every(function(value, index) {
            return value === right.values[index];
        });
    }

    function getControlMap(controls) {
        var map = {};

        if (!Array.isArray(controls)) {
            fail("context controls must be an array");
        }

        controls.forEach(function(control) {
            asObject(control, "control");
            if (!control.id || typeof control.id !== "string") {
                fail("control id is required");
            }
            if (map[control.id]) {
                fail("duplicate control id " + control.id);
            }
            map[control.id] = control;
        });

        return map;
    }

    function optionMap(control) {
        var map = {};

        (control.options || []).forEach(function(option) {
            if (!option || typeof option.value !== "string") {
                return;
            }
            map[option.value] = option;
        });

        return map;
    }

    function validateClosedValues(control, state) {
        var options = optionMap(control),
            currentValues = control.state && control.state.values || [];

        if (["choice", "path", "relative-date"].indexOf(control.kind) === -1) {
            return;
        }

        state.values.forEach(function(value) {
            var option = options[value];

            if (!option) {
                fail(control.id + " selected an unknown option");
            }
            if (option.disabled && currentValues.indexOf(value) === -1) {
                fail(control.id + " selected a disabled option");
            }
        });
    }

    function validateText(control, state) {
        var constraints = control.constraints || {},
            pattern = constraints.pattern ? new RegExp(constraints.pattern) : null;

        if (control.kind !== "text") {
            return;
        }

        state.values.forEach(function(value) {
            if (typeof constraints.minLength === "number" && value.length < constraints.minLength) {
                fail(control.id + " value is shorter than minLength");
            }
            if (typeof constraints.maxLength === "number" && value.length > constraints.maxLength) {
                fail(control.id + " value exceeds maxLength");
            }
            if (pattern && !pattern.test(value)) {
                fail(control.id + " value does not match pattern");
            }
        });
    }

    function canonicalizePath(path) {
        var raw = normalizeStringOrNull(path, "query.path", MAX_PATH_LENGTH),
            segments,
            clean = [];

        if (raw === null) {
            return null;
        }
        if (raw.charAt(0) !== "/" || raw.indexOf("?") > -1 || raw.indexOf("#") > -1 ||
                raw.indexOf("\\") > -1) {
            fail("query.path must be an absolute repository path");
        }

        segments = raw.split("/");
        segments.forEach(function(segment, index) {
            if (index === 0 || segment === "") {
                return;
            }
            if (segment === "." || segment === "..") {
                fail("query.path must not contain traversal segments");
            }
            clean.push(segment);
        });

        return "/" + clean.join("/");
    }

    function isUnderRoot(path, root) {
        var normalizedRoot = canonicalizePath(root);

        return path === normalizedRoot || path.indexOf(normalizedRoot + "/") === 0;
    }

    function validateResidualPath(path, controls, allowedPathRoots, resultingStates) {
        var pathOptions = [],
            roots = allowedPathRoots || [],
            withinRoot;

        if (path === null) {
            return null;
        }

        if (!roots.length) {
            fail("query.path is not allowed without allowedPathRoots");
        }

        withinRoot = roots.some(function(root) {
            return isUnderRoot(path, root);
        });
        if (!withinRoot) {
            fail("query.path is outside the allowed roots");
        }

        controls.forEach(function(control) {
            if (control.kind !== "path") {
                return;
            }

            (control.options || []).forEach(function(option) {
                pathOptions.push(option.value);
            });

            if ((resultingStates[control.id].values || []).length) {
                fail("query.path cannot coexist with selected path controls");
            }
        });

        if (pathOptions.indexOf(path) > -1) {
            fail("query.path must be represented by the path control option");
        }

        return path;
    }

    function validateResponse(response, requestContext) {
        var parsed = parseResponse(response),
            context = requestContext.context || requestContext,
            controls = context.controls || [],
            controlMap = getControlMap(controls),
            queryContext = context.query || {},
            allowedPathRoots = queryContext.allowedPathRoots || [],
            seenUpdates = {},
            resultingStates = {},
            validatedUpdates = [],
            diagnostics = [],
            residualPath,
            residualFulltext;

        if (!sameKeys(parsed, ["version", "query", "controlUpdates"]) || parsed.version !== VERSION) {
            fail("response must use v2 with query and controlUpdates");
        }
        asObject(parsed.query, "query");
        if (!sameKeys(parsed.query, ["fulltext", "path"])) {
            fail("query may contain only fulltext and path");
        }
        if (!Array.isArray(parsed.controlUpdates)) {
            fail("controlUpdates must be an array");
        }

        controls.forEach(function(control) {
            resultingStates[control.id] = normalizeState(control.state, control);
        });

        parsed.controlUpdates.forEach(function(update) {
            var control,
                normalizedState;

            asObject(update, "control update");
            if (!sameKeys(update, ["id", "state"])) {
                fail("control update may contain only id and state");
            }
            if (typeof update.id !== "string" || !controlMap[update.id]) {
                fail("control update has an unknown id");
            }
            if (seenUpdates[update.id]) {
                fail("duplicate control update id " + update.id);
            }
            seenUpdates[update.id] = true;

            control = controlMap[update.id];
            normalizedState = normalizeState(update.state, control);
            validateClosedValues(control, normalizedState);
            validateText(control, normalizedState);

            if (statesEqual(normalizedState, resultingStates[control.id], control.kind)) {
                diagnostics.push({id: update.id, code: "noop"});
                return;
            }

            resultingStates[control.id] = normalizedState;
            validatedUpdates.push({
                id: control.id,
                kind: control.kind,
                state: normalizedState
            });
        });

        residualFulltext = normalizeStringOrNull(parsed.query.fulltext, "query.fulltext", MAX_FULLTEXT_LENGTH);
        residualPath = canonicalizePath(parsed.query.path);
        residualPath = validateResidualPath(residualPath, controls, allowedPathRoots, resultingStates);

        return {
            version: VERSION,
            query: {
                fulltext: residualFulltext,
                path: residualPath
            },
            controlUpdates: validatedUpdates,
            diagnostics: diagnostics
        };
    }

    return {
        validateResponse: validateResponse,
        canonicalizePath: canonicalizePath,
        statesEqual: statesEqual
    };
}));
