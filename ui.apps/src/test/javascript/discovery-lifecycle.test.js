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

Collection.prototype.add = function(collection) {
    return new Collection(this.elements.concat(collection.elements || []));
};
Collection.prototype.addClass = function() { return this; };
Collection.prototype.appendTo = function() { return this; };
Collection.prototype.attr = function(name, value) {
    if (!this.length) {
        return undefined;
    }
    if (typeof value === "undefined") {
        return this.elements[0].attributes[name];
    }
    this.elements.forEach(function(element) {
        element.attributes[name] = value;
    });
    return this;
};
Collection.prototype.empty = function() {
    this.elements.forEach(function(element) {
        element.children = [];
        element.text = "";
    });
    return this;
};
Collection.prototype.filter = function(callback) {
    return new Collection(this.elements.filter(function(element, index) {
        return callback.call(element, index, element);
    }));
};
Collection.prototype.find = function() {
    return new Collection(searchInput ? [searchInput] : []);
};
Collection.prototype.first = function() {
    return new Collection(this.length ? [this.elements[0]] : []);
};
Collection.prototype.get = function() {
    return this.elements;
};
Collection.prototype.html = function(value) {
    if (typeof value === "undefined") {
        return this.length ? (this.elements[0].html || "") : "";
    }
    this.elements.forEach(function(element) {
        var match = /data-asset-share-discovery-agent-response="([^"]*)"/.exec(value);
        element.html = value;
        element.discoveryAgentResponseRaw = match ? match[1].replace(/&quot;/g, "\"") : null;
    });
    return this;
};
Collection.prototype.keypress = function() { return this; };
Collection.prototype.map = function(callback) {
    return new Collection(this.elements.map(function(element, index) {
        return callback.call(element, index, element);
    }));
};
Collection.prototype.on = function(eventName, selector, handler) {
    eventHandlers.push({eventName: eventName, selector: selector, handler: handler});
    return this;
};
Collection.prototype.removeClass = function() { return this; };
Collection.prototype.text = function(value) {
    if (typeof value === "undefined") {
        return this.length ? this.elements[0].text || "" : "";
    }
    this.elements.forEach(function(element) {
        element.text = value;
    });
    return this;
};
Collection.prototype.trigger = function() { return this; };
Collection.prototype.val = function() {
    return this.length ? this.elements[0].value : undefined;
};

var eventHandlers = [],
    searchInput = {
        attributes: {name: "fulltext", form: "asset-share-commons__form-id__1"},
        value: "/discovery find legal jpeg assets"
    },
    pathInput = {
        attributes: {name: "9_group.0_path", form: "asset-share-commons__form-id__1", "for": "location"},
        value: "/content/dam/campaigns"
    },
    discoveryMessage = {
        attributes: {
            "data-asset-share-query-title": "Discovery constraints",
            "data-asset-share-residual-title": "Discovery constraints",
            "data-asset-share-error-title": "Discovery unavailable",
            "data-asset-share-error-message": "Try again"
        },
        children: []
    },
    discoveryOutput = {attributes: {}, children: []},
    discoveryTitle = {attributes: {}, text: ""},
    discoveryQueries = [],
    submittedQueries = [],
    addressBars = [],
    appliedUpdates = [],
    clearedControls = 0,
    currentAction = null,
    currentResidual = null;

var AGENT_RESPONSE = {
    version: 2,
    query: {fulltext: "jpeg", path: "/content/dam/legal"},
    controlUpdates: [
        {id: "format", kind: "choice", state: {values: ["image/jpeg"]}},
        {
            id: "__asset_share_discovery_sort_orderby",
            kind: "choice",
            state: {values: ["jcr:content/metadata/dam:size"]}
        }
    ]
};

