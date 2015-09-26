/*
 * Copyright 2015 the original author or authors
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
package grails.plugins.gridfs

import com.mongodb.gridfs.GridFS
import grails.plugins.Plugin
import groovy.util.logging.Commons
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
@Commons
class GridfsGrailsPlugin extends Plugin {

    def grailsVersion = '3.0.0 > *'

    def title = 'GridFS'
    def author = 'Alexey Zhokhov'
    def authorEmail = 'donbeave@gmail.com'
    def description = '''\
GridFS plugin for MongoDB.
'''

    def documentation = 'https://github.com/donbeave/grails-gridfs'

    def license = 'APACHE'

    def organization = [name: 'AZ', url: 'http://www.zhokhov.com']

    def developers = [
            [name: 'Alexey Zhokhov', email: 'donbeave@gmail.com']
    ]

    def issueManagement = [system: 'github', url: 'https://github.com/donbeave/grails-gridfs/issues']
    def scm = [url: 'https://github.com/donbeave/grails-gridfs']

    def observe = ['services', 'domainClass']
    def loadAfter = ['mongodb']

    Closure doWithSpring() {
        { ->
            def mongoConfig = grailsApplication.config?.grails?.mongo.clone()

            log.debug 'Overriding MongoDB Datastore bean.'

            mongoDatastore(GridfsDatastoreFactoryBean) {
                mongo = ref('mongoBean')
                mappingContext = ref('mongoMappingContext')
                config = mongoConfig.toProperties()
            }
        }
    }

    void doWithDynamicMethods() {
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
                    return service.save(delegate)
                }
                it.javaClass.metaClass.delete = {
                    service.delete(delegate)
                }
                it.javaClass.metaClass.getDbFile = {
                    return service.getDbFile(delegate)
                }
            }
        }
    }

    void onChange(Map<String, Object> event) {
        if (event.ctx) {
            doWithDynamicMethods(event.ctx)
        }
    }

}