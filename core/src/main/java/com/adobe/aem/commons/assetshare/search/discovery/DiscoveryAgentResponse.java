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

/**
 * The raw result of a call to the discovery agent. This intentionally carries the agent's
 * response bytes unmodified, alongside HTTP status/content-type metadata, so the server-side
 * resolver can validate the complete response before producing ASC parameters.
 */
@ProviderType
public final class DiscoveryAgentResponse {
    private final int status;
    private final String contentType;
    private final byte[] body;

    public DiscoveryAgentResponse(final int status, final String contentType, final byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body.clone();
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }
}
