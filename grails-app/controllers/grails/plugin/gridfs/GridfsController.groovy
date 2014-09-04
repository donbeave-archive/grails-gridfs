package grails.plugin.gridfs

import com.mongodb.gridfs.GridFSDBFile
import org.springframework.util.Assert

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
class GridfsController {

    def gridfsService

    def exists(FileCommand fileCommand) {
        GridFSDBFile file = getGridFSFile(fileCommand)
        file != null
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
    boolean attachment
}