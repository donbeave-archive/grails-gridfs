package grails.plugin.gridfs

import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.gridfs.GridFSInputFile
import org.apache.commons.fileupload.FileItem
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeType
import org.springframework.web.multipart.MultipartFile

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsService {

    def mongoDatastore

    List gridfsClasses = []

    void save(obj) {
        if (obj.validate()) {
            if (!obj.metaClass.hasProperty(obj, fileObj)) {
                throw new RuntimeException('Can not save object, file not initialized')
            }

            def data = obj.metaClass.getProperty(obj, fileObj)

            byte[] bytes = data.class.isArray() ? data : null
            String filename = null

            if (data instanceof MultipartFile) {
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
    }

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

    void setInputStream(obj, InputStream inputStream) {
        byte[] bytes = inputStream.bytes

        setBytes(obj, bytes)
    }

    GridFSDBFile getDbFile(obj) {
        if (obj.id) {
            return obj.gridfs.find(obj.id)
        }
    }

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

}
