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

package org.apache.cxf.transport.xmpp.messaging;

import org.apache.cxf.transport.MessageObserver;

/**
 * An XMPP message listener will implement this interface.
 * The listener receives SOAP messages over XMPP.
 * Then takes these messages and passes them to the MessageObserver.
 */
public interface MessageReceiptStrategy {

    /**
     * Provides the Apache CXF message observer to XMPP message listener.
     * @param observer The XMPP message is passed to MessageObserver.onMessage() on receipt.
     */
    void setMessageObserver(MessageObserver observer);
}
