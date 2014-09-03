package org.grails.datastore.mapping.mongo.gridfs;

import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import org.grails.datastore.mapping.collection.*;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.core.SessionImplementor;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.EntityPersister;
import org.grails.datastore.mapping.engine.NativeEntryEntityPersister;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.mongo.MongoSession;
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

import javax.persistence.CascadeType;
import javax.persistence.EnumType;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
public class GridfsEntityPersister extends MongoEntityPersister {

    public GridfsEntityPersister(MappingContext mappingContext, PersistentEntity entity, MongoSession mongoSession, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, mongoSession, publisher);
    }

    public GridFSInputFile getNative(Object obj, GridFS gridFS, byte[] data) throws IOException {
        Assert.notNull(obj);
        Assert.notNull(gridFS);

        GridFSInputFile nativeEntry = data == null ? gridFS.createFile() : gridFS.createFile(data);

        // adapted code from NativeEntryEntityPersister.persistEntity()
        SessionImplementor<Object> si = (SessionImplementor<Object>) session;

        ProxyFactory proxyFactory = getProxyFactory();
        // if called internally, obj can potentially be a proxy, which won't work.
        obj = proxyFactory.unwrap(obj);

        PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(obj.getClass().getName());

        final List<PersistentProperty> props = persistentEntity.getPersistentProperties();
        final Map<Association, List<Serializable>> toManyKeys = new HashMap<Association, List<Serializable>>();

        final EntityAccess entityAccess = createEntityAccess(persistentEntity, obj, nativeEntry);
        Object k = readObjectIdentifier(entityAccess, persistentEntity.getMapping());

        for (PersistentProperty prop : props) {
            PropertyMapping<Property> pm = prop.getMapping();
            final Property mappedProperty = pm.getMappedForm();
            boolean ordinalEnum = false;
            String key = null;
            if (mappedProperty != null) {
                key = mappedProperty.getTargetName();
                if (prop.getType().isEnum() && mappedProperty.getEnumTypeObject() == EnumType.ORDINAL) {
                    ordinalEnum = true;
                }
            }
            if (key == null) key = prop.getName();
            if ((prop instanceof Simple)) {
                Object propValue = entityAccess.getProperty(prop.getName());

                if (ordinalEnum && (propValue instanceof Enum)) {
                    propValue = ((Enum) propValue).ordinal();
                }
                setEntryValue(nativeEntry, key, propValue);
            } else if ((prop instanceof Basic)) {
                Basic basic = (Basic) prop;
                CustomTypeMarshaller customTypeMarshaller = basic.getCustomTypeMarshaller();
                if (customTypeMarshaller != null && customTypeMarshaller.supports(getSession().getDatastore())) {
                    Object propValue = entityAccess.getProperty(prop.getName());
                    customTypeMarshaller.write(prop, propValue, nativeEntry);
                } else {
                    Object propValue = entityAccess.getProperty(prop.getName());

                    if (!(key.equals("contentType") && propValue == null) &&
                            !(key.equals("uploadDate") && propValue == null) &&
                            !(key.equals("filename") && propValue == null) &&
                            !(key.equals("md5") && propValue == null)) {
                        setEntryValue(nativeEntry, key, propValue);
                    }
                }
            } else if ((prop instanceof Custom)) {
                CustomTypeMarshaller customTypeMarshaller = ((Custom) prop).getCustomTypeMarshaller();
                if (customTypeMarshaller.supports(getSession().getDatastore())) {
                    Object propValue = entityAccess.getProperty(prop.getName());
                    customTypeMarshaller.write(prop, propValue, nativeEntry);
                }
            } else if (prop instanceof OneToMany) {
                final OneToMany oneToMany = (OneToMany) prop;

                final Object propValue = entityAccess.getProperty(oneToMany.getName());
                if (propValue instanceof Collection) {
                    Collection associatedObjects = (Collection) propValue;
                    if (isInitializedCollection(associatedObjects)) {
                        PersistentEntity associatedEntity = oneToMany.getAssociatedEntity();
                        if (associatedEntity != null) {
                            EntityPersister associationPersister = (EntityPersister) getSession().getPersister(associatedEntity);
                            if (associationPersister != null) {
                                PersistentCollection persistentCollection;
                                boolean newCollection = false;
                                if (associatedObjects instanceof PersistentCollection) {
                                    persistentCollection = (PersistentCollection) associatedObjects;
                                } else {
                                    Class associationType = associatedEntity.getJavaClass();
                                    persistentCollection = getPersistentCollection(associatedObjects, associationType);
                                    entityAccess.setProperty(oneToMany.getName(), persistentCollection);
                                    persistentCollection.markDirty();
                                    newCollection = true;
                                }
                                if (persistentCollection.isDirty()) {
                                    persistentCollection.resetDirty();
                                    List<Serializable> keys = associationPersister.persist(associatedObjects);
                                    toManyKeys.put(oneToMany, keys);
                                    if (newCollection) {
                                        entityAccess.setProperty(oneToMany.getName(), associatedObjects);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (prop instanceof ManyToMany) {
                final ManyToMany manyToMany = (ManyToMany) prop;

                final Object propValue = entityAccess.getProperty(manyToMany.getName());
                if (propValue instanceof Collection) {
                    Collection associatedObjects = (Collection) propValue;
                    if (isInitializedCollection(associatedObjects)) {
                        setManyToMany(persistentEntity, obj, nativeEntry, manyToMany, associatedObjects, toManyKeys);
                    }
                }
            } else if (prop instanceof ToOne) {
                ToOne association = (ToOne) prop;
                if (prop instanceof Embedded) {
                    // For embedded properties simply set the entry value, the underlying implementation
                    // will have to store the embedded entity in an appropriate way (as a sub-document in a document store for example)
                    handleEmbeddedToOne(association, key, entityAccess, nativeEntry);
                } else if (association.doesCascade(CascadeType.PERSIST) && association.getAssociatedEntity() != null) {
                    final Object associatedObject = entityAccess.getProperty(prop.getName());
                    if (associatedObject != null) {
                        Serializable associationId;
                        NativeEntryEntityPersister associationPersister = (NativeEntryEntityPersister) getSession().getPersister(associatedObject);
                        if (proxyFactory.isInitialized(associatedObject) && !getSession().contains(associatedObject)) {
                            Serializable tempId = associationPersister.getObjectIdentifier(associatedObject);
                            if (tempId == null) {
                                if (association.isOwningSide()) {
                                    tempId = getSession().persist(associatedObject);
                                }
                            }
                            associationId = tempId;
                        } else {
                            associationId = associationPersister.getObjectIdentifier(associatedObject);
                        }

                        // handling of hasOne inverse key
                        if (association.isForeignKeyInChild()) {
                            DBObject cachedAssociationEntry = (DBObject) si.getCachedEntry(association.getAssociatedEntity(), associationId);
                            if (cachedAssociationEntry != null) {
                                if (association.isBidirectional()) {
                                    Association inverseSide = association.getInverseSide();
                                    if (inverseSide != null) {
                                        setEntryValue(cachedAssociationEntry, inverseSide.getName(), formulateDatabaseReference(association.getAssociatedEntity(), inverseSide, (Serializable) k));
                                    } else {
                                        setEntryValue(cachedAssociationEntry, key, formulateDatabaseReference(association.getAssociatedEntity(), inverseSide, (Serializable) k));
                                    }
                                }
                            }

                            if (association.doesCascade(CascadeType.PERSIST)) {

                                if (association.isBidirectional()) {
                                    Association inverseSide = association.getInverseSide();
                                    if (inverseSide != null) {
                                        EntityAccess inverseAccess = new EntityAccess(inverseSide.getOwner(), associatedObject);
                                        inverseAccess.setProperty(inverseSide.getName(), obj);
                                    }
                                }
                                associationPersister.persist(associatedObject);
                            }
                        }
                        // handle of standard many-to-one
                        else {
                            if (associationId != null) {
                                setEntryValue(nativeEntry, key, formulateDatabaseReference(persistentEntity, association, associationId));

                                if (association.isBidirectional()) {
                                    Association inverse = association.getInverseSide();
                                    // unwrap the entity in case it is a proxy, since we may need to update the reverse link.
                                    Object inverseEntity = proxyFactory.unwrap(entityAccess.getProperty(association.getName()));
                                    if (inverseEntity != null) {
                                        EntityAccess inverseAccess = createEntityAccess(association.getAssociatedEntity(), inverseEntity);
                                        Object entity = entityAccess.getEntity();
                                        if (inverse instanceof OneToMany) {
                                            Collection existingValues = (Collection) inverseAccess.getProperty(inverse.getName());
                                            if (existingValues == null) {
                                                existingValues = MappingUtils.createConcreteCollection(inverse.getType());
                                                inverseAccess.setProperty(inverse.getName(), existingValues);
                                            }
                                            if (!existingValues.contains(entity))
                                                existingValues.add(entity);
                                        } else if (inverse instanceof ToOne) {
                                            inverseAccess.setProperty(inverse.getName(), entity);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        setEntryValue(nativeEntry, getPropertyKey(prop), null);
                    }
                }
            } else if (prop instanceof EmbeddedCollection) {
                handleEmbeddedToMany(entityAccess, nativeEntry, prop, key);
            }
        }

        return nativeEntry;
    }

    private boolean isInitializedCollection(Collection associatedObjects) {
        return !(associatedObjects instanceof PersistentCollection) || ((PersistentCollection) associatedObjects).isInitialized();
    }

    private AbstractPersistentCollection getPersistentCollection(Collection associatedObjects, Class associationType) {
        if (associatedObjects instanceof Set) {
            return associatedObjects instanceof SortedSet ? new PersistentSortedSet(associationType, getSession(), (SortedSet) associatedObjects) : new PersistentSet(associationType, getSession(), associatedObjects);
        }
        return new PersistentList(associationType, getSession(), (List) associatedObjects);
    }

}
