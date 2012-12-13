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

package org.apache.cxf.attachment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;

import javax.activation.CommandInfo;
import javax.activation.CommandMap;
import javax.activation.DataContentHandler;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.MailcapCommandMap;
import javax.activation.URLDataSource;
import javax.mail.Header;
import javax.mail.internet.InternetHeaders;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;

public final class AttachmentUtil {
    public static final String BODY_ATTACHMENT_ID = "root.message@cxf.apache.org";
    
    private static volatile int counter;
    private static final String ATT_UUID = UUID.randomUUID().toString();
    
    private static final Random BOUND_RANDOM = new Random();
    private static final CommandMap DEFAULT_COMMAND_MAP = CommandMap.getDefaultCommandMap();
    private static final MailcapCommandMap COMMAND_MAP = new EnhancedMailcapCommandMap();
    
    static final class EnhancedMailcapCommandMap extends MailcapCommandMap {
        @Override
        public synchronized DataContentHandler createDataContentHandler(
                String mimeType) {
            DataContentHandler dch = super.createDataContentHandler(mimeType);
            if (dch == null) {
                dch = DEFAULT_COMMAND_MAP.createDataContentHandler(mimeType);
            }
            return dch;
        }

        @Override
        public DataContentHandler createDataContentHandler(String mimeType,
                DataSource ds) {
            DataContentHandler dch = super.createDataContentHandler(mimeType);
            if (dch == null) {
                dch = DEFAULT_COMMAND_MAP.createDataContentHandler(mimeType, ds);
            }
            return dch;
        }

        @Override
        public synchronized CommandInfo[] getAllCommands(String mimeType) {
            CommandInfo[] commands = super.getAllCommands(mimeType);
            CommandInfo[] defaultCommands = DEFAULT_COMMAND_MAP.getAllCommands(mimeType);
            List<CommandInfo> cmdList = new ArrayList<CommandInfo>(Arrays.asList(commands));
            
            // Add CommandInfo which does not exist in current command map.
            for (CommandInfo defCmdInfo : defaultCommands) {
                String defCmdName = defCmdInfo.getCommandName();
                boolean cmdNameExist = false;
                for (CommandInfo cmdInfo : commands) {
                    if (cmdInfo.getCommandName().equals(defCmdName)) {
                        cmdNameExist = true;
                        break;
                    }
                }
                if (!cmdNameExist) {
                    cmdList.add(defCmdInfo);
                }
            }
            
            CommandInfo[] allCommandArray = new CommandInfo[0];
            return cmdList.toArray(allCommandArray);
        }

        @Override
        public synchronized CommandInfo getCommand(String mimeType, String cmdName) {
            CommandInfo cmdInfo = super.getCommand(mimeType, cmdName);
            if (cmdInfo == null) {
                cmdInfo = DEFAULT_COMMAND_MAP.getCommand(mimeType, cmdName);
            }
            return cmdInfo;
        }

        /**
         * Merge current mime types and default mime types.
         */
        @Override
        public synchronized String[] getMimeTypes() {
            String[] mimeTypes = super.getMimeTypes();
            String[] defMimeTypes = DEFAULT_COMMAND_MAP.getMimeTypes();
            Set<String> mimeTypeSet = new HashSet<String>();
            mimeTypeSet.addAll(Arrays.asList(mimeTypes));
            mimeTypeSet.addAll(Arrays.asList(defMimeTypes));
            String[] mimeArray = new String[0];
            return mimeTypeSet.toArray(mimeArray);
        }
    }



    private AttachmentUtil() {

    }
    
    static {
        COMMAND_MAP.addMailcap("image/*;;x-java-content-handler=" 
                               + ImageDataContentHandler.class.getName());
    }

    public static CommandMap getCommandMap() {
        return COMMAND_MAP;
    }
    
    public static boolean isMtomEnabled(Message message) {
        Object prop = message.getContextualProperty(org.apache.cxf.message.Message.MTOM_ENABLED); 
        return MessageUtils.isTrue(prop);
    }
    
    public static void setStreamedAttachmentProperties(Message message, CachedOutputStream bos) 
        throws IOException {
        Object directory = message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_DIRECTORY);
        if (directory != null) {
            if (directory instanceof File) {
                bos.setOutputDir((File)directory);
            } else {
                bos.setOutputDir(new File((String)directory));
            }
        }
        
