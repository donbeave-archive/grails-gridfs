import com.mongodb.DBRef
import com.mongodb.gridfs.GridFS
import org.apache.commons.io.IOUtils
import org.bson.types.ObjectId
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.data.mongodb.core.MongoTemplate

class GridfsGrailsPlugin {
  def version = '0.2-SNAPSHOT'
  def grailsVersion = "1.3.7 > *"
  def observe = ['services', 'domainClass']
  def loadAfter = ['mongodb']

  def dependsOn = [mongodb: '1.3.0 > *']
  def pluginExcludes = [
      'grails-app/views/error.gsp'
  ]

  // TODO Fill in these fields
  def title = 'GridFS Plugin'
  def author = 'BeaVe'
  def authorEmail = 'donbeave@gmail.com'
  def description = '''\
GridFS plugin for MongoDB.
'''

  def documentation = 'http://grails.org/plugin/gridfs'

  private List gridfsClasses = []
  private Map gridfsCollections = [:]

  def doWithSpring = {
    def mongoConfig = application.config?.grails?.mongo.clone()

    log.debug 'Overriding MongoDB Datastore bean.'

    mongoDatastore(GridfsDatastoreFactoryBean) {
      mongo = ref('mongoBean')
      mappingContext = ref('mongoMappingContext')
      config = mongoConfig.toProperties()
    }
  }

  def doWithDynamicMethods = { ctx ->
    // from MongoFile Plugin
    for (domainClass in application.domainClasses) {
      domainClass.metaClass.mongoFileExists = { String fieldName = '' ->
        getMongoFile(fieldName) != null
      }

      domainClass.metaClass.getMongoFile = { String fieldName = '' ->
        def mongoFileService = ctx.getBean("mongoFileService")
        if (mongoFileService) {
          return mongoFileService.getFile(delegate.getClass(), id, fieldName)
        }

        null
      }

      domainClass.metaClass.saveMongoFile = { org.springframework.web.multipart.MultipartFile file, String fieldName = '' ->
        def mongoFileService = ctx.getBean("mongoFileService")
        if (mongoFileService) {
          mongoFileService.saveFile(file, delegate.getClass(), id, fieldName)
        }
      }

      domainClass.metaClass.saveMongoFile = { byte[] fileContents, String fileName, String fieldName = '' ->
        def mongoFileService = ctx.getBean("mongoFileService")
        if (mongoFileService) {
          mongoFileService.saveFile(fileContents, fileName, delegate.getClass(), id, fieldName)
        }
      }

      domainClass.metaClass.saveMongoFile = { InputStream inputStream, String fileName, String fieldName = '' ->
        def mongoFileService = ctx.getBean("mongoFileService")
        if (mongoFileService) {
          mongoFileService.saveFile(inputStream, fileName, delegate.getClass(), id, fieldName)
        }
      }

      domainClass.metaClass.deleteMongoFile = { String fieldName = '' ->
        def mongoFileService = ctx.getBean("mongoFileService")
        if (mongoFileService) {
          mongoFileService.deleteFile(delegate.getClass(), id, fieldName)
        }
      }
    }



    MongoDatastore datastore = ctx.mongoDatastore
    MappingContext mongoMappingContext = ctx.getBean('mongoMappingContext')

    def saveHook = {
      delegate.validate()

      def gridfs = gridfsCollections.get(delegate.class)
      PersistentEntity entity = mongoMappingContext.getPersistentEntity(delegate.class.name)

      // TODO (ID convertor)
      def id = null

      if (delegate.id) {
        id = delegate.id instanceof ObjectId ? delegate.id : new ObjectId(delegate.id)
      }

      // TODO optimize queries
      def file = id ? gridfs.findOne(id) : null

      def needRemove = false

      if (id && delegate.bytes != file.bytes) {
        file = null
        needRemove = true
      }

      // firing events (if need)
      if (needRemove) {
        if (delegate.metaClass.respondsTo(delegate, 'beforeUpdate')) {
          delegate.beforeUpdate()
        }
      } else {
        if (delegate.metaClass.respondsTo(delegate, 'beforeInsert')) {
          delegate.beforeInsert()
        }
      }

      // re-creating file if need
      file = file ?: gridfs.createFile(delegate.bytes)

      // TODO use spring API to convert domain to mongo type
      entity.persistentProperties.each { prop ->
        if (!prop.name.equals('bytes') && !prop.name.equals('uploadDate') && !prop.name.equals('version')) {
          if (prop instanceof Association) {
            MongoTemplate template = datastore.getMongoTemplate(prop.associatedEntity)

            def collectionName = datastore.getCollectionName(prop.associatedEntity)

            file.put(prop.name, new DBRef(template.db, collectionName, delegate."${prop.name}".id))
          } else {
            file.put(prop.name, delegate."${prop.name}")
          }
        }
      }

      // setting id to file (if neeed)
      id ? file.setId(id) : null

      if (needRemove) {
        log.debug "removing existing object from DB."
        gridfs.remove(id)
      }

      file.save()

      delegate.id = file.id
      delegate.uploadDate = file.uploadDate

      // firing events (if need)
      if (needRemove) {
        if (delegate.metaClass.respondsTo(delegate, 'afterUpdate')) {
          delegate.afterUpdate()
        }
      } else {
        if (delegate.metaClass.respondsTo(delegate, 'afterInsert')) {
          delegate.afterInsert()
        }
      }

      return delegate
    }

    def getBytesHook = {
      def bytes = delegate.@bytes

      if (!bytes && delegate.id) {
        def gridfs = gridfsCollections.get(delegate.class)

        // TODO (ID convertor)
        def dbFile = gridfs.findOne(delegate.id instanceof ObjectId ? delegate.id : new ObjectId(delegate.id))
        bytes = IOUtils.toByteArray(dbFile.inputStream)
      }

      return bytes
    }

    mongoMappingContext.persistentEntities.each {
      def collectionName = datastore.getCollectionName(it)

      if (collectionName != null && collectionName.endsWith('.files')) {
        log.debug "Setting GridFS to ${it.javaClass}."

        MongoTemplate template = datastore.getMongoTemplate(it)

        def bucket = collectionName.replace('.files', '')
        GridFS gridfs = new GridFS(template.db, bucket)

        gridfsClasses.add it.javaClass
        gridfsCollections.put it.javaClass, gridfs

        it.javaClass.metaClass.save = saveHook
        it.javaClass.metaClass.getBytes = getBytesHook
      }
    }
  }

}