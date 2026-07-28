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

function Collection(elements) {
    this.elements = elements || [];
    this.length = this.elements.length;
    this[0] = this.elements[0];
}

Collection.prototype.attr = function(name) {
    return this.length ? this.elements[0].attributes[name] : undefined;
};

Collection.prototype.closest = function() {
    return new Collection([]);
};

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
    if (!this.length || selector !== "option") {
        return new Collection([]);
    }

    return new Collection(this.elements[0].options || []);
};

Collection.prototype.first = function() {
    return new Collection(this.length ? [this.elements[0]] : []);
};

Collection.prototype.get = function() {
    return this.elements;
};

Collection.prototype.is = function(selector) {
    var element = this.elements[0],
        selectors = selector.split(",");

    if (!element) {
        return false;
    }

    return selectors.some(function(candidate) {
        return candidate === element.tagName ||
            candidate === ":checkbox" && element.type === "checkbox" ||
            candidate === ":radio" && element.type === "radio";
    });
};

Collection.prototype.map = function(callback) {
    return new Collection(this.elements.map(function(element, index) {
        return callback.call(element, index, element);
    }));
};

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

Collection.prototype.val = function(value) {
    if (!this.length) {
        return undefined;
    }

    if (typeof value === "undefined") {
        return this.elements[0].value;
    }

    this.elements.forEach(function(element) {
        element.value = value;
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

(function generatedQueryUpdatesOnlyExistingControls() {
    var formId = "asset-share-commons__form-id__1",
        formatProperty = field({
            name: "7_group.propertyvalues.property",
            form: formId,
            "data-asset-share-predicate-id": "format"
        }, "jcr:content/metadata/dc:format"),
        jpeg = field({name: "7_group.propertyvalues.0_values", form: formId, "for": "format"},
            "image/jpeg", "input", "checkbox"),
        png = field({name: "7_group.propertyvalues.1_values", form: formId, "for": "format"},
            "image/png", "input", "checkbox"),
        styleProperty = field({
            name: "8_group.propertyvalues.property",
            form: formId,
            "data-asset-share-predicate-id": "style"
        }, "jcr:content/metadata/style"),
        style = field({name: "8_group.propertyvalues.values", form: formId, "for": "style"},
            "portrait", "select"),
        pathBacking = field({
            name: "9_group.p.or",
            form: formId,
            "data-asset-share-predicate-id": "location"
        }, "true"),
        products = field({name: "9_group.0_path", form: formId, "for": "location"},
            "/content/dam/products", "input", "checkbox"),
        campaigns = field({name: "9_group.1_path", form: formId, "for": "location"},
            "/content/dam/campaigns", "input", "checkbox"),
        dateProperty = field({
            name: "10_group.daterange.property",
            form: formId,
            "data-asset-share-predicate-id": "modified"
        }, "jcr:content/jcr:lastModified"),
        lowerBound = field({name: "10_group.daterange.lowerBound", form: formId, "for": "modified"},
            "2026-01-01", "input", "text"),
        upperBound = field({name: "10_group.daterange.upperBound", form: formId, "for": "modified"},
            "2026-01-31", "input", "text"),
        relativeDateProperty = field({
            name: "12_group.relativedaterange.property",
            form: formId,
            "data-asset-share-predicate-id": "recent"
        }, "jcr:content/metadata/dc:modified"),
        lastDay = field({
            name: "12_group.relativedaterange.lowerBound",
            form: formId,
            "for": "recent"
        }, "-1d", "input", "radio"),
        lastMonth = field({
            name: "12_group.relativedaterange.lowerBound",
            form: formId,
            "for": "recent"
        }, "-1M", "input", "radio"),
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
        keywords = field({name: "11_group.propertyvalues.values", form: formId, "for": "keywords"},
            "local", "input", "text"),
        fields = [
            formatProperty, jpeg, png,
            styleProperty, style,
            pathBacking, products, campaigns,
            dateProperty, lowerBound, upperBound,
            relativeDateProperty, lastDay, lastMonth,
            textProperty, delimiter, keywords
        ],
        initialFieldCount = fields.length,
        context,
        form;

    jpeg.checked = true;
    png.checked = false;
    products.checked = true;
    campaigns.checked = false;
    lastDay.checked = true;
    lastMonth.checked = false;
    style.options = [
        field({}, "", "option"),
        field({}, "portrait", "option"),
        field({}, "landscape", "option")
    ];

    function jquery(value) {
        var match;

        if (typeof value !== "string") {
            return new Collection(value ? [value] : []);
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

        return new Collection([]);
    }

    context = {
        $: jquery,
        jQuery: jquery,
        AssetShare: {
            Search: {DiscoveryQuery: discoveryQuery},
            Data: {
                attr: function(element, name) {
                    return element.attr("data-asset-share-" + name) || "";
                },
                val: function() {
                    return "";
                }
            },
            Elements: {
                element: function() {
                    return new Collection([]);
                }
            },
            FormData: function() {}
        }
    };

    vm.createContext(context);
    vm.runInContext(
        fs.readFileSync(path.resolve(
            __dirname,
            "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search-form.js"
        ), "utf8"),
        context
    );

    form = context.AssetShare.Search.Form(context.AssetShare);
    form.applyDiscoveryPredicates([
        "0_property=jcr%3Acontent%2Fmetadata%2Fdc%3Aformat",
        "0_property.1_value=image%2Fpng",
        "1_property=jcr%3Acontent%2Fmetadata%2Fstyle",
        "1_property.1_value=square",
        "2_group.0_path=%2Fcontent%2Fdam%2Fcampaigns",
        "3_group.daterange.property=jcr%3Acontent%2Fjcr%3AlastModified",
        "3_group.daterange.lowerBound=2026-07-01",
        "3_group.daterange.upperBound=2026-07-27",
        "6_group.relativedaterange.property=jcr%3Acontent%2Fmetadata%2Fdc%3Amodified",
        "6_group.relativedaterange.lowerBound=-1M",
        "4_property=jcr%3Acontent%2Fmetadata%2Fkeywords",
        "4_property.1_value=red",
        "4_property.2_value=blue",
        "5_property=jcr%3Acontent%2Fmetadata%2Funrepresented",
        "5_property.1_value=kept-in-query"
    ].join("&"));

    assert.strictEqual(jpeg.checked, false);
    assert.strictEqual(png.checked, true);
    assert.strictEqual(style.value, "");
    assert.strictEqual(style.options.length, 3, "must not create an option for square");
    assert.strictEqual(products.checked, false);
    assert.strictEqual(campaigns.checked, true);
    assert.strictEqual(lowerBound.value, "2026-07-01");
    assert.strictEqual(upperBound.value, "2026-07-27");
    assert.strictEqual(lastDay.checked, false);
    assert.strictEqual(lastMonth.checked, true);
    assert.strictEqual(keywords.value, "red|blue");
    assert.strictEqual(fields.length, initialFieldCount, "must not create UI fields");
    assert.strictEqual(form.isApplyingDiscoveryPredicates(), false);
}());

console.log("discovery reconciliation tests passed");