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

/**
 * @author <a href='mailto:alexey@zhokhov.com'>Alexey Zhokhov</a>
 */
public class MongoSession {

    /*
    public MongoSession(org.grails.datastore.mapping.mongo.MongoDatastore datastore, MappingContext mappingContext,
                        ApplicationEventPublisher publisher) {
        super(datastore, mappingContext, publisher);
    }

    public MongoSession(MongoDatastore datastore, MappingContext mappingContext, ApplicationEventPublisher publisher,
                        boolean stateless) {
        super(datastore, mappingContext, publisher, stateless);
    }

    @Override
    protected void cacheEntry(Serializable key, Object entry, Map<Serializable, Object> entryCache, boolean forDirtyCheck) {
        if (forDirtyCheck && entry instanceof GridFSFile) {
            // skip to cache entry
        } else {
            super.cacheEntry(key, entry, entryCache, forDirtyCheck);
        }
    }

    @Override
    protected Persister createPersister(@SuppressWarnings("rawtypes") Class cls, MappingContext mappingContext) {
        final PersistentEntity entity = mappingContext.getPersistentEntity(cls.getName());
        return entity == null ? null : new GridfsEntityPersister(mappingContext, entity, this, publisher);
    }
    */

}
