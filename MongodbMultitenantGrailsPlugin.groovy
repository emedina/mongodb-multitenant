
import se.webinventions.mongomultitenant.MongoTenantDatastoreFactoryBean
import org.springframework.aop.scope.ScopedProxyFactoryBean
import se.webinventions.mongomultitenant.DomainTenantResolverService
import se.webinventions.mongomultitenant.TenantService


class MongodbMultitenantGrailsPlugin {
  // the plugin version
  def version = "0.1-ALPHA"
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
Plugin that enables multitenancy for mongodb. The plugin works by overrideing the mongoDatastore bean. All tenants have their own
MongoTemplate for choosen tenant domain classes thus creating their own collections and database settings.

Collections are postfixed with _tenantname_tenantid, and also the database is post fixed with an integer 'e.g. _0' for the first
e.g. 500 tenants (depending on setting in config file)

The plugin comes with 2 extra beans which is used by the overriden mongoDatastore bean

* tenantResolver which is responsible for resolving tenants. The default is a domain resover service which resolves against a tenantdomainmap

The interface it implements is as follows:

  /**
   * Get the tenant database name, you can use the originalDatabaseName as a 'starting' point if you want
   * and just .e.g. append a tenant id to it or whatever you like.
   *
   * @param originalDatabaseName the database name it should have had if it wasn't a multi tenant
   * @return
   */

  public String getTenantDatabaseName(String originalDatabaseName)

  /**
   * Get the collection name used for this tenant.
   * @param originalCollectionName name that it should have had if it wasnt a multi tenant
   * @return
   */
  public String getTenantCollectionName(String originalCollectionName)

  /**
   * Gets the current tenant id, (eg. based on url resolving for example or currently logged in user or whatever)
   * @return
   */
  public Object getTenantId()

  /**
   *   override the current set tenant with this one
   * @param tenantid
   */
  public void setTenantId(Object tenantid)

  /**
   *   resets the tenant to the default tenant (based on url for example or logged in user or whatever)
   */
  public void resetToDefaultTenant()


  //get the actual tenant object itself instead of it's id  (could be same for simple tenants)
  public Object getTenant()

  public Object setTenant(Object tenant)


* tenantService which is a helper service for tenants, if you overide this you have to provide at least these methods:

  public void doWithTenant(Object tenantId, Closure closure) throws Throwable  //invokes a closure with a temporary tenantid, in this way superadmins and alike can reach all tenants in an easy way.
  public TenantProvider createOrGetDefaultTenant(String name)
  public TenantProvider createNewTenant(String name)  // creates a new tenant (TenantProvider)





Start of by generating your tenant domain classes which will create

se/webinventions/TenantDomainMap.groovy
and
se/webinventions/Tenant.groovy

In your domains folder.


These can be moved to any package but then you have to specify where in config.groovy (se example below)

The Tenant implements the TenantProvider interface and can thus be replaced with any other class as long as it implements that interface.
so you can choose to store your tenants in any way you like.


You mark domainclasses either through inclusion or exclusion by adding the config field in Config.groovy


Config options include:

 grails.mongo.tenant.tenantclassname = "your.package.TenantDomainclass"
 grails.mongo.tenant.tenantsPerDb = 500
 grails.mongo.tenant.excludingdomainclasses =[Tenant,TenantDomainMap,SecRole]
//alternatively grails.mongo.tenant.includingdomainclasses =[Author,Book,ContentItem,Article] ... etc
 grails.mongo.tenant.defaultTenantName = "default"
 grails.mongo.tenant.defaultTenantId = new ObjectId()

  You cannot specify both exclude and include at this stage. If specifying exclude then 'ALL' Domain classes except those in list will be tenant dependent
  Specifying include -> only those domain classes are tenant dependent.

Sources: https://github.com/webinventions/mongodb-multitenant
Docs: Here..


#Release history#

* 0.1-ALPHA

Initial release.

#Road map#

* Secure plugin with more tests and some cleanups.



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
