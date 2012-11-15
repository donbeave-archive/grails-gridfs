import com.mongodb.BasicDBObject
import com.mongodb.DBCursor
import com.mongodb.DBObject
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.mongo.MongoDatastore

class GridfsGrailsPlugin {
  def version = "0.1"
  def grailsVersion = "1.3.7 > *"
  def observe = ['services', 'domainClass']
  def loadAfter = ['mongodb']

  def dependsOn = [:]
  def pluginExcludes = [
      "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def title = "GridFS Plugin"
  def author = "BeaVe"
  def authorEmail = "donbeave@gmail.com"
  def description = '''\
GridFS plugin for MongoDB.
'''

  def documentation = "http://grails.org/plugin/gridfs"

  def doWithDynamicMethods = { ctx ->
    MongoDatastore datastore = ctx.mongoDatastore
    MappingContext mongoMappingContext = ctx.getBean('mongoMappingContext')

    mongoMappingContext.persistentEntities.each {
      def collectionName = datastore.getCollectionName(it)

      if (collectionName.endsWith('.files')) {
        // TODO GridFS domain
        log.debug "Set GridFS to ${it.javaClass}."
      }
    }

    final oldAsType = DBObject.metaClass.getMetaMethod('asType', [Class] as Class[])

    def asTypeHook = { Class cls ->
      // TODO use custom convertor
      oldAsType.invoke(delegate, cls)
    }

    DBObject.metaClass.asType = asTypeHook
    BasicDBObject.metaClass.asType = asTypeHook
    DBCursor.metaClass.asType = asTypeHook
  }

}