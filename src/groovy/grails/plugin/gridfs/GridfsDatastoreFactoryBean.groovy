/*
 * Copyright 2014 the original author or authors
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
package grails.plugin.gridfs

import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.grails.datastore.gorm.mongo.bean.factory.MongoDatastoreFactoryBean
import org.grails.datastore.mapping.mongo.gridfs.MongoDatastore

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsDatastoreFactoryBean extends MongoDatastoreFactoryBean {

    @Override
    MongoDatastore getObject() {
        MongoDatastore datastore
        if (mongo) {
            datastore = new MongoDatastore(mappingContext, mongo, config, applicationContext)
        } else {
            datastore = new MongoDatastore(mappingContext, config, applicationContext)
        }

        applicationContext.addApplicationListener new DomainEventListener(datastore)
        applicationContext.addApplicationListener new AutoTimestampEventListener(datastore)

        datastore.afterPropertiesSet()
        datastore
    }

}
