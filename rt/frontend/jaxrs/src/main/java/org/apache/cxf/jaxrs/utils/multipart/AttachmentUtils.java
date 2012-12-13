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

package org.apache.cxf.jaxrs.utils.multipart;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.attachment.AttachmentDeserializer;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.utils.FormUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.phase.PhaseInterceptorChain;

public final class AttachmentUtils {
    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSUtils.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSUtils.class);
    
    private AttachmentUtils() {
    }
    
    public static MultipartBody getMultipartBody(MessageContext mc) {
        return (MultipartBody)mc.get(MultipartBody.INBOUND_MESSAGE_ATTACHMENTS);
    }
    
    public static Map<String, Attachment> getChildAttachmentsMap(MessageContext mc) {
        return fromListToMap(getChildAttachments(mc));
    }
    
    public static List<Attachment> getChildAttachments(MessageContext mc) {
        return ((MultipartBody)mc.get(MultipartBody.INBOUND_MESSAGE_ATTACHMENTS)).getChildAttachments();
    }
    
    public static Map<String, Attachment> getAttachmentsMap(MessageContext mc) {
        return fromListToMap(getAttachments(mc));
    }
    
    public static List<Attachment> getAttachments(MessageContext mc) {
        return ((MultipartBody)mc.get(MultipartBody.INBOUND_MESSAGE_ATTACHMENTS)).getAllAttachments();
    }
    
    public static MultipartBody getMultipartBody(MessageContext mc,
        String attachmentDir, String attachmentThreshold, String attachmentMaxSize) {
        if (attachmentDir != null) {
            mc.put(AttachmentDeserializer.ATTACHMENT_DIRECTORY, attachmentDir);
        }
        if (attachmentThreshold != null) {
            mc.put(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD, attachmentThreshold);
        }
        if (attachmentMaxSize != null) {
            mc.put(AttachmentDeserializer.ATTACHMENT_MAX_SIZE, attachmentMaxSize);
        }
        
        boolean embeddedAttachment = mc.get("org.apache.cxf.multipart.embedded") != null;
        String propertyName = embeddedAttachment ? MultipartBody.INBOUND_MESSAGE_ATTACHMENTS + ".embedded"
            : MultipartBody.INBOUND_MESSAGE_ATTACHMENTS;
                
        return (MultipartBody)mc.get(propertyName);
    }
    
    public static List<Attachment> getAttachments(MessageContext mc, 
        String attachmentDir, String attachmentThreshold, String attachmentMaxSize) {
        return getMultipartBody(mc, 
                                attachmentDir, 
                                attachmentThreshold, 
                                attachmentMaxSize).getAllAttachments();
    }
    
    public static Attachment getMultipart(Multipart id, 
                                          MediaType mt, 
                                          List<Attachment> infos) throws IOException {
        
        if (id != null) {
            for (Attachment a : infos) {
                if (matchAttachmentId(a, id, mt)) {
                    checkMediaTypes(a.getContentType(), id.type());
                    return a;    
                }
            }
            if (id.required()) {
                org.apache.cxf.common.i18n.Message errorMsg = 
                    new org.apache.cxf.common.i18n.Message("MULTTIPART_ID_NOT_FOUND", 
                                                           BUNDLE, 
                                                           id.value(),
                                                           mt.toString());
                LOG.warning(errorMsg.toString());
                throw new BadRequestException(
                          new MultipartReadException(id.value(), id.type(), errorMsg.toString()));
            } else {
                return null;
            }
        }
        
        return infos.size() > 0 ? infos.get(0) : null; 
    }
    
    public static List<Attachment> getAllMultiparts(Multipart id, 
                                              MediaType mt, 
                                              List<Attachment> infos) throws IOException {
    
        List<Attachment> all = new LinkedList<Attachment>();
        for (Attachment a : infos) {
            if (matchAttachmentId(a, id, mt)) {
                checkMediaTypes(a.getContentType(), id.type());
                all.add(a);    
            }
        }
        return all;
    }
    
    private static boolean matchAttachmentId(Attachment at, Multipart mid, MediaType multipartType) {
        if (at.getContentId().equals(mid.value())) {
            return true;
        }
        ContentDisposition cd = at.getContentDisposition();
        if (cd != null && mid.value().equals(cd.getParameter("name"))) {
            return true;
        }
        return false;
    }

    public static MultivaluedMap<String, String> populateFormMap(MessageContext mc, 
                                                                boolean errorIfMissing) {
        MultivaluedMap<String, String> data = new MetadataMap<String, String>();
        FormUtils.populateMapFromMultipart(data,
                                           AttachmentUtils.getMultipartBody(mc), 
                                           PhaseInterceptorChain.getCurrentMessage(),
                                           true);
        return data;
    }
    
    public static MultivaluedMap<String, String> populateFormMap(MessageContext mc) {
        return populateFormMap(mc, true);
    }
    
    private static Map<String, Attachment> fromListToMap(List<Attachment> atts) {
        Map<String, Attachment> map = new LinkedHashMap<String, Attachment>();
        for (Attachment a : atts) {
            map.put(a.getContentId(), a);    
        }
        return map;
    }
    
    private static void checkMediaTypes(MediaType mt1, String mt2) {
        if (!mt1.isCompatible(MediaType.valueOf(mt2))) {                                            
            throw new NotSupportedException();
        }
    }
}
