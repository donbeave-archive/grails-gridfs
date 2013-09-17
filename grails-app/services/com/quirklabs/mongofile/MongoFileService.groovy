package com.quirklabs.mongofile

import com.mongodb.*
import com.mongodb.gridfs.GridFS
import com.mongodb.gridfs.GridFSDBFile
import com.mongodb.gridfs.GridFSFile
import com.mongodb.gridfs.GridFSInputFile
import org.apache.commons.io.IOUtils
import org.apache.tika.config.TikaConfig
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeType
import org.bson.types.ObjectId
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.springframework.web.multipart.MultipartFile

import javax.servlet.http.HttpServletResponse

class MongoFileService {

  static transactional = false

  def mongo
  private HashMap<String, GridFS> _gridfs = new HashMap<String, GridFS>()

  /**
   * Save a multipart file linked to an external id. Deletes any file currently linked to that id.
   *
   * @param file instance of MultipartFile, generally from an upload
   * @param domainClass domain class to associate with
   * @param id id of the domain class to associate with
   * @param fieldName optional field name
   * @return GridFSFile new file
   */
  public GridFSFile saveFile(MultipartFile file, Class domainClass, Long id, String fieldName = '') {
    String bucket = getBucket(domainClass, fieldName)

    deleteFile(domainClass, id, fieldName)
    saveFileMultipart(bucket, file, null, [id: id])
  }

  /**
   * Save a byte array as a file linked to an external id. Deletes any file currently linked to that id.
   *
   * @param fileContents byte array of file data
   * @param fileName name of file
   * @param domainClass domain class to associate with
   * @param id id of the domain class to associate with
   * @param fieldName optional field name
   * @return GridFSFile new file
   */
  public GridFSFile saveFile(byte[] fileContents, String fileName, Class domainClass, Long id, String fieldName = '') {
    String bucket = getBucket(domainClass, fieldName)

    deleteFile(domainClass, id, fieldName)
    saveFileBytes(bucket, fileContents, fileName, [id: id])
  }

  /**
   * Save an input stream as a file linked to an external id. Deletes any file currently linked to that id.
   *
   * @param inputStream input stream of file data
   * @param fileName name of file
   * @param domainClass domain class to associate with
   * @param id id of the domain class to associate with
   * @param fieldName optional field name
   * @return GridFSFile new file
   */
  public GridFSFile saveFile(InputStream inputStream, String fileName, Class domainClass, Long id, String fieldName = '') {
    String bucket = getBucket(domainClass, fieldName)

    deleteFile(domainClass, id, fieldName)
    saveFileStream(bucket, inputStream, fileName, [id: id])
  }

  /**
   * Save a multipart file with custom metadata
   *
   * @param bucket the name of the collection in GridFS
   * @param file instance of MultipartFile, generally from an upload
   * @param filename the filename of the file
   * @param metaData any metadata to save
   * @return GridFSFile new file
   */
  public GridFSFile saveFileMultipart(String bucket, MultipartFile file, String filename = null, Map metaData = null) {
    GridFS gfs = getGridfs(bucket, metaData?.id != null)

    // get content type
    byte[] b = new byte[4096]
    BufferedInputStream s = new BufferedInputStream(file.getInputStream(), 6144)
    s.mark(0)
    s.read(b, 0, 4096)
    s.reset()
    def contentType = getMimeType(b)

    GridFSInputFile gInputFile = gfs.createFile(s)
    if (!filename) {
      filename = file.getOriginalFilename()
    }
    gInputFile.setFilename(filename)
    gInputFile.setContentType(contentType)
    if (metaData) {
      DBObject mD = new BasicDBObject()
      metaData.each { mD.put(it.key.toString(), it.value) }
      gInputFile.setMetaData(mD)
    }

    _saveFile(gfs, bucket, gInputFile)
  }

  /**
   * Save a java.io.File with custom metadata
   *
   * @param bucket the name of the collection in GridFS
   * @param file instance of the File
   * @param filename the filename of the file
   * @param metaData any metadata to save
   * @return GridFSFile new file
   */
  public GridFSFile saveFileFile(String bucket, File file, String filename, Map metaData = null) {
    GridFS gfs = getGridfs(bucket, metaData?.id != null)

    GridFSInputFile gInputFile = gfs.createFile(file.newInputStream())
    gInputFile.setFilename(filename)
    gInputFile.setContentType(getMimeType(file))
    if (metaData) {
      DBObject mD = new BasicDBObject()
      metaData.each { mD.put(it.key.toString(), it.value) }
      gInputFile.setMetaData(mD)
    }

    _saveFile(gfs, bucket, gInputFile)
  }

