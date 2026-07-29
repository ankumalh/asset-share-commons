/*
 * Asset Share Commons
 *
 * Copyright [2017]  Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global es6: true, $: false, AssetShare: false */

AssetShare.Search.Form = function (ns) {
    "use strict";

    var url,
        mode,
        formData,
        SORT_ORDERBY_CONTROL_ID = "__asset_share_discovery_sort_orderby",
        SORT_DIRECTION_CONTROL_ID = "__asset_share_discovery_sort_direction",
        applyingDiscoveryControls = false;

    function getId() {
       return "asset-share-commons__form-id__1";
    }

    function _htmlForm() {
        return $('form[id="' + getId() + '"]');
    }

    function getUrl() {
        return url;
    }

    function reset() {
        formData = new ns.FormData(_htmlForm());
    }

    function clean(sourceFormData) {
        var cleanFormData = new ns.FormData();

        sourceFormData.forEach(function (inputName, inputValue) {
            var candidateInputs = $("[name=\"" + inputName + "\"][form=\"" + getId() + "\"]");

            candidateInputs.each(function() {
                var candidateInput = $(this),
                    candidatePredicateId = ns.Data.attr(candidateInput, "predicate-id") || null,
                    candidateInputAdded = false;

               if (candidatePredicateId) {
                    $("[for=\"" + candidatePredicateId + "\"]").each(function (index, relatedInput) {
                        if (!candidateInputAdded) {
                            var relativeInputName = $(relatedInput).attr("name"),
                                relatedInputValue = sourceFormData.get(relativeInputName);
                            if (relatedInputValue) {
                                if (cleanFormData.getAll(inputName).indexOf(inputValue) === -1) {
                                    cleanFormData.add(inputName, inputValue);
                                }
                                candidateInputAdded = true;
                            }
                        }
                    });
                } else if (inputValue !== '' && cleanFormData.getAll(inputName).indexOf(inputValue) === -1) {
                   cleanFormData.add(inputName, inputValue);
                }
            });
        });

        return cleanFormData;
    }

    function removeAll(formDataToUpdate, name) {
        while (typeof formDataToUpdate.get(name) !== "undefined") {
            formDataToUpdate.remove(name);
        }
    }

    function applyActionFields(targetFormData, event) {
        $("[data-asset-share-search-actions]").each(function () {
            removeAll(targetFormData, $(this).attr("name"));
        });

        $("[data-asset-share-search-actions*=\"all\"],[data-asset-share-search-actions*=\"" + event + "\"]").each(function () {
            if ($.trim($(this).val()) !== "") {
                targetFormData.set($(this).attr("name"), $(this).val());
            }
        });

        return targetFormData;
    }

    function buildFormData(sourceFormData, event) {
        var clone = clean(sourceFormData.clone());

        clone = _adjustFormData(clone);
        return applyActionFields(clone, event);
    }

    function serializeFor(event, resetForm) {
        if (resetForm) {
            reset();
        }
        return buildFormData(formData, event).serialize();
    }

    function deserialize(query) {
        var deserializedFormData = new ns.FormData();

        $.each((query || "").replace(/^\?/, "").split("&"), function(index, pair) {
            var separator,
                name,
                value;

            if (!pair) {
                return;
            }

            separator = pair.indexOf("=");
            name = separator > -1 ? pair.substring(0, separator) : pair;
            value = separator > -1 ? pair.substring(separator + 1) : "";

            try {
                name = decodeURIComponent(name.replace(/\+/g, " "));
                value = decodeURIComponent(value.replace(/\+/g, " "));
            } catch (e) {
                return;
            }

            if (name) {
                deserializedFormData.add(name, value);
            }
        });

        return deserializedFormData;
    }

    function serializeQueryFor(query, event) {
        return applyActionFields(deserialize(query), event).serialize();
    }

    function isPathInput(input) {
        var name = input.attr("name") || "",
            segments = name.split(".");

        return /^(?:\d+_)?path$/.test(segments[segments.length - 1]);
    }

    function getInputLabel(input) {
        var inputElement = $(input),
            label;

        if (inputElement.attr("id")) {
            label = $("label[for=\"" + inputElement.attr("id") + "\"]").first().text();
        }

        if (!label) {
            label = inputElement.closest(".checkbox").find("label").first().text();
        }

        return $.trim(label || "");
    }

    function getPredicateTitle(predicateId, relatedInputs) {
        var title = relatedInputs.closest(".content").prev(".title").first().text();

        if (!title) {
            title = relatedInputs.closest(".accordion").find(".title").first().text();
        }

        return $.trim(title || predicateId).replace(/\s+/g, " ");
    }

    function getDelimiter(predicateFields) {
        var delimiterField = predicateFields.filter(function() {
            return /_delimiter$/.test($(this).attr("name") || "");
        }).first();

        return delimiterField.length ? delimiterField.val() : ",";
    }

    function splitValues(value, delimiter) {
        return (value || "").split(delimiter || ",").map(function(item) {
            return $.trim(item);
        }).filter(function(item, index, values) {
            return item && values.indexOf(item) === index;
        });
    }

    function readChoiceValues(relatedInputs) {
        var values = [];

        relatedInputs.each(function(index, input) {
            var inputElement = $(input),
                value;

            if (inputElement.is("select")) {
                value = inputElement.val();
                if (Array.isArray(value)) {
                    values = values.concat(value.filter(Boolean));
                } else if (value) {
                    values.push(value);
                }
            } else if (inputElement.is(":checkbox,:radio") && inputElement.is(":checked")) {
                values.push(inputElement.val());
            }
        });

        return values.filter(function(value, index) {
            return values.indexOf(value) === index;
        });
    }

    function getPredicateOptions(relatedInputs) {
        var options = [];

        relatedInputs.each(function(index, input) {
            var inputElement = $(input),
                type = inputElement.attr("type") || input.tagName.toLowerCase();

            if (inputElement.is("select")) {
                inputElement.find("option").each(function(optionIndex, option) {
                    var optionElement = $(option);

                    if ($.trim(optionElement.val()) !== "") {
                        options.push({
                            value: optionElement.val(),
                            label: $.trim(optionElement.text()).replace(/\s+/g, " "),
                            disabled: optionElement.is(":disabled")
                        });
                    }
                });
            } else if (type === "checkbox" || type === "radio") {
                options.push({
                    value: inputElement.val(),
                    label: getInputLabel(input),
                    disabled: inputElement.is(":disabled")
                });
            }
        });

        return options;
    }

    function readHtmlConstraints(input, delimiter) {
        var constraints = {},
            maxValues = input.attr("data-asset-share-max-values");

        if (input.attr("required")) {
            constraints.required = true;
        }
        if (input.attr("minlength")) {
            constraints.minLength = parseInt(input.attr("minlength"), 10);
        }
        if (input.attr("maxlength")) {
            constraints.maxLength = parseInt(input.attr("maxlength"), 10);
        }
        if (input.attr("pattern")) {
            constraints.pattern = input.attr("pattern");
        }
        if (maxValues) {
            constraints.maxValues = parseInt(maxValues, 10);
        }
        if (delimiter) {
            constraints.delimited = true;
        }

        return constraints;
    }

    function controlKind(relatedInputs) {
        var pathInputs = relatedInputs.filter(function() {
                return isPathInput($(this));
            }),
            lowerInputs = relatedInputs.filter(function() {
                return /\.lowerBound$/.test($(this).attr("name") || "");
            }),
            upperInputs = relatedInputs.filter(function() {
                return /\.upperBound$/.test($(this).attr("name") || "");
            }),
            choiceInputs = relatedInputs.filter(function() {
                return $(this).is("select,:checkbox,:radio");
            }),
            textInputs = relatedInputs.filter(function() {
                return $(this).is("input,textarea") && !$(this).is(":checkbox,:radio");
            });

        if (pathInputs.length && choiceInputs.length === pathInputs.length) {
            return "path";
        }
        if (lowerInputs.length && !lowerInputs.first().is("select,:checkbox,:radio")) {
            return "date-range";
        }
        if (lowerInputs.length && choiceInputs.length === relatedInputs.length) {
            return "relative-date";
        }
        if (choiceInputs.length === relatedInputs.length && relatedInputs.length) {
            return "choice";
        }
        if (textInputs.length === relatedInputs.length && textInputs.length === 1 && !upperInputs.length) {
            return "text";
        }

        return null;
    }

    function cardinalityFor(kind, relatedInputs) {
        var first = relatedInputs.first();

        if (kind === "text" || kind === "date-range") {
            return null;
        }
        if (first.is(":radio")) {
            return "one";
        }
        if (first.is("select")) {
            return first.prop("multiple") ? "many" : "one";
        }
        return "many";
    }

    function descriptorFor(predicateId) {
        var predicateFields = $("[data-asset-share-predicate-id=\"" + predicateId +
                "\"][form=\"" + getId() + "\"]"),
            relatedInputs = $(":input[for=\"" + predicateId + "\"][form=\"" + getId() + "\"]"),
            kind = controlKind(relatedInputs),
            descriptor,
            delimiter,
            textInput,
            lowerInput,
            upperInput;

        if (!kind) {
            return null;
        }

        descriptor = {
            id: predicateId,
            title: getPredicateTitle(predicateId, relatedInputs),
            kind: kind
        };

        if (kind === "choice" || kind === "path" || kind === "relative-date") {
            descriptor.cardinality = cardinalityFor(kind, relatedInputs);
            descriptor.state = {values: readChoiceValues(relatedInputs)};
            descriptor.options = getPredicateOptions(relatedInputs);
        } else if (kind === "text") {
            delimiter = getDelimiter(predicateFields);
            textInput = relatedInputs.first();
            descriptor.state = {values: splitValues(textInput.val(), delimiter)};
            descriptor.constraints = readHtmlConstraints(textInput, delimiter);
        } else if (kind === "date-range") {
            lowerInput = relatedInputs.filter(function() {
                return /\.lowerBound$/.test($(this).attr("name") || "");
            }).first();
            upperInput = relatedInputs.filter(function() {
                return /\.upperBound$/.test($(this).attr("name") || "");
            }).first();
            descriptor.state = {
                lowerBound: lowerInput.val() || null,
                upperBound: upperInput.val() || null
            };
            descriptor.constraints = {format: "YYYY-MM-DD"};
        }

        return descriptor;
    }

    function getDiscoveryControls() {
        var controls = [],
            seen = {};

        $("[data-asset-share-predicate-id][form=\"" + getId() + "\"]").each(function(index, element) {
            var predicateId = ns.Data.attr($(element), "predicate-id"),
                descriptor;

            if (!predicateId || seen[predicateId]) {
                return;
            }
            seen[predicateId] = true;
            descriptor = descriptorFor(predicateId);
            if (descriptor) {
                controls.push(descriptor);
            }
        });

        return controls;
    }

    function getAllowedPathRoots() {
        return $("[data-asset-share-discovery-allowed-path-root]").map(function() {
            return $(this).attr("data-asset-share-discovery-allowed-path-root");
        }).get().filter(Boolean);
    }

    function getSortInput(name) {
        return $("[data-asset-share-id=\"sort\"][name=\"" + name + "\"][form=\"" + getId() + "\"]").first();
    }

    function getSortOptions(input) {
        var options = [];

        input.closest(".ui.dropdown").find(".item").each(function(index, item) {
            var option = $(item),
                value = option.attr("data-value");

            if (!value) {
                return;
            }

            options.push({
                value: value,
                label: $.trim(option.text()).replace(/\s+/g, " "),
                disabled: option.is(":disabled")
            });
        });

        return options;
    }

    function getDiscoverySortControls() {
        var orderByInput = getSortInput("orderby"),
            directionInput = getSortInput("orderby.sort"),
            options,
            directions;

        if (!orderByInput.length || !directionInput.length) {
            return [];
        }

        options = getSortOptions(orderByInput);
        directions = getSortOptions(directionInput);
        if (!options.length || !directions.length) {
            return [];
        }

        return [
            {
                id: SORT_ORDERBY_CONTROL_ID,
                title: "SORT BY",
                kind: "choice",
                cardinality: "one",
                state: {values: orderByInput.val() ? [orderByInput.val()] : []},
                options: options
            },
            {
                id: SORT_DIRECTION_CONTROL_ID,
                title: "SORT DIRECTION",
                kind: "choice",
                cardinality: "one",
                state: {values: directionInput.val() ? [directionInput.val()] : []},
                options: directions
            }
        ];
    }

    function getResidualQueryFromForm(removeKeys) {
        var current = buildFormData(formData, "search"),
            fulltext = current.get("fulltext") || null,
            path = current.get("path") || null;

        removeKeys = removeKeys || [];
        if (removeKeys.indexOf("fulltext") > -1 || /^\/discovery(?:\s|$)/.test($.trim(fulltext || ""))) {
            fulltext = null;
        }
        if (removeKeys.indexOf("path") > -1) {
            path = null;
        }

        return {
            fulltext: fulltext,
            path: path
        };
    }

    function serializeDiscoveryContextFor(event, resetForm, removeKeys, residualQuery) {
        var query,
            context;

        if (resetForm) {
            reset();
        }

        query = residualQuery || getResidualQueryFromForm(removeKeys);

        context = {
            query: {
                fulltext: query.fulltext || null,
                path: query.path || null,
                allowedPathRoots: getAllowedPathRoots()
            },
            controls: getDiscoveryControls().concat(getDiscoverySortControls())
        };

        return JSON.stringify(context);
    }

    function getMatchingOptionValues(input, values) {
        var availableValues = input.find("option").map(function() {
            return $(this).val();
        }).get();

        return values.filter(function(value) {
            return availableValues.indexOf(value) > -1;
        });
    }

    function syncDropdown(input, values) {
        var dropdown = input.closest(".ui.dropdown"),
            selected = input.prop("multiple") ? values : values[0];

        if (!dropdown.length || !dropdown.attr("data-asset-share-processed") ||
                typeof dropdown.dropdown !== "function") {
            return;
        }

        dropdown.dropdown("clear");
        if (values.length) {
            dropdown.dropdown("set selected", selected);
        }
    }

    function applyValuesState(relatedInputs, values) {
        relatedInputs.each(function(index, input) {
            var inputElement = $(input),
                matchingValues;

            if (inputElement.is("select")) {
                matchingValues = getMatchingOptionValues(inputElement, values);
                inputElement.val(inputElement.prop("multiple") ? matchingValues : matchingValues[0] || "");
                syncDropdown(inputElement, matchingValues);
            } else if (inputElement.is(":checkbox,:radio")) {
                inputElement.prop("checked", values.indexOf(inputElement.val()) > -1);
            } else {
                inputElement.val(values.join(getDelimiter(
                    $("[data-asset-share-predicate-id=\"" + inputElement.attr("for") +
                    "\"][form=\"" + getId() + "\"]")
                )));
            }
        });
    }

    function expandDiscoveryControl(relatedInputs) {
        var content = relatedInputs.closest(".content").first(),
            title = content.prev(".title").first();

        if (!content.length || !title.length) {
            return;
        }

        title.addClass("active");
        content.addClass("active").show();
    }

    function applyDiscoveryControlUpdates(updates, expandUpdatedControls) {
        applyingDiscoveryControls = true;
        expandUpdatedControls = expandUpdatedControls !== false;
        try {
            (updates || []).forEach(function(update) {
                var relatedInputs = $(":input[for=\"" + update.id + "\"][form=\"" + getId() + "\"]"),
                    lowerInput,
                    upperInput;

                if (update.id === SORT_ORDERBY_CONTROL_ID || update.id === SORT_DIRECTION_CONTROL_ID) {
                    applyDiscoverySortControlUpdate(update);
                    return;
                }

                if (update.kind === "date-range") {
                    lowerInput = relatedInputs.filter(function() {
                        return /\.lowerBound$/.test($(this).attr("name") || "");
                    }).first();
                    upperInput = relatedInputs.filter(function() {
                        return /\.upperBound$/.test($(this).attr("name") || "");
                    }).first();
                    lowerInput.val(update.state.lowerBound || "");
                    upperInput.val(update.state.upperBound || "");
                } else {
                    applyValuesState(relatedInputs, update.state.values || []);
                }

                if (expandUpdatedControls) {
                    expandDiscoveryControl(relatedInputs);
                }
            });
            reset();
        } finally {
            applyingDiscoveryControls = false;
        }
    }

    function getMatchingSortOption(input, value) {
        return input.closest(".ui.dropdown").find(".item").filter(function() {
            return $(this).attr("data-value") === value;
        }).first();
    }

    function applyDiscoverySortUpdate(sort) {
        var orderByInput,
            directionInput,
            caseInput,
            option;

        if (!sort) {
            return;
        }

        orderByInput = getSortInput("orderby");
        directionInput = getSortInput("orderby.sort");
        if (!orderByInput.length || !directionInput.length) {
            return;
        }

        orderByInput.val(sort.orderby);
        directionInput.val(sort.direction);
        syncDropdown(orderByInput, [sort.orderby]);
        syncDropdown(directionInput, [sort.direction]);

        caseInput = getSortInput("orderby.case");
        if (caseInput.length) {
            option = getMatchingSortOption(orderByInput, sort.orderby);
            caseInput.val(option.length &&
                typeof option.attr("data-asset-share-sort-case-sensitive") !== "undefined" ? "" : "ignore");
        }
    }

    function applyDiscoverySortControlUpdate(update) {
        var values = update.state && update.state.values || [],
            value = values[0],
            sort = {};

        if (!value) {
            return;
        }

        if (update.id === SORT_ORDERBY_CONTROL_ID) {
            sort.orderby = value;
            sort.direction = getSortInput("orderby.sort").val();
        } else {
            sort.orderby = getSortInput("orderby").val();
            sort.direction = value;
        }

        applyDiscoverySortUpdate(sort);
    }

    function clearDiscoveryControls() {
        applyDiscoveryControlUpdates(getDiscoveryControls().map(function(control) {
            return {
                id: control.id,
                kind: control.kind,
                state: control.kind === "date-range" ?
                    {lowerBound: null, upperBound: null} :
                    {values: []}
            };
        }), false);
    }

    function isApplyingDiscoveryControls() {
        return applyingDiscoveryControls;
    }

    function isPathControl(predicateId) {
        var descriptor = predicateId ? descriptorFor(predicateId) : null;

        return descriptor && descriptor.kind === "path";
    }

    function serializeDiscoveryStateFor(residualQuery, event, discoveryCommandFieldNames) {
        var clone;

        reset();
        clone = clean(formData.clone());
        clone = _adjustFormData(clone);

        $("[data-asset-share-search-actions]").each(function () {
            removeAll(clone, $(this).attr("name"));
        });

        (discoveryCommandFieldNames || []).forEach(function(name) {
            removeAll(clone, name);
        });

        removeAll(clone, "fulltext");
        removeAll(clone, "path");

        if (residualQuery && residualQuery.fulltext !== null) {
            clone.set("fulltext", residualQuery.fulltext);
        }
        if (residualQuery && residualQuery.path !== null) {
            clone.set("path", residualQuery.path);
        }

        applyActionFields(clone, event);

        return clone.serialize();
    }

    function serializeJsonFor(event, resetForm, removeKeys) {
        var json = {};

        if (resetForm) {
            reset();
        }

        removeKeys = removeKeys || [];

        buildFormData(formData, event).getAll().forEach(function(field) {
            if (removeKeys.indexOf(field.name) > -1) {
                return;
            }

            if (json[field.name]) {
                if (!Array.isArray(json[field.name])) {
                    json[field.name] = [json[field.name]];
                }
                json[field.name].push(field.value);
            } else {
                json[field.name] = field.value;
            }
        });

        return JSON.stringify(json);
    }

    function _adjustFormData(formDataToAdjust) {
        formDataToAdjust.getAll().forEach(function(field) {
            if (field.name.endsWith("daterange.upperBound") && field.value &&
                    !field.value.endsWith("T23:59:59.999Z")) {
                formDataToAdjust.set(field.name, field.value + "T23:59:59.999Z");
            }
        });

        return formDataToAdjust;
    }

    function _valid() {
        var valid = true,
            visible = true;

        formData.getAll().forEach(function(formEntry) {
           var inputElement = $('[name="' + formEntry.name + '"][form="' + getId() + '"]'),
               inputElementValid,
               inputElementValidationMessage;

           if (inputElement && inputElement[0] && typeof inputElement[0].checkValidity === "function") {
               inputElementValid = inputElement[0].checkValidity();

               if (!inputElementValid) {
                   valid = false;
                   visible = visible && inputElement.is(":visible");

                   inputElementValidationMessage = inputElement.data("asset-share-input-validation-message");
                   if (inputElementValidationMessage) {
                       inputElement[0].setCustomValidity(inputElementValidationMessage);
                   }
               }
           }
        });

        if (!valid && visible) {
            $('<input type="submit">').hide().appendTo(_htmlForm()).click().remove();
        }

        return valid;
    }

    function isValid() {
        reset();
        return _valid();
    }

    function submit(serializationType, resetForm, success, failure) {
        var formToSubmit = serializeFor(serializationType, resetForm);

        if (_valid()) {
            $.when($.get(getUrl(), formToSubmit)).then(success).fail(failure);
            return true;
        }
        return false;
    }

    function submitQuery(query, success) {
        return $.when($.get(getUrl(), query)).then(success);
    }

    function init() {
        url = ns.Data.attr(ns.Elements.element("form"), "action");
        mode = ns.Data.val("mode");

        reset();
    }

    init();

    return {
        url: getUrl,
        serializeFor: serializeFor,
        serializeQueryFor: serializeQueryFor,
        serializeDiscoveryStateFor: serializeDiscoveryStateFor,
        applyDiscoveryControlUpdates: applyDiscoveryControlUpdates,
        applyDiscoverySortUpdate: applyDiscoverySortUpdate,
        clearDiscoveryControls: clearDiscoveryControls,
        isApplyingDiscoveryControls: isApplyingDiscoveryControls,
        isPathControl: isPathControl,
        serializeJsonFor: serializeJsonFor,
        serializeDiscoveryContextFor: serializeDiscoveryContextFor,
        id: getId,
        submit: submit,
        submitQuery: submitQuery,
        isValid: isValid
    };
};
