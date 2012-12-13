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

import org.jivesoftware.smack.packet.IQ;

class NodeNotificationPacket extends IQ  {
    
    public static final String NAMESPACE = "cxf:xmpp:node:notification";
    public static final String ROOT_ELEMENT = "notification";

    private String nodeName;
    private String serviceName;

    /**
     * {@inheritDoc}
     * 
     * @see IQ
     */
    @Override
    public String getXmlns() {
        return NAMESPACE;
    }
    
    public void setNodeName(String node) {
        nodeName = node;
    }
    
    public void setServiceName(String service) {
        serviceName = service;
    }
    
    public String getNodeName() {
        return nodeName;
    }

    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * {@inheritDoc}
     * 
     * @see IQ
     */
    @Override
    public String getChildElementXML() {
        StringBuilder msg = new StringBuilder();
        msg.append("<");
        msg.append(ROOT_ELEMENT);
        msg.append(" xmlns='");
        msg.append(NAMESPACE);
        msg.append("' node='");
        msg.append(nodeName);
        msg.append("' service='");
        msg.append(serviceName);
        msg.append("' />");
        
        return msg.toString();
    }

}
