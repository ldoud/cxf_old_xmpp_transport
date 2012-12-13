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
package org.apache.cxf.interceptor;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.io.CachedWriter;
import org.apache.cxf.io.DelegatingInputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;



/**
 * A simple logging handler which outputs the bytes of the message to the
 * Logger.
 */
@NoJSR250Annotations
public class LoggingInInterceptor extends AbstractLoggingInterceptor {
    private static final Logger LOG = LogUtils.getLogger(LoggingInInterceptor.class);
    
    public LoggingInInterceptor() {
        super(Phase.RECEIVE);
    }
    
    public LoggingInInterceptor(String phase) {
        super(phase);
    }

    public LoggingInInterceptor(String id, String phase) {
        super(id, phase);
    }

    public LoggingInInterceptor(int lim) {
        this();
        limit = lim;
    }
    public LoggingInInterceptor(String id, int lim) {
        this(id, Phase.RECEIVE);
        limit = lim;
    }

    public LoggingInInterceptor(PrintWriter w) {
        this();
        this.writer = w;
    }
    public LoggingInInterceptor(String id, PrintWriter w) {
        this(id, Phase.RECEIVE);
        this.writer = w;
    }
    
    public void handleMessage(Message message) throws Fault {
        Logger logger = getMessageLogger(message);
        if (writer != null || logger.isLoggable(Level.INFO)) {
            logging(logger, message);
        }
    }

    protected void logging(Logger logger, Message message) throws Fault {
        if (message.containsKey(LoggingMessage.ID_KEY)) {
            return;
        }
        String id = (String)message.getExchange().get(LoggingMessage.ID_KEY);
        if (id == null) {
            id = LoggingMessage.nextId();
            message.getExchange().put(LoggingMessage.ID_KEY, id);
        }
        message.put(LoggingMessage.ID_KEY, id);
        final LoggingMessage buffer 
            = new LoggingMessage("Inbound Message\n----------------------------", id);

        Integer responseCode = (Integer)message.get(Message.RESPONSE_CODE);
        if (responseCode != null) {
            buffer.getResponseCode().append(responseCode);
        }

        String encoding = (String)message.get(Message.ENCODING);

        if (encoding != null) {
            buffer.getEncoding().append(encoding);
        }
        String httpMethod = (String)message.get(Message.HTTP_REQUEST_METHOD);
        if (httpMethod != null) {
            buffer.getHttpMethod().append(httpMethod);
        }
        String ct = (String)message.get(Message.CONTENT_TYPE);
        if (ct != null) {
            buffer.getContentType().append(ct);
        }
        Object headers = message.get(Message.PROTOCOL_HEADERS);

        if (headers != null) {
            buffer.getHeader().append(headers);
        }
        String uri = (String)message.get(Message.REQUEST_URL);
        if (uri != null) {
            buffer.getAddress().append(uri);
            String query = (String)message.get(Message.QUERY_STRING);
            if (query != null) {
                buffer.getAddress().append("?").append(query);
            }
        }
        
        if (!isShowBinaryContent() && isBinaryContent(ct)) {
            buffer.getMessage().append(BINARY_CONTENT_MESSAGE).append('\n');
            log(logger, buffer.toString());
            return;
        }
        
        InputStream is = message.getContent(InputStream.class);
        if (is != null) {
            CachedOutputStream bos = new CachedOutputStream();
            if (threshold > 0) {
                bos.setThreshold(threshold);
            }
            try {
                // use the appropriate input stream and restore it later
                InputStream bis = is instanceof DelegatingInputStream 
                    ? ((DelegatingInputStream)is).getInputStream() : is;
                
                IOUtils.copyAndCloseInput(bis, bos);
                bos.flush();
                bis = bos.getInputStream();
                
                // restore the delegating input stream or the input stream
                if (is instanceof DelegatingInputStream) {
                    ((DelegatingInputStream)is).setInputStream(bis);
                } else {
                    message.setContent(InputStream.class, bis);
                }

                if (bos.getTempFile() != null) {
                    //large thing on disk...
                    buffer.getMessage().append("\nMessage (saved to tmp file):\n");
                    buffer.getMessage().append("Filename: " + bos.getTempFile().getAbsolutePath() + "\n");
                }
                if (bos.size() > limit) {
                    buffer.getMessage().append("(message truncated to " + limit + " bytes)\n");
                }
                writePayload(buffer.getPayload(), bos, encoding, ct); 
                    
                bos.close();
            } catch (Exception e) {
                throw new Fault(e);
            }
        } else {
            Reader reader = message.getContent(Reader.class);
            if (reader != null) {
                try {
                    CachedWriter writer = new CachedWriter();
                    IOUtils.copyAndCloseInput(reader, writer);
                    message.setContent(Reader.class, writer.getReader());
                    
                    if (writer.getTempFile() != null) {
                        //large thing on disk...
                        buffer.getMessage().append("\nMessage (saved to tmp file):\n");
                        buffer.getMessage().append("Filename: " + writer.getTempFile().getAbsolutePath() + "\n");
                    }
                    if (writer.size() > limit) {
                        buffer.getMessage().append("(message truncated to " + limit + " bytes)\n");
                    }
                    writer.writeCacheTo(buffer.getPayload(), limit);
                } catch (Exception e) {
                    throw new Fault(e);
                }
            }
        }
        log(logger, formatLoggingMessage(buffer));
    }


    protected String formatLoggingMessage(LoggingMessage loggingMessage) {

        return loggingMessage.toString();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
