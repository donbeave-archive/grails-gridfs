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

import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.gridfs.GridFSInputFile
import org.apache.commons.fileupload.FileItem
import org.apache.commons.io.IOUtils
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeType
import org.springframework.web.multipart.MultipartFile

import javax.servlet.http.HttpServletResponse

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsService {

    def mongoDatastore

    List gridfsClasses = []

    /**
     * @param obj
     */
    def save(obj) {
        if (obj.validate()) {
            if (!obj.metaClass.hasProperty(obj, fileObj)) {
                throw new RuntimeException('Can not save object, file not initialized')
            }

            def data = obj.metaClass.getProperty(obj, fileObj)

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

            GridFSInputFile nativeEntry =
                    mongoDatastore.currentSession.getPersister(obj).getNative(obj, obj.gridfs, bytes)

            if (!nativeEntry.uploadDate) {
                nativeEntry.put 'uploadDate', new Date()
            }
            nativeEntry.filename = nativeEntry.filename ?: filename
            nativeEntry.contentType = nativeEntry.contentType ?: detectMimeType(bytes).name

            nativeEntry.save()

            obj.id = nativeEntry.id
        }
        return obj
    }

    /**
     * @param obj
     */
    void delete(obj) {
        if (obj.id) {
            obj.gridfs.remove(obj.id)
        }
    }

    /**
     * @param obj
     * @param file
     */
    void setFile(obj, file) {
        if (file instanceof MultipartFile || file instanceof FileItem || file instanceof File) {
            setFileObj(obj, file)

            if (obj.metaClass.getMetaProperty('filename')) {
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
        setFileObj(obj, new ByteArrayInputStream(bytes))
        try {
            obj.filename = null
        }
        catch (e) {
        }
        try {
            obj.contentType = detectMimeType(bytes).name
        }
        catch (e) {
        }
    }

    /**
     * @param obj
     * @param inputStream
     */
    void setInputStream(obj, InputStream inputStream) {
        byte[] bytes = inputStream.bytes

        setBytes(obj, bytes)
    }

    /**
     * @param obj
     */
    GridFSDBFile getDbFile(obj) {
        if (obj.id) {
            return obj.gridfs.find(obj.id)
        }
    }

    /**
     * Sends the file to the client.
     * If no filename is given, the one from the gridfsfile is used.
     *
     * @param response
     * @param file
     * @param filename
     * @param asAttachment
     */
    void deliverFile(HttpServletResponse response, file, String filename = null, boolean asAttachment = true) {
        if (file == null || (file && !gridfsClasses.contains(file.class))) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            } catch (e) {
            }
            return
        }

        def dbFile = file.dbFile

        if (filename == null) filename = dbFile.filename

        response.contentType = dbFile.contentType
        response.contentLength = dbFile.length.toInteger()
        if (asAttachment)
            setAttachmentHeader(response, filename)

        try {
            IOUtils.copy dbFile.inputStream, response.outputStream
        } catch (e) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            } catch (IOException ignored) {
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

    private static String fileObj = '__fileObj'

    private static void setFileObj(obj, file) {
        if (obj.metaClass.hasProperty(obj, fileObj)) {
            obj.metaClass.setProperty(obj, fileObj, file)
        } else {
            obj.metaClass."${fileObj}" = file
        }
    }

    private static void setAttachmentHeader(HttpServletResponse response, String filename = null) {
        response.setHeader('Content-Disposition', "attachment; filename=\"" + filename + "\"")
    }

}
