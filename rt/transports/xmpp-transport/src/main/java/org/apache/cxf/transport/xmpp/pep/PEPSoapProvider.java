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

package org.apache.cxf.transport.xmpp.pep;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smackx.packet.PEPSoapPacket;
import org.xmlpull.v1.XmlPullParser;

public class PEPSoapProvider implements PacketExtensionProvider {
    

    @Override
    public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
        StringBuilder request = new StringBuilder();

        String nodeName = "";
        boolean startOfSoapMsg = false;
        while (!startOfSoapMsg) {
            if ("publish".equals(parser.getName()) && 
                "http://jabber.org/protocol/pubsub".equals(parser.getNamespace())){
                nodeName = parser.getAttributeValue(1);
            }
            else if ("Envelope".equals(parser.getName()) && parser.getEventType() == XmlPullParser.START_TAG &&
                ("http://www.w3.org/2003/05/soap-envelope".equals(parser.getNamespace()) ||
                    "http://schemas.xmlsoap.org/soap/envelope/".equals(parser.getNamespace()))) {
                startOfSoapMsg = true;
                
                // Append the current text.
                request.append(parser.getText());
            }
            else {
                parser.next();
            }
        }
        
        boolean endOfSoapMsg = false;
        while (!endOfSoapMsg) {
            // Append the current text.
            request.append(parser.getText());

            // If </Envelope> then stop parsing.
            if ("Envelope".equals(parser.getName())
                && "http://www.w3.org/2003/05/soap-envelope".equals(parser.getNamespace())
                && parser.getEventType() == XmlPullParser.END_TAG) {
                endOfSoapMsg = true;
            } else if ("Envelope".equals(parser.getName())
                       && "http://schemas.xmlsoap.org/soap/envelope/".equals(parser.getNamespace())
                       && parser.getEventType() == XmlPullParser.END_TAG) {
                endOfSoapMsg = true;
            } else { // Otherwise keep parsing.
                parser.next();
            }
        }
        
        PEPSoapPacket soapPEP = new PEPSoapPacket(nodeName);
        soapPEP.setItemDetailsXML(request.toString());
        return soapPEP;
    }

}
