package org.grails.datastore.mapping.mongo.gridfs;

import com.mongodb.Mongo;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.mongo.config.MongoMappingContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

public class MongoDatastore extends com.monochromeroad.grails.plugins.mongo.cia.MongoDatastore {

  public MongoDatastore(MongoMappingContext mappingContext, Mongo mongo,
                        Map<String, String> connectionDetails, ConfigurableApplicationContext ctx) {
    super(mappingContext, mongo, connectionDetails, ctx);
  }

  public MongoDatastore(MongoMappingContext mappingContext, Map<String, String> connectionDetails, ConfigurableApplicationContext ctx) {
    super(mappingContext, connectionDetails, ctx);
  }

  @Override
  protected Session createSession(Map<String, String> connDetails) {
    return new MongoSession(this, getMappingContext(), getApplicationEventPublisher());
  }

}