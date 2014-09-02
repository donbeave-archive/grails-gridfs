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
