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

package org.apache.cxf.transport.xmpp.smackx.soap;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

public class SoapProvider implements IQProvider {

    @Override
    public IQ parseIQ(XmlPullParser parser) throws Exception {
        StringBuilder request = new StringBuilder();

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

        SoapPacket packet = new SoapPacket();
        packet.setEnvelope(request.toString());
        return packet;
    }

}