  /**
   * Save an byte array with custom metadata e.g. for images, where you have bytedata only
   *
   * @param bucket the name of the collection in GridFS
   * @param fileContents byte array containing file data
   * @param filename the filename of the file
   * @param metaData any metadata to save
   * @return GridFSFile new file
   */
  public GridFSFile saveFileBytes(String bucket, byte[] fileContents, String filename, Map metaData = null) {
    GridFS gfs = getGridfs(bucket, metaData?.id != null)

    GridFSInputFile gInputFile = gfs.createFile(new ByteArrayInputStream(fileContents))
    gInputFile.setFilename(filename)
    gInputFile.setContentType(getMimeType(fileContents))
    if (metaData) {
      DBObject mD = new BasicDBObject()
      metaData.each { mD.put(it.key.toString(), it.value) }
      gInputFile.setMetaData(mD)
    }

    _saveFile(gfs, bucket, gInputFile)
  }

  /**
   * Save an java.io.InputStream with custom metadata
   *
   * @param bucket the name of the collection in GridFS
   * @param fileContents byte array containing file data
   * @param filename the filename of the file
   * @param metaData any metadata to save
   * @return GridFSFile new file
   */
  public GridFSFile saveFileStream(String bucket, InputStream inputStream, String filename, Map metaData = null) {
    GridFS gfs = getGridfs(bucket, metaData?.id != null)

    GridFSInputFile gInputFile = gfs.createFile(inputStream)
    gInputFile.setFilename(filename)
    gInputFile.setContentType(getMimeType(inputStream.bytes))
    if (metaData) {
      DBObject mD = new BasicDBObject()
      metaData.each { mD.put(it.key.toString(), it.value) }
      gInputFile.setMetaData(mD)
    }

    _saveFile(gfs, bucket, gInputFile)
  }

  private GridFSFile _saveFile(GridFS gfs, String bucket, GridFSInputFile gInputFile) {
    try {
      gInputFile.save()
    } catch (Exception e) {
      log.error('could not save file ' + gInputFile + ': ' + e.message)
    }

    return gInputFile
  }

  def GridFSDBFile getFile(String bucket, String id, boolean asObjectId = true) {
    GridFS gfs = getGridfs(bucket, false)

    if (asObjectId) {
      return gfs.findOne(new ObjectId(id))
    } else {
      return gfs.findOne(new BasicDBObject('_id', id))
    }
  }

  def GridFSDBFile getFile(Class domainClass, Long id, String fieldName = '') {
    String bucket = getBucket(domainClass, fieldName)

    return findFile(bucket, ['metadata.id': id])
  }

  def GridFSDBFile getFile(String bucket, Long id) {
    return findFile(bucket, ['metadata.id': id])
  }

  def GridFSDBFile findFile(String bucket, Map query) {
    GridFS gfs = getGridfs(bucket, false)

    return gfs.findOne(new BasicDBObject(query))
  }

  def deleteFile(Class domainClass, Long id, String fieldName = '') {
    String bucket = getBucket(domainClass, fieldName)

    deleteFile(bucket, ['metadata.id': id])
  }

  def deleteFile(String bucket, Map query) {
    GridFS gfs = getGridfs(bucket, false)

    gfs.remove(query as BasicDBObject)
  }

  def dropCollections() {
    def dbname = ApplicationHolder.application.config.mongodb?.database
    dbname = dbname ? dbname + 'db' : 'db'
    DB db = mongo.mongo.getDB(dbname)
    def collectionNames = db.getCollectionNames()
    collectionNames.each { name ->
      if (!name.startsWith('system.')) {
        def collection = db.getCollectionFromString(name)
        collection.drop()
      }
    }
  }

  def dropDatabase() {
    def dbname = ApplicationHolder.application.config.mongodb?.database
    dbname = dbname ? dbname + 'db' : 'db'
    DB db = mongo.mongo.getDB(dbname)
    db.dropDatabase()
  }

