import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.springframework.datastore.mapping.web.support.OpenSessionInViewInterceptor
import org.springframework.datastore.mapping.transactions.DatastoreTransactionManager
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.mongo.*
import org.grails.datastore.gorm.mongo.bean.factory.*



import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.springframework.datastore.mapping.reflect.ClassPropertyFetcher
import org.springframework.context.ApplicationContext
import org.springframework.datastore.mapping.core.Datastore
import org.springframework.transaction.PlatformTransactionManager
import org.grails.datastore.gorm.utils.InstanceProxy
import com.mongodb.ServerAddress
import se.webinventions.mongomultitenant.MongoTenantGormEnhancer


class MongodbMultitenantGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7 > *"
    // the other plugins this plugin depends on
    def dependsOn = [mongodb:"* > 1.0"]
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
        // TODO Implement runtime spring config (optional)



    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)


      Datastore datastore = ctx.getBean("mongoDatastore")
      PlatformTransactionManager transactionManager = ctx.getBean("mongoTransactionManager")
      def enhancer = transactionManager ?
                          new MongoTenantGormEnhancer(datastore, transactionManager) :
                          new MongoTenantGormEnhancer(datastore)


   	  def isHibernateInstalled = manager.hasGrailsPlugin("hibernate")
      for(entity in datastore.mappingContext.persistentEntities) {
        def cls = entity.javaClass
        def cpf = ClassPropertyFetcher.forClass(cls)

        def mappedWith = cpf.getStaticPropertyValue(GrailsDomainClassProperty.MAPPING_STRATEGY, String)
        if(isHibernateInstalled) {
          if(mappedWith == 'mongo') {
            enhancer.enhance(entity)
          }
          else {
            def staticApi = new MongoGormStaticApi(cls, datastore)
            def instanceApi = new MongoGormInstanceApi(cls, datastore)
            cls.metaClass.static.getMongo = {-> staticApi }
            cls.metaClass.getMongo = {-> new InstanceProxy(instance:delegate, target:instanceApi) }
          }
        }
        else {
		  if(mappedWith == 'mongo' || mappedWith == null)
          	enhancer.enhance(entity)
        }
      }

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
