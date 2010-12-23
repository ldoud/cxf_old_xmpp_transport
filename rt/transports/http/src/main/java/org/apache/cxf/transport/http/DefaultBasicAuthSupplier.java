/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.http;

import java.net.URL;

import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.message.Message;

final class DefaultBasicAuthSupplier extends HttpAuthSupplier {
    DefaultBasicAuthSupplier() {
        super();
    }

    @Override
    public String getPreemptiveAuthorization(HTTPConduit conduit, URL currentURL, Message message) {
        AuthorizationPolicy effectiveAuthPolicy = conduit.getEffectiveAuthPolicy(message);
        if (effectiveAuthPolicy.getUserName() != null && effectiveAuthPolicy.getPassword() != null) {
            return HttpBasicAuthSupplier.getBasicAuthHeader(effectiveAuthPolicy.getUserName(), 
                                                            effectiveAuthPolicy.getPassword());
        } else {
            return null;
        }
    }

    @Override
    public String getAuthorizationForRealm(HTTPConduit conduit, URL currentURL, Message message,
                                           String realm, String fullHeader) {
        return getPreemptiveAuthorization(conduit, currentURL, message);
    }


}