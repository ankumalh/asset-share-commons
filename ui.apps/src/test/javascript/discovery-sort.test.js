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

Collection.prototype.closest = function(selector) {
    var dropdown = this.length && selector === ".ui.dropdown" ?
        this.elements[0].dropdown : null;

    return new Collection(dropdown ? [dropdown] : []);
};

Collection.prototype.dropdown = function(command, value) {
    var dropdown;

    if (!this.length || command !== "set selected") {
        return this;
    }

    dropdown = this.elements[0];
    dropdown.selectedValue = value;
    dropdown.input.value = value;
    dropdown.selectedText = dropdown.items.filter(function(item) {
        return item.attributes["data-value"] === value;
    })[0].text;

    return this;
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
    if (!this.length || selector !== ".item") {
        return new Collection([]);
    }

    return new Collection(this.elements[0].items || []);
};

Collection.prototype.first = function() {
    return new Collection(this.length ? [this.elements[0]] : []);
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

(function discoverySortUpdatesRequestFieldsAndVisibleDropdowns() {
    var formId = "asset-share-commons__form-id__1",
        orderByInput = {
            attributes: {name: "orderby", form: formId},
            value: "@jcr:content/jcr:lastModified"
        },
        directionInput = {
            attributes: {name: "orderby.sort", form: formId},
            value: "asc"
        },
        caseInput = {
            attributes: {name: "orderby.case", form: formId},
            value: "respect"
        },
        orderByDropdown = {
            input: orderByInput,
            items: [
                {
                    attributes: {"data-value": "@jcr:content/jcr:lastModified"},
                    text: "Last Modified"
                },
                {
                    attributes: {"data-value": "@jcr:content/metadata/dam:size"},
                    text: "Size"
                }
            ]
        },
        directionDropdown = {
            input: directionInput,
            items: [
                {attributes: {"data-value": "asc"}, text: "ASC"},
                {attributes: {"data-value": "desc"}, text: "DESC"}
            ]
        },
        fields = [orderByInput, directionInput, caseInput],
        context,
        form;

    orderByInput.dropdown = orderByDropdown;
    directionInput.dropdown = directionDropdown;

    function jquery(value) {
        var nameMatch;

        if (typeof value !== "string") {
            return new Collection(value ? [value] : []);
        }

        nameMatch = value.match(/^\[name="([^"]+)"\]\[form="([^"]+)"\]$/);
        if (nameMatch) {
            return new Collection(fields.filter(function(field) {
                return field.attributes.name === nameMatch[1] &&
                    field.attributes.form === nameMatch[2];
            }));
        }

        return new Collection([]);
    }

    context = {
        $: jquery,
        AssetShare: {
            Search: {
                DiscoveryQuery: discoveryQuery
            }
        },
        jQuery: jquery
    };
    context.AssetShare.Data = {
        attr: function() {
            return "/content/search.html";
        },
        val: function() {
            return "";
        }
    };
    context.AssetShare.Elements = {
        element: function() {
            return new Collection([]);
        }
    };
    context.AssetShare.FormData = function() {};

    vm.createContext(context);
    vm.runInContext(
        fs.readFileSync(path.resolve(
            __dirname,
            "../../main/content/jcr_root/apps/asset-share-commons/clientlibs/clientlib-site/js/search/search-form.js"
        ), "utf8"),
        context
    );

    form = context.AssetShare.Search.Form(context.AssetShare);
    form.applyDiscoverySort([
        "fulltext=plant",
        "orderby=%40jcr%3Acontent%2Fmetadata%2Fdam%3Asize",
        "orderby.sort=desc",
        "orderby.case=ignore"
    ].join("&"));

    assert.strictEqual(orderByInput.value, "@jcr:content/metadata/dam:size");
    assert.strictEqual(orderByDropdown.selectedText, "Size");
    assert.strictEqual(directionInput.value, "desc");
    assert.strictEqual(directionDropdown.selectedText, "DESC");
    assert.strictEqual(caseInput.value, "ignore");
}());

console.log("discovery sort tests passed");
