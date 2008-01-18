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
package org.apache.yoko.bindings.corba;

import java.util.List;

import org.apache.cxf.service.model.ServiceInfo;
import org.apache.schemas.yoko.bindings.corba.TypeMappingType;
import org.apache.yoko.bindings.corba.utils.CorbaUtils;

public final class TypeMapCache {

    private static final String KEY = CorbaTypeMap.class.getName();

    private TypeMapCache() {
        //utility class
    }
    
    public static CorbaTypeMap get(ServiceInfo service) {
        if (service != null) {
            synchronized (service) {
                CorbaTypeMap map = service.getProperty(KEY, CorbaTypeMap.class);
                if (map == null) {
                    List<TypeMappingType> corbaTypes = service.getDescription()
                            .getExtensors(TypeMappingType.class);
                    if (corbaTypes != null) {
                        service.setProperty(KEY, CorbaUtils.createCorbaTypeMap(corbaTypes));
                    }                   
                }
                return map; 
            }
        }
        return null;
    }
}