        Object threshold = message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD);
        if (threshold != null) {
            if (threshold instanceof Long) {
                bos.setThreshold((Long)threshold);
            } else {
                bos.setThreshold(Long.valueOf((String)threshold));
            }
        } else {
            bos.setThreshold(AttachmentDeserializer.THRESHOLD);
        }

        Object maxSize = message.getContextualProperty(AttachmentDeserializer.ATTACHMENT_MAX_SIZE);
        if (maxSize != null) {
            if (maxSize instanceof Long) {
                bos.setMaxSize((Long) maxSize);
            } else {
                bos.setMaxSize(Long.valueOf((String)maxSize));
            }
        }
    }
    
    public static String createContentID(String ns) throws UnsupportedEncodingException {
        // tend to change
        String cid = "cxf.apache.org";
        
        String name = ATT_UUID + "-" + String.valueOf(++counter);
        if (ns != null && (ns.length() > 0)) {
            try {
                URI uri = new URI(ns);
                String host = uri.toURL().getHost();
                cid = host;
            } catch (Exception e) {
                cid = ns;
            }
        }
        return URLEncoder.encode(name, "UTF-8") + "@" + URLEncoder.encode(cid, "UTF-8");
    }

    public static String getUniqueBoundaryValue() {
        //generate a random UUID.
        //we don't need the cryptographically secure random uuid that
        //UUID.randomUUID() will produce.  Thus, use a faster
        //pseudo-random thing
        long leastSigBits = 0;
        long mostSigBits = 0;
        synchronized (BOUND_RANDOM) {
            mostSigBits = BOUND_RANDOM.nextLong();
            leastSigBits = BOUND_RANDOM.nextLong();
        }
        
        mostSigBits &= 0xFFFFFFFFFFFF0FFFL;  //clear version
        mostSigBits |= 0x0000000000004000L;  //set version

        leastSigBits &= 0x3FFFFFFFFFFFFFFFL; //clear the variant
        leastSigBits |= 0x8000000000000000L; //set to IETF variant
        
        UUID result = new UUID(mostSigBits, leastSigBits);
        
        return "uuid:" + result.toString();
    }

    public static String getAttachmentPartHeader(Attachment att) {
        StringBuilder buffer = new StringBuilder(200);
        buffer.append(HttpHeaderHelper.getHeaderKey(HttpHeaderHelper.CONTENT_TYPE) + ": "
                + att.getDataHandler().getContentType() + ";\r\n");
        if (att.isXOP()) {
            buffer.append("Content-Transfer-Encoding: binary\r\n");
        }
        String id = att.getId();
        if (id.charAt(0) == '<') {
            id = id.substring(1, id.length() - 1);
        }
        buffer.append("Content-ID: <" + id + ">\r\n\r\n");
        return buffer.toString();
    }

    public static Map<String, DataHandler> getDHMap(Collection<Attachment> attachments) {
        Map<String, DataHandler> dataHandlers = null;
        if (attachments != null) {
            if (attachments instanceof LazyAttachmentCollection) {
                dataHandlers = ((LazyAttachmentCollection)attachments).createDataHandlerMap();
            } else {
                //preserve the order of iteration
                dataHandlers = new LinkedHashMap<String, DataHandler>();
                for (Attachment attachment : attachments) {
                    dataHandlers.put(attachment.getId(), attachment.getDataHandler());
                }
            }
        }
        return dataHandlers == null ? new LinkedHashMap<String, DataHandler>() : dataHandlers;
    }
    
    public static String cleanContentId(String id) {
        if (id != null) {
            if (id.startsWith("<")) {
                // strip <>
                id = id.substring(1, id.length() - 1);
            }
            // strip cid:
            if (id.startsWith("cid:")) {
                id = id.substring(4);
            }
            // urldecode. Is this bad even without cid:? What does decode do with malformed %-signs, anyhow?
            try {
                id = URLDecoder.decode(id, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                //ignore, keep id as is
            }
        }
        if (id == null) {
            //no Content-ID, set cxf default ID
            id = "root.message@cxf.apache.org";
        }
        return id;
    }
    
    
    public static Attachment createAttachment(InputStream stream, InternetHeaders headers) 
        throws IOException {
     
        String id = cleanContentId(headers.getHeader("Content-ID", null));

        AttachmentImpl att = new AttachmentImpl(id);
        
        final String ct = headers.getHeader("Content-Type", null);
        String cd = headers.getHeader("Content-Disposition", null);
        String fileName = null;
        if (!StringUtils.isEmpty(cd)) {
            StringTokenizer token = new StringTokenizer(cd, ";");
            while (token.hasMoreElements()) {
                fileName = token.nextToken();
                if (fileName.startsWith("name=")) {
                    break;
                }
            }
            if (!StringUtils.isEmpty(fileName)) {
                if (fileName.contains("\"")) {
                    fileName = fileName.substring(fileName.indexOf("\"") + 1, fileName.lastIndexOf("\""));
                } else {
                    fileName = fileName.substring(fileName.indexOf("=") + 1);
                }
            }
        }
        
        boolean quotedPrintable = false;
        
        for (Enumeration<?> e = headers.getAllHeaders(); e.hasMoreElements();) {
            Header header = (Header) e.nextElement();
            if (header.getName().equalsIgnoreCase("Content-Transfer-Encoding")) {
                if (header.getValue().equalsIgnoreCase("binary")) {
                    att.setXOP(true);
                } else if (header.getValue().equalsIgnoreCase("quoted-printable")) {
                    quotedPrintable = true;
                }
            }
            att.setHeader(header.getName(), header.getValue());
        }
        
        if (quotedPrintable) {
            DataSource source = new AttachmentDataSource(ct, 
                                                         new QuotedPrintableDecoderStream(stream));
            if (!StringUtils.isEmpty(fileName)) {
                ((AttachmentDataSource)source).setName(fileName);
            }
            att.setDataHandler(new DataHandler(source));
        } else {
            DataSource source = new AttachmentDataSource(ct, stream);
            if (!StringUtils.isEmpty(fileName)) {
                ((AttachmentDataSource)source).setName(fileName);
            }
            att.setDataHandler(new DataHandler(source));
        }
        
        return att;
    }
    
    public static boolean isTypeSupported(String contentType, List<String> types) {
        if (contentType == null) {
            return false;
        }
        contentType = contentType.toLowerCase();
        for (String s : types) {
            if (contentType.indexOf(s) != -1) {
                return true;
            }
        }
        return false;
    }

    public static Attachment createMtomAttachment(boolean isXop, String mimeType, String elementNS, 
                                                 byte[] data, int offset, int length, int threshold) {
        if (!isXop || length < threshold) {
            return null;
        }        
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        
        ByteDataSource source = new ByteDataSource(data, offset, length);
        source.setContentType(mimeType);
        DataHandler handler = new DataHandler(source);

        String id;
        try {
            id = AttachmentUtil.createContentID(elementNS);
        } catch (UnsupportedEncodingException e) {
            throw new Fault(e);
        }
        AttachmentImpl att = new AttachmentImpl(id, handler);
        att.setXOP(isXop);
        return att;
    }
    
    public static Attachment createMtomAttachmentFromDH(
        boolean isXop, DataHandler handler, String elementNS, int threshold) {
        if (!isXop) {
            return null;
        }        

        // The following is just wrong. Even if the DataHandler has a stream, we should still
        // apply the threshold.
        try {
            DataSource ds = handler.getDataSource();
            if (ds instanceof FileDataSource) {
                FileDataSource fds = (FileDataSource)ds;
                File file = fds.getFile();
                if (file.length() < threshold) {
                    return null;
                }
            } else if (ds.getClass().getName().endsWith("ObjectDataSource")) {
                Object o = handler.getContent();
                if (o instanceof String 
                    && ((String)o).length() < threshold) {
                    return null;
                } else if (o instanceof byte[] && ((byte[])o).length < threshold) {
                    return null;
                }
            }
        } catch (IOException e1) {
        //      ignore, just do the normal attachment thing
        }
        
        String id;
        try {
            id = AttachmentUtil.createContentID(elementNS);
        } catch (UnsupportedEncodingException e) {
            throw new Fault(e);
        }
        AttachmentImpl att = new AttachmentImpl(id, handler);
        if (!StringUtils.isEmpty(handler.getName())) {
            //set Content-Disposition attachment header if filename isn't null
            String file = handler.getName();
            File f = new File(file);
            if (f.exists() && f.isFile()) {
                file = f.getName();
            }
            att.setHeader("Content-Disposition", "attachment;name=\"" + file + "\"");
        }
        att.setXOP(isXop);
        return att;
    }

    public static DataSource getAttachmentDataSource(String contentId, Collection<Attachment> atts) {
        // Is this right? - DD
        if (contentId.startsWith("cid:")) {
            try {
                contentId = URLDecoder.decode(contentId.substring(4), "UTF-8");
            } catch (UnsupportedEncodingException ue) {
                contentId = contentId.substring(4);
            }
            return loadDataSource(contentId, atts);
        } else if (contentId.indexOf("://") == -1) {
            return loadDataSource(contentId, atts);
        } else {
            try {
                return new URLDataSource(new URL(contentId));
            } catch (MalformedURLException e) {
                throw new Fault(e);
            }
        }
        
    }

    private static DataSource loadDataSource(String contentId, Collection<Attachment> atts) {
        return new LazyDataSource(contentId, atts);
    }
    
}
