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

package org.apache.cxf.transport.xmpp.connection;

import org.apache.cxf.service.model.EndpointInfo;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;

/**
 * This factory encapsulates if the XMPP connection is shared, the credentials used to login, and how the
 * resource name is generated.
 */
public interface XMPPConnectionFactory {

    /**
     * Provides a XMPP connection that is already logged in.
     * 
     * @return A connection that might be shared, so don't close it.
     * @throws XMPPException If the login fails.
     */
    XMPPConnection login(EndpointInfo epi) throws XMPPException;
}
