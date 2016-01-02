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
package grails.plugins.gridfs

import com.mongodb.gridfs.GridFS
import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.gridfs.GridFSFile
import com.mongodb.gridfs.GridFSInputFile
import org.apache.commons.fileupload.FileItem
import org.apache.commons.io.IOUtils
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeType
import org.bson.*
import org.bson.codecs.EncoderContext
import org.grails.datastore.mapping.document.config.DocumentPersistentEntity
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec
import org.grails.web.util.WebUtils
import org.springframework.util.Assert
import org.springframework.web.multipart.MultipartFile

import javax.servlet.http.HttpServletResponse

/**
 * @author <a href='mailto:alexey@zhokhov.com'>Alexey Zhokhov</a>
 */
class GridfsService {

    MongoDatastore mongoDatastore

    List<Class> gridfsClasses = []

    /**
     * @param obj
     */
    def save(obj) {
        Assert.notNull(obj, 'The obj must not be null')

        if (obj.validate()) {
            if (!obj.metaClass.hasProperty(obj, fileObjProperty)) {
                throw new RuntimeException('Can not save object, file not initialized')
            }

            GridFS gridFS = (GridFS) obj.gridfs
            PersistentEntityCodec codec = mongoDatastore.getPersistentEntityCodec(obj.class)
            DocumentPersistentEntity entity = (DocumentPersistentEntity) codec.entity

            BsonDocument bsonDoc = new BsonDocument()

            def data = obj.metaClass.getProperty(obj, fileObjProperty)

            byte[] bytes = null
            String filename = null

            if (data instanceof ByteArrayInputStream) {
                bytes = data.bytes
            } else if (data instanceof MultipartFile) {
                bytes = data.bytes
                filename = data.originalFilename ?: data.name
            } else if (data instanceof File) {
                bytes = data.bytes
                filename = data.name
            } else if (data instanceof FileItem) {
                bytes = data.inputStream.bytes
                filename = data.name
            }

            codec.encode(new BsonDocumentWriter(bsonDoc), obj, EncoderContext.builder().build())

            // clean id
            if (bsonDoc.containsKey(idProperty)
                    && bsonDoc.get(idProperty).isString()
                    && bsonDoc.get(idProperty).asString().value.equals('null')) {
                bsonDoc.remove(idProperty)
            }

            // remove md5 (if exist)
            if (bsonDoc.containsKey(md5Property))
                bsonDoc.remove(md5Property)

            // remove chunkSize (if exist)
            if (bsonDoc.containsKey(chunkSizeProperty))
                bsonDoc.remove(chunkSizeProperty)

            // remove length (if exist)
            if (bsonDoc.containsKey(lengthProperty))
                bsonDoc.remove(lengthProperty)

            // one-to-many
            entity.associations.each { assoc ->
                if (assoc instanceof OneToMany) {
                    BsonArray idsArray = new BsonArray(obj[assoc.name]?.collect { new BsonObjectId(assoc.id) })

                    bsonDoc.append(assoc.name, idsArray)
                }
            }

            log.debug("BSON object: ${bsonDoc}")

            // converting BSON to GridFS file
            Document nativeDoc = Document.parse(bsonDoc.toJson())

            // creating new grid fs file or loading existing from DB
            GridFSFile inputFile = nativeDoc.get(idProperty) ? gridFS.findOne(nativeDoc.getObjectId(idProperty))
                    : gridFS.createFile(bytes)

            nativeDoc.entrySet().each {
                inputFile.put(it.key, it.value)
            }

            // only for new file
            if (inputFile instanceof GridFSInputFile) {
                if (!inputFile.uploadDate)
                    inputFile.put uploadDateProperty, new Date()

                inputFile.filename = inputFile.filename ?: filename
                inputFile.contentType = inputFile.contentType ?: detectMimeType(bytes).name
            }

            inputFile.save()

            // assign properties obtained after document inserted to DB
            obj.id = inputFile.id

            if (obj.metaClass.getMetaProperty(uploadDateProperty))
                obj.uploadDate = inputFile.uploadDate

            if (obj.metaClass.getMetaProperty(contentTypeProperty))
                obj.contentType = inputFile.contentType

            if (obj.metaClass.getMetaProperty(filenameProperty))
                obj.filename = inputFile.filename

            if (obj.metaClass.getMetaProperty(md5Property))
                obj.md5 = inputFile.getMD5()

            if (obj.metaClass.getMetaProperty(chunkSizeProperty))
                obj.chunkSize = inputFile.chunkSize

            if (obj.metaClass.getMetaProperty(lengthProperty))
                obj.length = inputFile.length

            if (obj.metaClass.getMetaProperty(aliasesProperty))
                obj.aliases = inputFile.aliases

            // hack to clear dirty state
            try {
                obj.org_grails_datastore_mapping_dirty_checking_DirtyCheckable__$changedProperties = new LinkedHashMap<>()
                obj.org_grails_datastore_gorm_GormValidateable__errors = null
            } catch (ignored) {
            }
        }
        return obj
    }

