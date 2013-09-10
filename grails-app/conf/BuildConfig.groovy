grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'

grails.project.fork = [
    test: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon: true],
    run: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    war: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

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
    compile('eu.medsea.mimeutil:mime-util:2.1.3') {
      excludes([group: 'org.slf4j', name: 'slf4j-log4j12', version: '1.5.6'])
    }
  }

  plugins {
    build(':release:3.0.0',
        ':rest-client-builder:1.0.3') {
      export = false
    }
    compile(':mongodb:1.0.0.GA') {
      export = false
    }
  }
}