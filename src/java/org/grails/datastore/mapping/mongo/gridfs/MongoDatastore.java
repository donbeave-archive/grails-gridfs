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

  public MongoDatastore(MongoMappingContext mappingContext,
                        Map<String, String> connectionDetails, MongoOptions mongoOptions, ConfigurableApplicationContext ctx) {
    super(mappingContext, connectionDetails, mongoOptions, ctx);
  }

  public MongoDatastore(MongoMappingContext mappingContext,
                        Map<String, String> connectionDetails, ConfigurableApplicationContext ctx) {
    super(mappingContext, connectionDetails, ctx);
  }

  public MongoDatastore(MongoMappingContext mappingContext) {
    super(mappingContext);
  }

  public MongoDatastore(MongoMappingContext mappingContext, Mongo mongo,
                        ConfigurableApplicationContext ctx) {
    super(mappingContext, mongo, ctx);
  }

  public MongoDatastore(MongoMappingContext mappingContext, Mongo mongo,
                        Map<String, String> connectionDetails, ConfigurableApplicationContext ctx) {
    super(mappingContext, mongo, connectionDetails, ctx);
  }

  @Override
  protected Session createSession(Map<String, String> connDetails) {
    return new MongoSession(this, getMappingContext(), getApplicationEventPublisher());
  }

}