  private GridFS getGridfs(String bucket, boolean metadata = true) {
    def gridfs = _gridfs[bucket]
    if (!gridfs) {
      def dbname = ApplicationHolder.application.config.mongodb?.database
      dbname = dbname ? dbname + 'db' : 'db' // use db '<DBNAME>files' for files
      DB db = mongo.mongo.getDB(dbname)

      db.setWriteConcern(WriteConcern.SAFE)
      gridfs = new GridFS(db, bucket)
      _gridfs[bucket] = gridfs

      // set indices
      DBCollection collection = db.getCollection(bucket + ".files")

      if (metadata) {
        collection.ensureIndex(new BasicDBObject("metadata.id", 1), new BasicDBObject('unique', true).append('dropDups', true))
      }
    }

    gridfs
  }

  public String getBucket(Class clazz, String fieldName) {
    getBucketFromString(clazz.simpleName, fieldName)
  }

  public String getBucketFromString(String className, String fieldName) {
    String bucket = className.toLowerCase()
    if (fieldName) {
      bucket = "${bucket}_${fieldName.replaceAll("\\.", "_")}"
    }

    bucket
  }

  public String getMimeType(File file) {
    return getMimeType(file.bytes)
  }

  public String getMimeType(byte[] ba) {
    // use mime magic
    MimeType mimeType = detectMimeType(ba)

    if (mimeType) {
      return mimeType.name
    }

    return "application/octet-stream"
  }

  public void deliverFile(HttpServletResponse response, boolean asAttachment, Class domainClass, Long id, String fieldName = '') {
    GridFSDBFile file = getFile(domainClass, id, fieldName)
    if (file == null) {
      throw new FileNotFoundException("Could not find ${domainClass.name} file for id ${id}")
    }

    deliverGridFSFile(response, file, null, asAttachment)
  }

  /**
   * sends the file to the client
   * if no filename is given, the one from the gridfsfile is used
   *
   * @param response
   * @param file
   * @param filename
   */
  public void deliverGridFSFile(HttpServletResponse response, GridFSDBFile file, String filename = null, boolean asAttachment = true) {
    if (file == null) {
      try {
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      } catch (Exception e2) {
      }
      return
    }

    response.setContentType file.getContentType()
    response.setContentLength((int) file.getLength())
    if (filename == null) filename = file.getFilename()
    if (asAttachment) response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
    // response.setHeader("Cache-Control", "no-store")
    // response.setHeader("Pragma", "no-cache")
    // response.setDateHeader("Expires", 0)

    try {
      IOUtils.copy(file.getInputStream(), response.getOutputStream())
    } catch (Exception e) {
      try {
        // todo log
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      } catch (IOException ignored) {
      }
    }
  }

  /**
   * send local file to client
   * if mimetype = null, it is guessed by file content (mime magic)
   * if filename = null, the original file name is used
   *
   * @param response
   * @param file
   * @param filename
   * @param mimeType
   */
  public void deliverLocalFile(HttpServletResponse response, File file, String filename = null, String mimeType = null, boolean asAttachment = true) {
    if (!file.exists()) {
      try {
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      } catch (Exception e2) {
      }
      return
    }

    // handle length
    long size = file.length()
    if (size > 0) response.setContentLength((int) size)

    if (filename == null) filename = file.getName()
    if (asAttachment) response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
    // response.setHeader("Cache-Control", "no-store")
    // response.setHeader("Pragma", "no-cache")
    // response.setDateHeader("Expires", 0)

    // handle mimetype - if mimetype not known, then send code forbidden
    if (mimeType == null) mimeType = getMimeType(file)
    response.setContentType(mimeType)

    // send content
    try {
      IOUtils.copy(new FileInputStream(file), response.getOutputStream())
    } catch (Exception e) {
      try {
        // @todo log
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      } catch (IOException ignored) {
      }
    }
  }

  String detectExtension(byte[] bytes) {
    MimeType mimeType = detectMimeType(bytes)

    return mimeType.getExtension();
  }

  public MimeType detectMimeType(byte[] bytes) {
    TikaConfig config = TikaConfig.getDefaultConfig()

    InputStream stream = new ByteArrayInputStream(bytes);
    MediaType mediaType = config.getMimeRepository().detect(stream, new Metadata())
    config.getMimeRepository().forName(mediaType.toString())
  }

}