    /**
     * @param obj
     */
    void delete(obj) {
        Assert.notNull(obj, 'The obj must not be null')

        if (obj.id) {
            obj.gridfs.remove(obj.id)
        }
    }

    /**
     * @param obj
     * @param file
     */
    void setFile(obj, file) {
        Assert.notNull(obj, 'The obj must not be null')

        if (file instanceof MultipartFile || file instanceof FileItem || file instanceof File) {
            setFileObj(obj, file)

            if (obj.metaClass.getMetaProperty(filenameProperty)) {
                if (file instanceof MultipartFile) {
                    obj.filename = file.originalFilename ?: data.name
                } else if (file instanceof File) {
                    obj.filename = file.name
                } else if (file instanceof FileItem) {
                    obj.filename = file.name
                }
            }
        } else {
            throw new RuntimeException("${file.class} is not supported")
        }
    }

    /**
     * @param obj
     * @param bytes
     */
    void setBytes(obj, byte[] bytes) {
        assert obj

        setFileObj(obj, new ByteArrayInputStream(bytes))

        if (obj.metaClass.getMetaProperty(filenameProperty))
            obj.filename = null

        if (obj.metaClass.getMetaProperty(contentTypeProperty))
            obj.contentType = detectMimeType(bytes).name
    }

    /**
     * @param obj
     * @param inputStream
     */
    void setInputStream(obj, InputStream inputStream) {
        Assert.notNull(obj, 'The obj must not be null')

        byte[] bytes = inputStream.bytes

        setBytes(obj, bytes)
    }

    /**
     * @param obj
     */
    GridFSDBFile getDbFile(obj) {
        Assert.notNull(obj, 'The obj must not be null')

        if (obj.id) {
            return obj.gridfs.find(obj.id)
        }
    }

    /**
     * Sends the file to the client.
     * If no filename is given, the one from the gridfsfile is used.
     *
     * @param response
     * @param obj
     * @param filename
     * @param asAttachment
     */
    void deliverFile(obj, HttpServletResponse response = null, boolean asAttachment = true, String filename = null) {
        Assert.notNull(obj, 'The file must not be null')

        response = response ?: WebUtils.retrieveGrailsWebRequest()?.currentRequest

        if (obj == null || (obj && !gridfsClasses.contains(obj.class))) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            } catch (ignored) {
            }
            return
        }

        def dbFile = obj.dbFile

        if (filename == null) filename = dbFile.filename

        response.contentType = dbFile.contentType
        response.contentLength = dbFile.length.toInteger()
        response.setDateHeader('Last-Modified', dbFile.uploadDate.getTime())
        response.setHeader('ETag', obj.id.toString().encodeAsSHA1())

        if (asAttachment)
            setAttachmentHeader(response, filename)

        try {
            IOUtils.copy dbFile.inputStream, response.outputStream
        } catch (ignored) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * @param bytes
     */
    static MimeType detectMimeType(byte[] bytes) {
        TikaConfig config = TikaConfig.getDefaultConfig()

        MediaType mediaType = config.getMimeRepository().detect(new ByteArrayInputStream(bytes), new Metadata())
        config.getMimeRepository().forName(mediaType.toString())
    }

    private static String fileObjProperty = '__fileObj'
    private static String idProperty = '_id'
    private static String uploadDateProperty = 'uploadDate'
    private static String contentTypeProperty = 'contentType'
    private static String filenameProperty = 'filename'
    private static String md5Property = 'md5'
    private static String chunkSizeProperty = 'chunkSize'
    private static String lengthProperty = 'length'
    private static String aliasesProperty = 'aliases'

    private static void setFileObj(obj, file) {
        if (obj.metaClass.hasProperty(obj, fileObjProperty)) {
            obj.metaClass.setProperty(obj, fileObjProperty, file)
        } else {
            obj.metaClass."${fileObjProperty}" = file
        }
    }

    private static void setAttachmentHeader(HttpServletResponse response, String filename = null) {
        response.setHeader('Content-Disposition', "attachment; filename=\"" + filename + "\"")
    }

}
