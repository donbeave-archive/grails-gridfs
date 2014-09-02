package org.grails.datastore.mapping.mongo.gridfs;

import com.mongodb.gridfs.GridFSFile;
import org.grails.datastore.mapping.engine.Persister;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.springframework.context.ApplicationEventPublisher;

import java.io.Serializable;
import java.util.Map;

public class MongoSession extends org.grails.datastore.mapping.mongo.MongoSession {

    public MongoSession(org.grails.datastore.mapping.mongo.MongoDatastore datastore, MappingContext mappingContext,
                        ApplicationEventPublisher publisher) {
        super(datastore, mappingContext, publisher);
    }

    public MongoSession(org.grails.datastore.mapping.mongo.MongoDatastore datastore, MappingContext mappingContext,
                        ApplicationEventPublisher publisher, boolean stateless) {
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

}
