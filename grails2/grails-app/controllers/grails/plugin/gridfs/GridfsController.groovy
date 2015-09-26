/*
 * Copyright 2014 the original author or authors
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
package grails.plugin.gridfs

import grails.converters.JSON
import grails.converters.XML
import org.springframework.util.Assert

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsController {

    def gridfsService

    def exists(FileCommand fileCommand) {
        def result = [exist: false]
        try {
            def file = Class.forName(fileCommand.className).get(fileCommand.id)
            def dbFile = file?.dbFile

            result.exist = file != null

            if (dbFile) {
                result.filename = dbFile.filename
                result.contentType = dbFile.contentType
                result.length = dbFile.length
            }
        } catch (ClassNotFoundException ignore) {
        }
        if (params.format && params.format.toLowerCase().equals('xml')) {
            render(result as XML)
        } else {
            render(result as JSON)
        }
    }

    def deliver(FileCommand fileCommand) {
        Assert.notNull(fileCommand.className, 'The className must not be null')
        Assert.notNull(fileCommand.id, 'The id must not be null')

        def file = Class.forName(fileCommand.className).get(fileCommand.id)

        gridfsService.deliverFile(response, file, null, fileCommand.attachment)
    }

}

class FileCommand {
    String id
    String className
    boolean attachment = true
}