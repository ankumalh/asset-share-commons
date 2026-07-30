/*
 * Asset Share Commons
 *
 * Copyright [2017] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

/*global es6: true, $: false, AssetShare: false */

AssetShare.Search.Form = function (ns) {
    "use strict";

    var url,
        discoveryUrl,
        formData;

    function getId() {
        return "asset-share-commons__form-id__1";
    }

    function _htmlForm() {
        return $('form[id="' + getId() + '"]');
    }

    function getUrl() {
        return url;
    }

    function isSameOriginPath(value) {
        return typeof value === "string" && value.charAt(0) === "/" && value.charAt(1) !== "/";
    }

    function resolveDiscoveryUrl(formElement) {
        var configured = ns.Data.attr(formElement, "discovery-action"),
            enabled = String(ns.Data.attr(formElement, "discovery-enabled") || "").toLowerCase(),
            contract = String(ns.Data.attr(formElement, "discovery-contract") || ""),
            pageAction;

        if (enabled === "false") {
            return "";
        }
        if (isSameOriginPath(configured)) {
            return configured;
        }
        if (contract === "1") {
            return "";
        }

        // Compatibility for results-component overlays copied before discovery-action existed.
        pageAction = formElement.attr("action") || "";
        pageAction = pageAction.split(/[?#]/)[0];
        if (!isSameOriginPath(pageAction) || !/\.html$/.test(pageAction)) {
            return "";
        }
        return pageAction.replace(/\.html$/, ".discovery.json");
    }

    function isDiscoveryEnabled() {
        return Boolean(discoveryUrl);
    }

    function reset() {
        formData = new ns.FormData(_htmlForm());
    }

    function clean(sourceFormData) {
        var cleanFormData = new ns.FormData();

        sourceFormData.forEach(function(inputName, inputValue) {
            var candidateInputs = $("[name=\"" + inputName + "\"][form=\"" + getId() + "\"]");

            candidateInputs.each(function() {
                var candidateInput = $(this),
                    candidatePredicateId = ns.Data.attr(candidateInput, "predicate-id") || null,
                    candidateInputAdded = false;

                if (candidatePredicateId) {
                    $("[for=\"" + candidatePredicateId + "\"]").each(function(index, relatedInput) {
                        var relativeInputName,
                            relatedInputValue;

                        if (candidateInputAdded) {
                            return;
                        }

                        relativeInputName = $(relatedInput).attr("name");
                        relatedInputValue = sourceFormData.get(relativeInputName);
                        if (relatedInputValue) {
                            if (cleanFormData.getAll(inputName).indexOf(inputValue) === -1) {
                                cleanFormData.add(inputName, inputValue);
                            }
                            candidateInputAdded = true;
                        }
                    });
                } else if (inputValue !== "" &&
                        cleanFormData.getAll(inputName).indexOf(inputValue) === -1) {
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
        $("[data-asset-share-search-actions]").each(function() {
            removeAll(targetFormData, $(this).attr("name"));
        });

        $("[data-asset-share-search-actions*=\"all\"]," +
                "[data-asset-share-search-actions*=\"" + event + "\"]").each(function() {
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

    function serializeDiscoveryStateFor(event, discoveryCommandFieldNames) {
        var clone;

        reset();
        clone = clean(formData.clone());
        clone = _adjustFormData(clone);

        $("[data-asset-share-search-actions]").each(function() {
            removeAll(clone, $(this).attr("name"));
        });

        (discoveryCommandFieldNames || []).forEach(function(name) {
            removeAll(clone, name);
        });

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

            if (inputElement && inputElement[0] &&
                    typeof inputElement[0].checkValidity === "function") {
                inputElementValid = inputElement[0].checkValidity();
                if (!inputElementValid) {
                    valid = false;
                    visible = visible && inputElement.is(":visible");
                    inputElementValidationMessage =
                        inputElement.data("asset-share-input-validation-message");
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

    function submitDiscoveryResolution(prompt, currentState, success) {
        var query = (currentState ? currentState + "&" : "") +
            "prompt=" + encodeURIComponent(prompt);

        if (!discoveryUrl) {
            return $.Deferred().reject().promise();
        }
        return $.when($.post(discoveryUrl, query)).then(success);
    }

    function init() {
        var formElement = ns.Elements.element("form");

        url = ns.Data.attr(formElement, "action");
        discoveryUrl = resolveDiscoveryUrl(formElement);
        reset();
    }

    init();

    return {
        url: getUrl,
        serializeFor: serializeFor,
        serializeQueryFor: serializeQueryFor,
        serializeDiscoveryStateFor: serializeDiscoveryStateFor,
        serializeJsonFor: serializeJsonFor,
        id: getId,
        submit: submit,
        submitQuery: submitQuery,
        submitDiscoveryResolution: submitDiscoveryResolution,
        isDiscoveryEnabled: isDiscoveryEnabled,
        isValid: isValid
    };
};
