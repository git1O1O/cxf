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

package org.apache.cxf.jaxws;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.ws.Binding;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.Handler;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.jaxb.JAXBDataReaderFactory;
import org.apache.cxf.jaxb.JAXBDataWriterFactory;
import org.apache.cxf.jaxws.context.WebContextResourceResolver;
import org.apache.cxf.jaxws.handler.AnnotationHandlerChainBuilder;
//import org.apache.cxf.jaxws.javaee.HandlerChainType;
import org.apache.cxf.jaxws.support.JaxWsEndpointImpl;
import org.apache.cxf.jaxws.support.JaxWsImplementorInfo;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.jaxws.support.ProviderServiceFactoryBean;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.AbstractServiceFactoryBean;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.wsdl.EndpointReferenceUtils;

public class EndpointImpl extends javax.xml.ws.Endpoint {

    private static final Logger LOG = LogUtils.getL7dLogger(JaxWsServiceFactoryBean.class);
    private static final ResourceBundle BUNDLE = LOG.getResourceBundle();

    protected boolean doInit;

    private Bus bus;
    // private String bindingURI;
    private Object implementor;
    private ServerImpl server;
    private Service service;
    private JaxWsEndpointImpl endpoint;
    private JaxWsImplementorInfo implInfo;

    @SuppressWarnings("unchecked")
    public EndpointImpl(Bus b, Object i, String uri) {
        bus = b;
        implementor = i;
        // bindingURI = uri;
        // build up the Service model
        implInfo = new JaxWsImplementorInfo(implementor.getClass());

        AbstractServiceFactoryBean serviceFactory;
        if (implInfo.isWebServiceProvider()) {
            serviceFactory = new ProviderServiceFactoryBean(implInfo);
        } else {
            serviceFactory = new JaxWsServiceFactoryBean(implInfo);
        }
        serviceFactory.setBus(bus);
        service = serviceFactory.create();
        configureObject(service);

        // create the endpoint       
        QName endpointName = implInfo.getEndpointName();
        EndpointInfo ei = service.getServiceInfo().getEndpoint(endpointName);
        if (ei == null) {
            throw new NullPointerException("Could not find endpoint " + endpointName + " in Service.");
        }

        // revisit: should get enableSchemaValidation from configuration
        if (false) {
            addSchemaValidation();
        }

        if (implInfo.isWebServiceProvider()) {
            service.setInvoker(new ProviderInvoker((Provider<?>)i));
        } else {
            service.setInvoker(new JAXWSMethodInvoker(i));
        }

        //      TODO: use bindigURI     
        try {
            endpoint = new JaxWsEndpointImpl(bus, service, ei);
        } catch (EndpointException e) {
            throw new WebServiceException(e);
        }
        configureObject(endpoint);

        doInit = true;
    }

    public Binding getBinding() {
        return endpoint.getJaxwsBinding();
    }

    public void setExecutor(Executor executor) {
        server.getEndpoint().getService().setExecutor(executor);
    }

    public Executor getExecutor() {
        return server.getEndpoint().getService().getExecutor();
    }

    @Override
    public Object getImplementor() {
        return implementor;
    }

    @Override
    public List<Source> getMetadata() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Object> getProperties() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isPublished() {
        return server != null;
    }

    @Override
    public void publish(Object arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void publish(String address) {
        doPublish(address);
    }

    public void setMetadata(List<Source> arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setProperties(Map<String, Object> arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        if (null != server) {
            server.stop();
        }
    }

    public ServerImpl getServer() {
        return server;
    }

    /**
     * inject resources into servant.  The resources are injected
     * according to @Resource annotations.  See JSR 250 for more
     * information.
     */
    /**
     * @param instance
     */
    protected void injectResources(Object instance) {
        if (instance != null) {
            ResourceManager resourceManager = bus.getExtension(ResourceManager.class);
            resourceManager.addResourceResolver(new WebContextResourceResolver());
            ResourceInjector injector = new ResourceInjector(resourceManager);
            injector.inject(instance);
        }
    }

    protected void doPublish(String address) {
        init();

        if (null != address) {
            endpoint.getEndpointInfo().setAddress(address);
        }

        try {
            MessageObserver observer;
            if (implInfo.isWebServiceProvider()) {
                observer = new ProviderChainObserver(endpoint, bus, implInfo);
            } else {
                observer = new ChainInitiationObserver(endpoint, bus);
            }

            server = new ServerImpl(bus, endpoint, observer);
            server.start();
        } catch (BusException ex) {
            throw new WebServiceException(BUNDLE.getString("FAILED_TO_PUBLISH_ENDPOINT_EXC"), ex);
        } catch (IOException ex) {
            throw new WebServiceException(BUNDLE.getString("FAILED_TO_PUBLISH_ENDPOINT_EXC"), ex);
        }
    }

    org.apache.cxf.endpoint.Endpoint getEndpoint() {
        return endpoint;
    }

    private void configureObject(Object instance) {
        Configurer configurer = bus.getExtension(Configurer.class);
        if (null != configurer) {
            configurer.configureBean(instance);
        }
    }

    private void addSchemaValidation() {
        Schema schema = EndpointReferenceUtils.getSchema(service.getServiceInfo());

        if (service.getDataBinding().getDataReaderFactory() instanceof JAXBDataReaderFactory) {
            ((JAXBDataReaderFactory)service.getDataBinding().getDataReaderFactory()).setSchema(schema);
        }

        if (service.getDataBinding().getDataWriterFactory() instanceof JAXBDataWriterFactory) {
            ((JAXBDataWriterFactory)service.getDataBinding().getDataWriterFactory()).setSchema(schema);
        }
    }

    private synchronized void init() {
        if (doInit) {
            try {
                injectResources(implementor);
                configureHandlers();

            } catch (Exception ex) {
                ex.printStackTrace();
                if (ex instanceof WebServiceException) {
                    throw (WebServiceException)ex;
                }
                throw new WebServiceException("Creation of Endpoint failed", ex);
            }
        }
        doInit = false;
    }

    /**
     * Obtain handler chain from configuration first. If none is specified,
     * default to the chain configured in the code, i.e. in annotations.
     *
     */
    private void configureHandlers() {
        LOG.fine("loading handler chain for endpoint");
        AnnotationHandlerChainBuilder builder = new AnnotationHandlerChainBuilder();

        //TBD: get configuratoin from config file
        //List<Handler> chain = builder.buildHandlerChainFromConfiguration(hc);
        //builder.setHandlerInitEnabled(configuration.getBoolean(ENABLE_HANDLER_INIT));
        List<Handler> chain = null;

        if (null == chain || chain.size() == 0) {
            chain = builder.buildHandlerChainFromClass(implementor.getClass());
        }
        getBinding().setHandlerChain(chain);
    }
}
