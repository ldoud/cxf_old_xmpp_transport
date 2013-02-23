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

package org.apache.cxf.transport.xmpp.chat;

import java.io.IOException;

import org.apache.cxf.message.Message;
import org.apache.cxf.transport.xmpp.messaging.XMPPConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public class ChatConduit extends XMPPConduit {

    public ChatConduit(EndpointReferenceType refType) {
        super(refType);
    }

    @Override
    public void close(Message msg) throws IOException {
        // TODO Use another object to select JID of the service.
        // This other object should allow for any strategy to select the service to call.
        String jidOfService = getTarget().getAddress().getValue();
        getConnectionStrategy().sendChatMessage(msg, jidOfService, getMessageObserver());
    }
}
