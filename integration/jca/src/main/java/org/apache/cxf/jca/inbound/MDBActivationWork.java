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
package org.apache.cxf.jca.inbound;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.resource.spi.UnavailableException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.xml.namespace.QName;

import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.EndpointUtils;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

/**
 *
 * MDBActivationWork is a type of {@link Work} that is executed by 
 * {@link javax.resource.spi.work.WorkManager}.  MDBActivationWork
 * starts an CXF service endpoint to accept inbound calls for
 * the JCA connector.
 * 
 */
public class MDBActivationWork implements Work {
    
    private static final Logger LOG = LogUtils.getL7dLogger(MDBActivationWork.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_SLEEP = 5000;

    private MDBActivationSpec spec;
    private MessageEndpointFactory endpointFactory;
    private boolean released;

    private Map<String, InboundEndpoint> endpoints;

    public MDBActivationWork(MDBActivationSpec spec, 
            MessageEndpointFactory endpointFactory, 
            Map<String, InboundEndpoint> endpoints) {
        this.spec = spec;
        this.endpointFactory = endpointFactory;
        this.endpoints = endpoints;
    }

    public void release() {
        released = true;
    }

    /**
     * Performs the work
     */
    public void run() {
        // get message driven bean proxy
        MessageEndpoint endpoint = getMesssageEndpoint();
        if (endpoint == null) {
            // error has been logged.
            return;
        }
    
        // get class loader
        ClassLoader classLoader = endpoint.getClass().getClassLoader();
        ClassLoader savedClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            activate(endpoint, classLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(savedClassLoader);
        }
    }
    
    /**
     * @param endpoint
     * @param classLoader 
     */
    private void activate(MessageEndpoint endpoint, ClassLoader classLoader) {
        Class<?> serviceClass = null;
        if (spec.getServiceInterfaceClass() != null) {
            try {
                serviceClass = Class.forName(spec.getServiceInterfaceClass(),
                        false, classLoader);
            } catch (ClassNotFoundException e) {
                LOG.severe("Failed to activate service endpoint " 
                        + spec.getDisplayName() 
                        + " due to unable to endpoint listener.");
                return;
            } 
        }
        
        // create server bean factory
        ServerFactoryBean factory = null;
        if (serviceClass != null && EndpointUtils.hasWebServiceAnnotation(serviceClass)) {
            factory = new JaxWsServerFactoryBean();
        } else {
            factory = new ServerFactoryBean();
        }
        
        if (serviceClass != null) {
            factory.setServiceClass(serviceClass);
        }
        
        if (spec.getWsdlLocation() != null) {   
            factory.setWsdlLocation(spec.getWsdlLocation());
        }
        
        if (spec.getAddress() != null) {
            factory.setAddress(spec.getAddress());
        }
        
        if (spec.getBusConfigLocation() != null) {
            factory.setBus(new SpringBusFactory().createBus(classLoader
                    .getResource(spec.getBusConfigLocation())));
        }
        
        if (spec.getEndpointName() != null) {
            factory.setEndpointName(QName.valueOf(spec.getEndpointName()));
        }
        
        if (spec.getSchemaLocations() != null) {
            factory.setSchemaLocations(getListOfString(spec.getSchemaLocations()));
        }
        
        if (spec.getServiceName() != null) {
            factory.setServiceName(QName.valueOf(spec.getServiceName()));
        }
              
        MDBInvoker invoker = createInvoker(endpoint);
        factory.setInvoker(invoker);


        // create and start the server
        factory.setStart(true);
        Server server = factory.create();

        // save the server for clean up later
        endpoints.put(spec.getDisplayName(), new InboundEndpoint(server, invoker));
    }


    /**
     * @param str
     * @return
     */
    private List<String> getListOfString(String str) {
        if (str == null) {
            return null;
        }
        
        return Arrays.asList(str.split(","));
    }

    /**
     * @param endpoint
     * @return
     */
    private MDBInvoker createInvoker(MessageEndpoint endpoint) {
        MDBInvoker answer = null;
        if (spec instanceof DispatchMDBActivationSpec) {
            answer = new DispatchMDBInvoker(endpoint, 
                    ((DispatchMDBActivationSpec)spec).getTargetBeanJndiName());
        } else {
            answer = new MDBInvoker(endpoint);
        }
        return answer;
    }

    /**
     * Invokes endpoint factory to create message endpoint (event driven bean).
     * It will retry if the event driven bean is not yet available.
     */
    private MessageEndpoint getMesssageEndpoint() {
        MessageEndpoint answer = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            
            if (released) {
                LOG.warning("CXF service activation has been stopped.");
                return null;
            }
            
            try {
                answer = endpointFactory.createEndpoint(null);
                break;
            } catch (UnavailableException e) {
                LOG.fine("Target endpoint activation in progress.  Will retry.");
                try {
                    Thread.sleep(RETRY_SLEEP);
                } catch (InterruptedException e1) {
                    // ignore
                }
            }
        }
        
        if (answer == null) {
            LOG.severe("Failed to activate  service endpoint " 
                    + spec.getDisplayName() 
                    + " due to unable to endpoint listener.");
        }
        
        return answer;
    }
}