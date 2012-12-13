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

package org.apache.cxf.jaxrs.ext.search;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxrs.ext.search.fiql.FiqlParser;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;

public class SearchContextImpl implements SearchContext {

    public static final String SEARCH_QUERY = "_search";
    public static final String SHORT_SEARCH_QUERY = "_s";
    private static final Logger LOG = LogUtils.getL7dLogger(SearchContextImpl.class);
    private Message message;
    
    public SearchContextImpl(Message message) {
        this.message = message;
    }
    
    public <T> SearchCondition<T> getCondition(Class<T> cls) {
        return getCondition(null, cls);
    }
    
    public <T> SearchCondition<T> getCondition(Class<T> cls, Map<String, String> beanProperties) {
        return getCondition(null, cls, beanProperties);
    }
    
    public <T> SearchCondition<T> getCondition(Class<T> cls, 
                                               Map<String, String> beanProperties,
                                               Map<String, String> parserProperties) {
        return getCondition(null, cls, beanProperties, parserProperties);
    }
    
    public <T> SearchCondition<T> getCondition(String expression, Class<T> cls) {
        return getCondition(expression, cls, null);
    }
    
    public <T> SearchCondition<T> getCondition(String expression, 
                                               Class<T> cls, 
                                               Map<String, String> beanProperties) {
        return getCondition(expression, cls, beanProperties, null);
    }
    
    public <T> SearchCondition<T> getCondition(String expression, 
                                               Class<T> cls, 
                                               Map<String, String> beanProperties,
                                               Map<String, String> parserProperties) {    
        if (InjectionUtils.isPrimitive(cls)) {
            String errorMessage = "Primitive condition types are not supported"; 
            LOG.warning(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        SearchConditionParser<T> parser = getParser(cls, beanProperties, parserProperties);
        
        String theExpression = expression == null 
            ? getSearchExpression() : expression;
        if (theExpression != null) {
            try {
                return parser.parse(theExpression);
            } catch (SearchParseException ex) {
                return null;
            }
        } else {
            return null;
        }
        
    }

    public String getSearchExpression() {
        
        String queryStr = (String)message.get(Message.QUERY_STRING);
        if (queryStr != null 
            && (queryStr.contains(SHORT_SEARCH_QUERY) || queryStr.contains(SEARCH_QUERY))) {
            MultivaluedMap<String, String> params = 
                JAXRSUtils.getStructuredParams(queryStr, "&", true, false);
            if (params.containsKey(SHORT_SEARCH_QUERY)) {
                return params.getFirst(SHORT_SEARCH_QUERY);
            } else {
                return params.getFirst(SEARCH_QUERY);
            }
        } else {
            return null;
        }
    }
    
    private <T> SearchConditionParser<T> getParser(Class<T> cls, 
                                                   Map<String, String> beanProperties,
                                                   Map<String, String> parserProperties) {
        // we can use this method as a parser factory, ex
        // we can get parsers capable of parsing XQuery and other languages
        // depending on the properties set by a user
        Map<String, String> props = null;
        if (parserProperties == null) {
            props = new LinkedHashMap<String, String>(4);
            props.put(SearchUtils.DATE_FORMAT_PROPERTY, 
                      (String)message.getContextualProperty(SearchUtils.DATE_FORMAT_PROPERTY));
            props.put(SearchUtils.TIMEZONE_SUPPORT_PROPERTY, 
                      (String)message.getContextualProperty(SearchUtils.TIMEZONE_SUPPORT_PROPERTY));
            props.put(SearchUtils.LAX_PROPERTY_MATCH, 
                      (String)message.getContextualProperty(SearchUtils.LAX_PROPERTY_MATCH));
        } else {
            props = parserProperties;
        }
        
        Map<String, String> beanProps = null;
            
        if (beanProperties == null) {    
            beanProps = CastUtils.cast((Map<?, ?>)message.getContextualProperty(SearchUtils.BEAN_PROPERTY_MAP));
        } else {
            beanProps = beanProperties;
        }
        
        return new FiqlParser<T>(cls, props, beanProps); 
    }
}
