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

package org.apache.cxf.jaxrs.provider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.ConsumeMime;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.JAXBContextProvider;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProviderFactoryTest extends Assert {

    
    @Before
    public void setUp() {
        ProviderFactory.getInstance().clearProviders();
    }
    
    @Test
    public void testSortEntityProviders() throws Exception {
        ProviderFactory pf = ProviderFactory.getInstance();
        pf.registerUserProvider(new TestStringProvider());
        pf.registerUserProvider(new StringProvider());
        
        List<ProviderInfo<MessageBodyReader>> readers = pf.getUserMessageReaders();

        assertTrue(indexOf(readers, TestStringProvider.class) 
                   < indexOf(readers, StringProvider.class));
        
        List<ProviderInfo<MessageBodyWriter>> writers = pf.getUserMessageWriters();

        assertTrue(indexOf(writers, TestStringProvider.class) 
                   < indexOf(writers, StringProvider.class));
        
        //REVISIT the compare algorithm
        //assertTrue(indexOf(providers, JSONProvider.class) < indexOf(providers, TestStringProvider.class));
    }
    
    @Test
    public void testGetStringProvider() throws Exception {
        verifyProvider(String.class, StringProvider.class, "text/html");
    }
    
    @Test
    public void testGetBinaryProvider() throws Exception {
        verifyProvider(byte[].class, BinaryDataProvider.class, "*/*");
        verifyProvider(InputStream.class, BinaryDataProvider.class, "image/png");
        MessageBodyWriter writer = ProviderFactory.getInstance()
            .createMessageBodyWriter(File.class, null, null, JAXRSUtils.ALL_TYPES, null);
        assertTrue(BinaryDataProvider.class == writer.getClass());
    }
    
    private void verifyProvider(Class<?> type, Class<?> provider, String mediaType,
                                String errorMessage) 
        throws Exception {
        
        MediaType mType = MediaType.valueOf(mediaType);
        
        MessageBodyReader reader = ProviderFactory.getInstance()
            .createMessageBodyReader(type, null, null, mType, null);
        assertSame(errorMessage, provider, reader.getClass());
    
        MessageBodyWriter writer = ProviderFactory.getInstance()
            .createMessageBodyWriter(type, null, null, mType, null);
        assertTrue(errorMessage, provider == writer.getClass());
    }
    
    
    private void verifyProvider(Class<?> type, Class<?> provider, String mediaType) 
        throws Exception {
        verifyProvider(type, provider, mediaType, "Unexpected provider found");
        
    }
       
    @Test
    public void testGetStringProviderWildCard() throws Exception {
        verifyProvider(String.class, StringProvider.class, "text/*");
    }
    
    @Test
    public void testGetAtomProvider() throws Exception {
        ProviderFactory.getInstance().setUserProviders(
             Arrays.asList(
                  new Object[]{new AtomEntryProvider(), new AtomFeedProvider()}));
        verifyProvider(Entry.class, AtomEntryProvider.class, "application/atom+xml");
        verifyProvider(Feed.class, AtomFeedProvider.class, "application/atom+xml");
    }
    
    @Test
    public void testGetStringProviderUsingProviderDeclaration() throws Exception {
        ProviderFactory pf = ProviderFactory.getInstance();
        pf.registerUserProvider(new TestStringProvider());
        verifyProvider(String.class, TestStringProvider.class, "text/html");
    }    
    
    @Test
    public void testGetJSONProviderConsumeMime() throws Exception {
        verifyProvider(org.apache.cxf.jaxrs.resources.Book.class, JSONProvider.class, 
                       "application/json");
    }
    
    @Test
    public void testRegisterCustomJSONEntityProvider() throws Exception {
        ProviderFactory pf = ProviderFactory.getInstance();
        pf.registerUserProvider(new CustomJSONProvider());
        verifyProvider(org.apache.cxf.jaxrs.resources.Book.class, CustomJSONProvider.class, 
                       "application/json", "User-registered provider was not returned first");
    }
    
    
    @Test
    public void testRegisterCustomResolver() throws Exception {
        ProviderFactory pf = ProviderFactory.getInstance();
        pf.registerUserProvider(new JAXBContextProvider());
        ContextResolver<JAXBContext> cr = pf.createContextResolver(JAXBContext.class, null);
        assertTrue("JAXBContext ContextProvider can not be found", 
                   cr instanceof JAXBContextProvider);
        
    }
    
    @Test
    public void testRegisterCustomEntityProvider() throws Exception {
        ProviderFactory pf = (ProviderFactory)ProviderFactory.getInstance();
        pf.registerUserProvider(new CustomWidgetProvider());
        
        verifyProvider(org.apache.cxf.jaxrs.resources.Book.class, CustomWidgetProvider.class, 
                       "application/widget", "User-registered provider was not returned first");
    }
    
    private int indexOf(List<? extends Object> providerInfos, Class providerType) {
        int index = 0;
        for (Object pi : providerInfos) {
            Object p = ((ProviderInfo)pi).getProvider();
            if (p.getClass().isAssignableFrom(providerType)) {
                break;
            }
            index++;
        }
        return index;
    }
    
    @ConsumeMime("text/html")
    @ProduceMime("text/html")
    private final class TestStringProvider 
        implements MessageBodyReader<String>, MessageBodyWriter<String>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type == String.class;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type == String.class;
        }
        
        public long getSize(String s) {
            return s.length();
        }

        public String readFrom(Class<String> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {
            try {
                return IOUtils.toString(is);
            } catch (IOException e) {
                // TODO: better exception handling
            }
            return null;
        }

        public void writeTo(String obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            try {
                os.write(obj.getBytes());
            } catch (IOException e) {
                // TODO: better exception handling
            }
        }

    }
    
    @ConsumeMime("application/json")
    @ProduceMime("application/json")
    private final class CustomJSONProvider 
        implements MessageBodyReader<String>, MessageBodyWriter<String>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public long getSize(String s) {
            return s.length();
        }

        public String readFrom(Class<String> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {    
            //Dummy
            return null;
        }

        public void writeTo(String obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            //Dummy
        }

    }
    
    @ConsumeMime("application/widget")
    @ProduceMime("application/widget")
    private final class CustomWidgetProvider
        implements MessageBodyReader<String>, MessageBodyWriter<String>  {

        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations) {
            return type.getAnnotation(XmlRootElement.class) != null;
        }
        
        public long getSize(String s) {
            return s.length();
        }


        public String readFrom(Class<String> clazz, Type genericType, Annotation[] annotations, 
                               MediaType m, MultivaluedMap<String, String> headers, InputStream is) {    
            //Dummy
            return null;
        }

        public void writeTo(String obj, Class<?> clazz, Type genericType, Annotation[] annotations,  
            MediaType m, MultivaluedMap<String, Object> headers, OutputStream os) {
            //Dummy
        }

    }
    
}