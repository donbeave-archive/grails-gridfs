/*
 * Copyright 2012 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo.gridfs;

import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class MongoDatastore extends org.grails.datastore.mapping.mongo.MongoDatastore {

    public MongoDatastore() {
        super();
    }

    public MongoDatastore(MongoMappingContext mappingContext, Map<String, String> connectionDetails,
                          MongoOptions mongoOptions, ConfigurableApplicationContext ctx) {
        super(mappingContext, connectionDetails, mongoOptions, ctx);
    }

    public MongoDatastore(MongoMappingContext mappingContext, Map<String, String> connectionDetails,
                          ConfigurableApplicationContext ctx) {
        super(mappingContext, connectionDetails, ctx);
    }

    public MongoDatastore(MongoMappingContext mappingContext) {
        super(mappingContext);
    }

    public MongoDatastore(MongoMappingContext mappingContext, Mongo mongo, ConfigurableApplicationContext ctx) {
        super(mappingContext, mongo, ctx);
    }

    public MongoDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails,
                          ConfigurableApplicationContext ctx) {
        super(mappingContext, mongo, connectionDetails, ctx);
    }

    @Override
    protected Session createSession(Map<String, String> connDetails) {
        return new MongoSession(this, getMappingContext(), getApplicationEventPublisher());
    }

}