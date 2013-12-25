grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'

grails.project.dependency.resolver = 'maven'
grails.project.dependency.resolution = {
  inherits('global') {

  }
  log 'warn'
  repositories {
    grailsCentral()
    mavenLocal()
    mavenCentral()
  }
  dependencies {
    compile 'org.apache.tika:tika-core:1.4', {
      excludes 'xercesImpl', 'xmlParserAPIs', 'xml-apis', 'groovy'
    }
  }

  plugins {
    build(':release:3.0.1',
        ':rest-client-builder:1.0.3') {
      export = false
    }
    compile(':mongodb:1.3.0') {
      export = false
    }
  }
}
