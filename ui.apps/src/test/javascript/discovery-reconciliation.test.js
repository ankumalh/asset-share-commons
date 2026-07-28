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
    discoveryControls = require(path.resolve(
        __dirname,
        "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/discovery-controls.js"
    ));

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
        return typeof this.elements[0].attributes[name] !== "undefined" ?
            this.elements[0].attributes[name] :
            this.elements[0][name];
    }
    this.elements.forEach(function(element) {
        element.attributes[name] = value;
    });
    return this;
};
Collection.prototype.closest = function() { return new Collection([]); };
Collection.prototype.data = function() { return null; };
Collection.prototype.each = function(callback) {
    this.elements.forEach(function(element, index) {
        callback.call(element, index, element);
    });
    return this;
};
Collection.prototype.filter = function(callback) {
    return new Collection(this.elements.filter(function(element, index) {
        return callback.call(element, index, element);
    }));
};
Collection.prototype.find = function(selector) {
    if (!this.length) {
        return new Collection([]);
    }
    if (selector === "option") {
        return new Collection(this.elements[0].options || []);
    }
    return new Collection([]);
};
Collection.prototype.first = function() {
    return new Collection(this.length ? [this.elements[0]] : []);
};
Collection.prototype.get = function() {
    return this.elements;
};
Collection.prototype.hide = function() { return this; };
Collection.prototype.is = function(selector) {
    var element = this.elements[0],
        selectors = selector.split(",");

    if (!element) {
        return false;
    }

    return selectors.some(function(candidate) {
        return candidate === element.tagName ||
            candidate === ":input" ||
            candidate === ":checked" && element.checked ||
            candidate === ":disabled" && element.disabled ||
            candidate === ":checkbox" && element.type === "checkbox" ||
            candidate === ":radio" && element.type === "radio" ||
            candidate === "select" && element.tagName === "select" ||
            candidate === "textarea" && element.tagName === "textarea" ||
            candidate === "input" && element.tagName === "input";
    });
};
Collection.prototype.map = function(callback) {
    return new Collection(this.elements.map(function(element, index) {
        return callback.call(element, index, element);
    }));
};
Collection.prototype.prev = function() { return new Collection([]); };
Collection.prototype.prop = function(name, value) {
    if (!this.length) {
        return undefined;
    }
    if (typeof value === "undefined") {
        return this.elements[0][name];
    }
    this.elements.forEach(function(element) {
        element[name] = value;
    });
    return this;
};
Collection.prototype.remove = function() { return this; };
Collection.prototype.serializeArray = function() {
    var serialized = [];

    this.elements.forEach(function(element) {
        if (!element.attributes.name || element.disabled) {
            return;
        }
        if ((element.type === "checkbox" || element.type === "radio") && !element.checked) {
            return;
        }
        if (element.tagName === "select" && Array.isArray(element.value)) {
            element.value.forEach(function(value) {
                if (value) {
                    serialized.push({name: element.attributes.name, value: value});
                }
            });
        } else if (element.value !== "") {
            serialized.push({name: element.attributes.name, value: element.value});
        }
    });

    return serialized;
};
Collection.prototype.text = function() {
    return this.length ? this.elements[0].text || "" : "";
};
Collection.prototype.val = function(value) {
    if (!this.length) {
        return undefined;
    }
    if (typeof value === "undefined") {
        return this.elements[0].value;
    }
    this.elements.forEach(function(element) {
        element.value = value;
        if (element.tagName === "option") {
            element.selected = true;
        }
    });
    return this;
};

function field(attributes, value, tagName, type) {
    return {
        attributes: attributes,
        value: value || "",
        tagName: tagName || "input",
        type: type || "hidden"
    };
}

