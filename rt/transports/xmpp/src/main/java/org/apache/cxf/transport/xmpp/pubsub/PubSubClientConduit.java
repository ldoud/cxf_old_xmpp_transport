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

package org.apache.cxf.transport.xmpp.pubsub;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.xmpp.common.AbstractConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.SimplePayload;

public class PubSubClientConduit extends AbstractConduit {

    private LeafNode targetNode;

    public PubSubClientConduit(EndpointReferenceType type) {
        super(type);
    }

    public void setNode(LeafNode node) {
        targetNode = node;
    }

    @Override
    public void prepare(Message msg) throws IOException {
        super.prepare(msg);
        msg.getExchange().setOneWay(true);
    }

    @Override
    public void close(Message msg) throws IOException {
        CachedOutputStream output = (CachedOutputStream)msg.getContent(OutputStream.class);
        if (targetNode != null && output != null) {
            StringBuilder soapEnvelope = new StringBuilder();
            output.writeCacheTo(soapEnvelope);

            SimplePayload payload = new SimplePayload("Envelope", "http://www.w3.org/2003/05/soap-envelope",
                                                      soapEnvelope.toString());

            PayloadItem<SimplePayload> pi = new PayloadItem<SimplePayload>(payload);
            try {
                targetNode.send(pi);
            } catch (XMPPException e) {
                throw new IOException(e);
            }
        }
    }
}
