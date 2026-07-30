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
    vm = require("vm"),
    SEARCH_SOURCE = fs.readFileSync(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search.js"
    ), "utf8");

function Collection(elements, harness) {
    this.elements = elements || [];
    this.length = this.elements.length;
    this[0] = this.elements[0];
    this.harness = harness;
}

Collection.prototype.add = function(collection) {
    return new Collection(this.elements.concat(collection.elements || []).filter(function(element, index, values) {
        return values.indexOf(element) === index;
    }), this.harness);
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
        element.text = "";
    });
    return this;
};
Collection.prototype.filter = function(callback) {
    return new Collection(this.elements.filter(function(element, index) {
        return callback.call(element, index, element);
    }), this.harness);
};
Collection.prototype.find = function() {
    if (this.elements[0] === this.harness.discoverySearch) {
        return new Collection([this.harness.searchInput], this.harness);
    }
    if (this.elements[0] === this.harness.formElement) {
        return new Collection([
            this.harness.searchInput,
            this.harness.customerInput
        ], this.harness);
    }
    return new Collection([], this.harness);
};
Collection.prototype.first = function() {
    return new Collection(this.length ? [this.elements[0]] : [], this.harness);
};
Collection.prototype.get = function() {
    return this.elements;
};
Collection.prototype.is = function(selector) {
    return Boolean(this.length && selector === "input,textarea" &&
        this.elements[0].attributes && this.elements[0].attributes.name);
};
Collection.prototype.keypress = function() { return this; };
Collection.prototype.map = function(callback) {
    return new Collection(this.elements.map(function(element, index) {
        return callback.call(element, index, element);
    }), this.harness);
};
Collection.prototype.on = function() { return this; };
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
Collection.prototype.trigger = function(eventName, values) {
    this.harness.events.push({name: eventName, values: values});
    return this;
};
Collection.prototype.val = function() {
    return this.length ? this.elements[0].value : undefined;
};

function promise(failure) {
    return {
        then: function() {
            return this;
        },
        fail: function(callback) {
            if (failure) {
                callback();
            }
            return this;
        }
    };
}