(function typedContextAndAtomicControlApplication() {
    var formId = "asset-share-commons__form-id__1",
        formElement = {attributes: {id: formId}},
        allowedRoot = {attributes: {"data-asset-share-discovery-allowed-path-root": "/content/dam"}},
        formatProperty = field({
            name: "7_group.propertyvalues.property",
            form: formId,
            "data-asset-share-predicate-id": "format"
        }, "jcr:content/metadata/dc:format"),
        jpeg = field({name: "7_group.propertyvalues.0_values", form: formId, "for": "format", id: "jpeg"},
            "image/jpeg", "input", "checkbox"),
        png = field({name: "7_group.propertyvalues.1_values", form: formId, "for": "format", id: "png"},
            "image/png", "input", "checkbox"),
        dateProperty = field({
            name: "10_group.daterange.property",
            form: formId,
            "data-asset-share-predicate-id": "created"
        }, "jcr:content/jcr:created"),
        lowerBound = field({name: "10_group.daterange.lowerBound", form: formId, "for": "created"},
            "", "input", "text"),
        upperBound = field({name: "10_group.daterange.upperBound", form: formId, "for": "created"},
            "", "input", "text"),
        relativeDateProperty = field({
            name: "12_group.relativedaterange.property",
            form: formId,
            "data-asset-share-predicate-id": "recent"
        }, "jcr:content/metadata/dc:modified"),
        lastDay = field({name: "12_group.relativedaterange.lowerBound", form: formId, "for": "recent"},
            "-1d", "input", "radio"),
        lastMonth = field({name: "12_group.relativedaterange.lowerBound", form: formId, "for": "recent"},
            "-1M", "input", "radio"),
        pathBacking = field({
            name: "9_group.p.or",
            form: formId,
            "data-asset-share-predicate-id": "location"
        }, "true"),
        products = field({name: "9_group.0_path", form: formId, "for": "location"},
            "/content/dam/products", "input", "checkbox"),
        campaigns = field({name: "9_group.1_path", form: formId, "for": "location"},
            "/content/dam/campaigns", "input", "checkbox"),
        textProperty = field({
            name: "11_group.propertyvalues.property",
            form: formId,
            "data-asset-share-predicate-id": "keywords"
        }, "jcr:content/metadata/keywords"),
        delimiter = field({
            name: "11_group.propertyvalues.0_delimiter",
            form: formId,
            "data-asset-share-predicate-id": "keywords"
        }, "|"),
        keywords = field({
            name: "11_group.propertyvalues.values",
            form: formId,
            "for": "keywords",
            minlength: "2",
            maxlength: "20",
            pattern: "^[A-Za-z0-9 |]+$"
        }, "summer", "input", "text"),
        fields = [
            formatProperty, jpeg, png,
            dateProperty, lowerBound, upperBound,
            relativeDateProperty, lastDay, lastMonth,
            pathBacking, products, campaigns,
            textProperty, delimiter, keywords
        ],
        context,
        form,
        snapshot,
        validated,
        beforeInvalid;

    jpeg.checked = false;
    png.checked = true;
    lastDay.checked = true;
    products.checked = true;
    [jpeg, png, products, campaigns, lastDay, lastMonth].forEach(function(input) {
        input.text = input.value;
    });

    function jquery(value) {
        var match;

        if (typeof value !== "string") {
            return new Collection(value ? [value] : []);
        }
        if (value === "form[id=\"" + formId + "\"]") {
            return new Collection([formElement]);
        }
        if (value === "[form=\"" + formId + "\"]") {
            return new Collection(fields);
        }
        if (value === "[data-asset-share-discovery-allowed-path-root]") {
            return new Collection([allowedRoot]);
        }
        if (value === "[data-asset-share-search-actions]" ||
                value.indexOf("[data-asset-share-search-actions*=") === 0) {
            return new Collection([]);
        }
        match = value.match(/^\[data-asset-share-predicate-id\]\[form="([^"]+)"\]$/);
        if (match) {
            return new Collection(fields.filter(function(candidate) {
                return candidate.attributes.form === match[1] &&
                    candidate.attributes["data-asset-share-predicate-id"];
            }));
        }
        match = value.match(/^\[data-asset-share-predicate-id="([^"]+)"\]\[form="([^"]+)"\]$/);
        if (match) {
            return new Collection(fields.filter(function(candidate) {
                return candidate.attributes["data-asset-share-predicate-id"] === match[1] &&
                    candidate.attributes.form === match[2];
            }));
        }
        match = value.match(/^:input\[for="([^"]+)"\]\[form="([^"]+)"\]$/);
        if (match) {
            return new Collection(fields.filter(function(candidate) {
                return candidate.attributes["for"] === match[1] &&
                    candidate.attributes.form === match[2];
            }));
        }
        match = value.match(/^\[name="([^"]+)"\]\[form="([^"]+)"\]$/);
        if (match) {
            return new Collection(fields.filter(function(candidate) {
                return candidate.attributes.name === match[1] && candidate.attributes.form === match[2];
            }));
        }
        match = value.match(/^\[for="([^"]+)"\]$/);
        if (match) {
            return new Collection(fields.filter(function(candidate) {
                return candidate.attributes["for"] === match[1];
            }));
        }
        return new Collection([]);
    }

    jquery.trim = function(value) {
        return (value || "").trim();
    };
    jquery.each = function(values, callback) {
        values.forEach(callback);
    };
    jquery.param = function(values) {
        return values.map(function(fieldValue) {
            return encodeURIComponent(fieldValue.name) + "=" + encodeURIComponent(fieldValue.value);
        }).join("&");
    };

    context = {
        $: jquery,
        jQuery: jquery,
        AssetShare: {
            Search: {DiscoveryControls: discoveryControls},
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
    snapshot = JSON.parse(form.serializeDiscoveryContextFor("search", true, ["fulltext"]));

    assert.deepStrictEqual(snapshot.query.allowedPathRoots, ["/content/dam"]);
    assert.deepStrictEqual(snapshot.controls.map(function(control) {
        return control.kind;
    }), ["choice", "date-range", "relative-date", "path", "text"]);

    validated = discoveryControls.validateResponse({
        version: 2,
        query: {fulltext: null, path: null},
        controlUpdates: [
            {id: "format", state: {values: ["image/jpeg"]}},
            {id: "created", state: {lowerBound: "2026-07-01", upperBound: "2026-07-15"}},
            {id: "recent", state: {values: ["-1M"]}},
            {id: "location", state: {values: ["/content/dam/campaigns"]}},
            {id: "keywords", state: {values: ["red", "blue"]}}
        ]
    }, snapshot);

    form.applyDiscoveryControlUpdates(validated.controlUpdates);

    assert.strictEqual(jpeg.checked, true);
    assert.strictEqual(png.checked, false);
    assert.strictEqual(lowerBound.value, "2026-07-01");
    assert.strictEqual(upperBound.value, "2026-07-15");
    assert.strictEqual(lastDay.checked, false);
    assert.strictEqual(lastMonth.checked, true);
    assert.strictEqual(products.checked, false);
    assert.strictEqual(campaigns.checked, true);
    assert.strictEqual(keywords.value, "red|blue");

    beforeInvalid = {
        jpeg: jpeg.checked,
        png: png.checked,
        campaigns: campaigns.checked,
        keywords: keywords.value
    };
    assert.throws(function() {
        var invalid = discoveryControls.validateResponse({
            version: 2,
            query: {fulltext: null, path: null},
            controlUpdates: [
                {id: "format", state: {values: ["image/png"]}},
                {id: "location", state: {values: ["/content/dam/missing"]}}
            ]
        }, JSON.parse(form.serializeDiscoveryContextFor("search", true, [])));
        form.applyDiscoveryControlUpdates(invalid.controlUpdates);
    });
    assert.deepStrictEqual({
        jpeg: jpeg.checked,
        png: png.checked,
        campaigns: campaigns.checked,
        keywords: keywords.value
    }, beforeInvalid);
    assert.strictEqual(form.isApplyingDiscoveryControls(), false);
}());

console.log("discovery reconciliation tests passed");