function discoveryResultsFragment() {
    var escaped = JSON.stringify(AGENT_RESPONSE).replace(/"/g, "&quot;");

    // Mirrors the real results.html markup: DiscoverySearchProviderImpl stashes the agent's raw
    // response in Results#getAdditionalData(), and the HTL embeds it as a hidden data attribute in
    // the results fragment for search.js to extract in this same round trip.
    return "<section>results" +
        "<div data-asset-share-id=\"discovery-agent-response\" " +
        "data-asset-share-discovery-agent-response=\"" + escaped + "\"></div>" +
        "</section>";
}

function promise(value) {
    return {
        then: function(success) {
            success(value);
            return this;
        },
        fail: function() {
            return this;
        }
    };
}

function jquery(value) {
    if (typeof value !== "string") {
        return new Collection(value ? [value] : []);
    }
    if (value === "body") {
        return new Collection([{attributes: {}}]);
    }
    if (value.charAt(0) === "<") {
        return new Collection([{attributes: {}, children: []}]);
    }
    if (value === "#asset-share-commons__form-id__1") {
        return new Collection([{attributes: {id: "asset-share-commons__form-id__1"}}]);
    }
    if (value === "[form='asset-share-commons__form-id__1']") {
        return new Collection([searchInput, pathInput]);
    }
    if (value === "button[form='asset-share-commons__form-id__1']" ||
            value === "input[form='asset-share-commons__form-id__1']") {
        return new Collection([]);
    }
    return new Collection([]);
}

jquery.trim = function(value) {
    return (value || "").trim();
};
jquery.get = function(url, query) {
    submittedQueries.push({url: url, query: query});
    return promise("<section>results</section>");
};
jquery.when = function(result) {
    return result;
};

(function discoveryLifecycleUsesValidatedResidualState() {
    var context = {
        console: console,
        window: {
            location: {pathname: "/content/search.html", search: ""},
            top: {location: {pathname: "/content/search.html"}},
            addEventListener: function() {}
        },
        jQuery: jquery,
        AssetShare: {
            Ajax: {},
            Data: {
                attr: function(element, name) {
                    return element.attr("data-asset-share-" + name) || "";
                },
                val: function() {
                    return "";
                }
            },
            Elements: {
                element: function(name, context) {
                    if (name === "form") {
                        return new Collection([{attributes: {}}]);
                    }
                    if (name === "discovery-query") {
                        return new Collection([discoveryMessage]);
                    }
                    if (name === "discovery-query-output") {
                        return new Collection([discoveryOutput]);
                    }
                    if (name === "discovery-query-title") {
                        return new Collection([discoveryTitle]);
                    }
                    if (name === "discovery-agent-response") {
                        var element = context && context.elements && context.elements[0];
                        return element && element.discoveryAgentResponseRaw ?
                            new Collection([{
                                attributes: {
                                    "data-asset-share-discovery-agent-response": element.discoveryAgentResponseRaw
                                }
                            }]) :
                            new Collection([]);
                    }
                    return new Collection([]);
                },
                selector: function(name) {
                    return "[data-asset-share-id='" + name + "']";
                },
                update: function() {}
            },
            Events: {
                SEARCH_BEGIN: "begin",
                SEARCH_END: "end",
                SEARCH_INVALID: "invalid"
            },
            Navigation: {
                addressBar: function(url) {
                    addressBars.push(url);
                },
                gotoTop: function() {},
                returnUrl: function() {}
            },
            Search: {
                DiscoveryControls: {
                    validateResponse: function(response) {
                        return typeof response === "string" ? JSON.parse(response) : response;
                    }
                },
                Form: function() {
                    return {
                        id: function() {
                            return "asset-share-commons__form-id__1";
                        },
                        serializeDiscoveryContextFor: function(action, reset, removeNames, residual) {
                            currentResidual = residual;
                            return JSON.stringify({
                                query: {
                                    fulltext: residual.fulltext,
                                    path: residual.path,
                                    allowedPathRoots: ["/content/dam"]
                                },
                                controls: []
                            });
                        },
                        applyDiscoveryControlUpdates: function(updates) {
                            appliedUpdates.push(updates);
                        },
                        clearDiscoveryControls: function() {
                            clearedControls += 1;
                        },
                        serializeDiscoveryStateFor: function(residual, action) {
                            currentAction = action;
                            return "fulltext=" + encodeURIComponent(residual.fulltext || "") +
                                "&path=" + encodeURIComponent(residual.path || "") +
                                "&action=" + action;
                        },
                        serializeFor: function(action) {
                            return "action=" + action;
                        },
                        isApplyingDiscoveryControls: function() {
                            return false;
                        },
                        isPathControl: function(id) {
                            return id === "location";
                        },
                        isValid: function() {
                            return true;
                        },
                        submit: function(action, reset, success) {
                            success("<section>results</section>");
                            return true;
                        },
                        submitQuery: function(query, success) {
                            submittedQueries.push({query: query});
                            success("<section>results</section>");
                            return promise();
                        },
                        submitDiscoveryQuery: function(prompt, contextJson, baselineQuery, success) {
                            discoveryQueries.push({
                                prompt: prompt,
                                context: contextJson,
                                baselineQuery: baselineQuery
                            });
                            success(discoveryResultsFragment());
                            return promise();
                        }
                    };
                }
            },
            Util: {
                isSameOrigin: function() {
                    return true;
                }
            }
        }
    };

    context.$ = jquery;
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search.js"
    ), "utf8"), context);
    context.AssetShare.Search.DiscoveryControls = {
        validateResponse: function(response) {
            return typeof response === "string" ? JSON.parse(response) : response;
        }
    };

    context.AssetShare.Search.search({preventDefault: function() {}});

    assert.strictEqual(discoveryQueries.length, 1, "initial discovery calls the agent in a single round trip");
    assert.strictEqual(clearedControls, 1, "discovery clears writable controls before snapshot");
    assert.deepStrictEqual(appliedUpdates[0][0].state.values, ["image/jpeg"]);
    assert.deepStrictEqual(appliedUpdates[0][1].state.values, ["jcr:content/metadata/dam:size"]);
    assert.ok(discoveryQueries[0].prompt, "the agent request carries the parsed /discovery prompt");
    assert.ok(addressBars[addressBars.length - 1].indexOf("path=%2Fcontent%2Fdam%2Flegal") > -1,
        "the deep-link address bar reflects the agent-resolved residual path, synced after control apply");

    context.AssetShare.Search.search({preventDefault: function() {}});
    assert.strictEqual(discoveryQueries.length, 2, "discovery submit asks the agent for fresh control state");
    assert.strictEqual(clearedControls, 2);

    eventHandlers.filter(function(handler) {
        return handler.selector === "[for][form=\"asset-share-commons__form-id__1\"]";
    })[0].handler.call(pathInput);

    context.AssetShare.Search.loadMore({preventDefault: function() {}});
    assert.strictEqual(currentAction, "load-more");
    assert.strictEqual(currentResidual.path, null);
}());

console.log("discovery lifecycle tests passed");