function createHarness(response, requestFails, discoveryEnabled) {
    var harness = {
            response: response,
            requestFails: requestFails,
            events: [],
            resolverCalls: [],
            navigations: [],
            manualSearches: 0,
            formElement: {
                attributes: {id: "asset-share-commons__form-id__1"}
            },
            discoverySearch: {attributes: {}},
            searchInput: {
                attributes: {name: "fulltext", form: "asset-share-commons__form-id__1"},
                value: "/discovery find legal jpeg assets"
            },
            customerInput: {
                attributes: {name: "customer", form: "asset-share-commons__form-id__1"},
                value: "ordinary customer value"
            },
            discoveryMessage: {
                attributes: {
                    "data-asset-share-error-title": "Discovery unavailable",
                    "data-asset-share-error-message": "Try again"
                },
                text: ""
            },
            discoveryOutput: {attributes: {}, text: ""},
            discoveryTitle: {attributes: {}, text: ""}
        },
        jquery,
        context;

    function collection(elements) {
        return new Collection(elements, harness);
    }

    function jquery(value) {
        if (typeof value !== "string") {
            return collection(value ? [value] : []);
        }
        if (value === "body") {
            return collection([{attributes: {}}]);
        }
        if (value.charAt(0) === "<") {
            return collection([{attributes: {}}]);
        }
        if (value === "#asset-share-commons__form-id__1") {
            return collection([harness.formElement]);
        }
        if (value === "[form='asset-share-commons__form-id__1']") {
            return collection([harness.searchInput, harness.customerInput]);
        }
        return collection([]);
    }

    jquery.trim = function(value) {
        return (value || "").trim();
    };

    context = {
        console: console,
        document: {
            createElement: function() {
                var anchor = {};
                Object.defineProperty(anchor, "href", {
                    set: function(value) {
                        var parsed = new URL(value, "https://example.com/content/search.html");
                        anchor.protocol = parsed.protocol;
                        anchor.host = parsed.host;
                    }
                });
                return anchor;
            }
        },
        window: {
            location: {
                href: "https://example.com/content/search.html",
                pathname: "/content/search.html",
                search: "",
                assign: function(url) {
                    harness.navigations.push(url);
                }
            },
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
                element: function(name) {
                    if (name === "form") {
                        return collection([harness.formElement]);
                    }
                    if (name === "discovery-search") {
                        return collection([harness.discoverySearch]);
                    }
                    if (name === "discovery-query") {
                        return collection([harness.discoveryMessage]);
                    }
                    if (name === "discovery-query-output") {
                        return collection([harness.discoveryOutput]);
                    }
                    if (name === "discovery-query-title") {
                        return collection([harness.discoveryTitle]);
                    }
                    return collection([]);
                },
                selector: function(name) {
                    return "[data-asset-share-id='" + name + "']";
                },
                update: function() {
                    throw new Error("discovery must not update result or filter DOM");
                }
            },
            Events: {
                SEARCH_BEGIN: "begin",
                SEARCH_END: "end",
                SEARCH_INVALID: "invalid"
            },
            Navigation: {
                addressBar: function() {},
                gotoTop: function() {},
                returnUrl: function() {}
            },
            Search: {
                Form: function() {
                    return {
                        id: function() {
                            return "asset-share-commons__form-id__1";
                        },
                        serializeDiscoveryStateFor: function(action, removeNames) {
                            harness.serialized = {
                                action: action,
                                removeNames: removeNames
                            };
                            return "4_group.propertyvalues.0_values=image%2Fpng" +
                                "&customer=one&customer=two&p.offset=24";
                        },
                        isValid: function() {
                            return true;
                        },
                        isDiscoveryEnabled: function() {
                            return discoveryEnabled !== false;
                        },
                        submit: function() {
                            harness.manualSearches += 1;
                            return true;
                        },
                        submitDiscoveryResolution: function(prompt, baseline, success) {
                            harness.resolverCalls.push({prompt: prompt, baseline: baseline});
                            if (!requestFails) {
                                success(response);
                            }
                            return promise(requestFails);
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
    vm.runInContext(SEARCH_SOURCE, context);
    harness.search = context.AssetShare.Search;
    return harness;
}

(function navigatesToCanonicalSameOriginUrl() {
    var harness = createHarness({
        version: 1,
        redirectUrl: "/content/search.html?4_group.propertyvalues.0_values=image%2Fjpeg&p.offset=0"
    }, false);

    harness.search.search({preventDefault: function() {}});

    assert.strictEqual(harness.resolverCalls.length, 1);
    assert.strictEqual(harness.resolverCalls[0].prompt, "find legal jpeg assets");
    assert.ok(harness.resolverCalls[0].baseline.indexOf("customer=one&customer=two") > -1,
        "current customer parameters are submitted to the resolver");
    assert.deepStrictEqual(harness.serialized.removeNames, ["fulltext"],
        "the /discovery command field is excluded from the submitted search state");
    assert.deepStrictEqual(harness.navigations, [
        "/content/search.html?4_group.propertyvalues.0_values=image%2Fjpeg&p.offset=0"
    ]);
    assert.strictEqual(harness.events[0].name, "begin");
}());

(function rejectsCrossOriginRedirect() {
    var harness = createHarness({
        version: 1,
        redirectUrl: "https://attacker.example/content/search.html"
    }, false);

    harness.search.search({preventDefault: function() {}});

    assert.deepStrictEqual(harness.navigations, []);
    assert.ok(harness.events.some(function(event) {
        return event.name === "invalid";
    }));
    assert.strictEqual(harness.discoveryOutput.text, "Try again");
}());

(function staysOnPageWhenResolverFails() {
    var harness = createHarness(null, true);

    harness.search.search({preventDefault: function() {}});

    assert.deepStrictEqual(harness.navigations, []);
    assert.strictEqual(harness.discoveryTitle.text, "Discovery unavailable");
    assert.strictEqual(harness.discoveryOutput.text, "Try again");
}());

(function failsClearlyWhenDiscoveryIsNotConfigured() {
    var harness = createHarness(null, false, false);

    harness.search.search({preventDefault: function() {}});

    assert.deepStrictEqual(harness.navigations, []);
    assert.strictEqual(harness.resolverCalls.length, 0);
    assert.strictEqual(harness.discoveryTitle.text, "Discovery unavailable");
    assert.ok(harness.events.some(function(event) {
        return event.name === "invalid";
    }));
}());

(function ignoresDiscoveryTextInNonCommandFields() {
    var harness = createHarness(null, false);

    harness.searchInput.value = "ordinary search";
    harness.customerInput.value = "/discovery is a legitimate customer value";
    harness.search.search({preventDefault: function() {}});

    assert.strictEqual(harness.resolverCalls.length, 0);
    assert.strictEqual(harness.manualSearches, 1);
}());

console.log("discovery lifecycle tests passed");
