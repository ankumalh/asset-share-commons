/*
 * Asset Share Commons
 *
 * Copyright [2026] Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adobe.aem.commons.assetshare.search.discovery;

import org.osgi.annotation.versioning.ProviderType;

import java.io.IOException;

/**
 * Encapsulates IMS-authenticated calls to the configured discovery agent endpoint.
 *
 * This is the single place that knows how to obtain a bearer token (via the configured
 * {@code AccessTokenProvider}) and issue the HTTP request, consumed by
 * {@code DiscoverySearchProviderImpl} (SearchProvider-based, single round-trip flow).
 */
@ProviderType
public interface DiscoveryAgentClient {

    /**
     * @return true if an agent endpoint is configured and looks like a valid absolute HTTP(S) URL.
     */
    boolean isConfigured();

    /**
     * Calls the configured discovery agent with the given prompt and context payload.
     *
     * IMS/service authentication is resolved internally (a dedicated, short-lived service resource
     * resolver is opened and closed for the duration of the token lookup) -- callers do not need to,
     * and should not, supply their own resource resolver for this.
     *
     * @param prompt the free-text user prompt.
     * @param context the raw (already-serialized) JSON context object to forward to the agent as-is.
     * @return the agent's raw response.
     * @throws IOException if the agent could not be reached, or the response exceeded the configured size limit.
     */
    DiscoveryAgentResponse call(String prompt, String context) throws IOException;
}
