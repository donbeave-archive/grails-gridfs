grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {

  inherits("global") {
    excludes 'xml-apis', 'netty'
  }

  log "warn"

  repositories {
    grailsCentral()
    mavenLocal()
    mavenCentral()
  }

  dependencies {

  }

  plugins {
    compile ':mongodb:1.0.0.GA'
  }

}
