import com.mongodb.gridfs.GridFS
import grails.plugin.gridfs.GridfsDatastoreFactoryBean
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsGrailsPlugin {

    def version = '0.2-SNAPSHOT'
    def grailsVersion = '2.1.4 > *'
    def observe = ['services', 'domainClass']
    def loadAfter = ['mongodb']

    def title = 'GridFS Plugin'
    def author = 'Alexey Zhokhov'
    def authorEmail = 'donbeave@gmail.com'
    def description = '''\
GridFS plugin for MongoDB.
'''

    def documentation = 'http://grails.org/plugin/gridfs'

    def license = 'APACHE'

    def developers = [[name: 'Alexey Zhokhov', email: 'donbeave@gmail.com']]

    def scm = [url: 'https://github.com/donbeave/grails-gridfs']

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
        def datastore = ctx.mongoDatastore
        def service = ctx.gridfsService

        service.gridfsClasses.clear()

        ctx.mongoMappingContext.persistentEntities.each {
            def collectionName = datastore.getCollectionName(it)

            if (collectionName != null && collectionName.endsWith('.files')) {
                log.debug "Setting GridFS to ${it.javaClass}."

                MongoTemplate template = datastore.getMongoTemplate(it)

                def bucket = collectionName.replace('.files', '')

                service.gridfsClasses.add it.javaClass

                it.javaClass.metaClass.static.getGridfs = {
                    return new GridFS(template.db, bucket)
                }
                it.javaClass.metaClass.getGridfs = {
                    return new GridFS(template.db, bucket)
                }
                it.javaClass.metaClass.setBytes = { byte[] bytes ->
                    service.setBytes(delegate, bytes)
                }
                it.javaClass.metaClass.setFile = { file ->
                    service.setFile(delegate, file)
                }
                it.javaClass.metaClass.setInputStream = { stream ->
                    service.setInputStream(delegate, stream)
                }
                it.javaClass.metaClass.save = {
                    service.save(delegate)
                }
                it.javaClass.metaClass.getDbFile = {
                    return service.getDbFile(delegate)
                }
            }
        }
    }

    def onChange = { event ->
        if (event.ctx) {
            doWithDynamicMethods(event.ctx)
        }
    }

}