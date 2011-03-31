
import se.webinventions.mongomultitenant.MongoTenantDatastoreFactoryBean
import org.springframework.aop.scope.ScopedProxyFactoryBean
import se.webinventions.mongomultitenant.DomainTenantResolverService
import se.webinventions.mongomultitenant.TenantService


class MongodbMultitenantGrailsPlugin {
  // the plugin version
  def version = "0.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3.7 > *"
  // the other plugins this plugin depends on
  def dependsOn = [mongodb: " * > 0.9"]
  def loadAfter = ['mongodb']
  // resources that are excluded from plugin packaging
  def pluginExcludes = [
      "grails-app/views/error.gsp"
  ]

  // TODO Fill in these fields
  def author = "Per Sundberg"
  def authorEmail = "contact@webinventions.se"
  def title = "Mongodb Multitenant plugin"
  def description = '''\\
Plugin that enables multitenancy for mongodb. The plugin works by overrideing the MongoDatastore bean.
You mark classes either through inclusion or exclusion by adding the config field in Config.groovy
'''

  // URL to the plugin's documentation
  def documentation = "http://grails.org/plugin/mongodb-multitenant"

  def doWithWebDescriptor = { xml ->
    // TODO Implement additions to web.xml (optional), this event occurs before
  }

  def doWithSpring = {

    def mongoConfig = application.config?.grails?.mongo


    tenantResolver(DomainTenantResolverService) {
      grailsApplication = ref("grailsApplication")
    }

    tenantResolverProxy(ScopedProxyFactoryBean) {
      targetBeanName = 'tenantResolver'
      proxyTargetClass = true
    }

    tenantService(TenantService) {
      grailsApplication = ref("grailsApplication")
    }


    tenantServiceProxy(ScopedProxyFactoryBean) {
      targetBeanName = 'tenantService'
      proxyTargetClass = true
    }






    //this will override the mongoDatastore in the grails mongodbplugin so that we can handle multi tenants of
    //some domain classes which are configured as exclude or include (not both) list of domain classes i Config.groovy


    mongoDatastore(MongoTenantDatastoreFactoryBean) {
      mongo = ref("mongoBean")
      mappingContext = ref("mongoMappingContext")
      config = mongoConfig.toProperties()
      tenantResolverProxy = ref("tenantResolverProxy")

    }





  }

  def doWithDynamicMethods = { ctx ->
  }



  def doWithApplicationContext = { applicationContext ->
    // TODO Implement post initialization spring config (optional)
  }

  def onChange = { event ->
    // TODO Implement code that is executed when any artefact that this plugin is
    // watching is modified and reloaded. The event contains: event.source,
    // event.application, event.manager, event.ctx, and event.plugin.
  }

  def onConfigChange = { event ->
    // TODO Implement code that is executed when the project configuration changes.
    // The event is the same as for 'onChange'.
  }
}
