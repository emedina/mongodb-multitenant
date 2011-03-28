package se.webinventions.mongomultitenant


import org.springframework.web.context.request.RequestContextHolder
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.web.converters.ConverterUtil
import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.plugins.PluginManagerHolder

/**
 *
 */
class DomainTenantResolverService implements MongodbTenantResolver, ApplicationContextAware {
  //TODO: implement cashing (hosts) of already fetched tenants.

  static transactional = false
  static scope = "session"
  static proxy = true
  GrailsApplication grailsApplication
  def tenantServiceProxy
  def currentServerName


  ApplicationContext applicationContext

  def config = ConfigurationHolder.getConfig();
  Logger log = Logger.getLogger(getClass())


  TenantProvider defaultTenant //tenant that is cohering to the current url etc (default resovlved)
  TenantProvider currentTenant  //tenant that is forced by eg.g. admin


  def resolvedefaultTenant() {
    TenantProvider tenant = resolveDomainTenant()
    defaultTenant = tenant;

    return defaultTenant
  }


  private String resolveServerName() {
    def serverName = getCurrentRequestAttr()?.getRequest()?.getServerName();
    if (!serverName) {
      serverName = "localhost"
    }
    return serverName
  }

  private GrailsWebRequest getCurrentRequestAttr() {
    try {
      return RequestContextHolder.currentRequestAttributes()
    } catch (IllegalStateException e) {
      return null
    }

  }

  /**
   * perhaps make this public +send in the request attribute
   * and use it from a filter and make the service work on application context level instead
   * @return
   */
  private TenantProvider resolveDomainTenant() {
    this.currentServerName = resolveServerName()

    //ConverterUtil cu = new ConverterUtil();
    //cu.setGrailsApplication(grailsApplication)

    //cu.getDomainClass("se.webinventions.TenantDomainMap")
    def tenantMappingClassName = config?.grails?.mongo?.tenant?.tenantmappingclassname ?: "se.webinventions.TenantDomainMap"
    def domainClass = grailsApplication.getClassForName(tenantMappingClassName)

    if(!tenantServiceProxy)
        {
          tenantServiceProxy = applicationContext.getBean("tenantServiceProxy")
        }

    def domainTenantMappings
    def tenant;
    try {
      domainTenantMappings = domainClass.list()

    } catch (Exception e) {
      //we are in bootstrapping perhaps so the gorm methods are not yet available
      log.info("Bootstrapping so resolving tenant to bootstrapping tenant")
      def deftenantid = config?.grails?.mongo?.tenant?.defaultTenantId ?: 0

      tenant = tenantServiceProxy.createNewTenant("bootstrap_init_temp")
      tenant.id = deftenantid;
      return tenant;


    }
    finally {
      domainTenantMappings?.each { dom ->

        if (currentServerName.toString().equalsIgnoreCase(dom.domainName)) {
          tenant = dom?.tenant;
        }
      }

      //if tenant is null still we need to find or create a default tenant with default options specified in config.groovy
      if(!tenant) {
        def deftenant = config?.grails?.mongo?.tenant?.defaultTenantName ?: "maindefaulttenant"



        tenant = tenantServiceProxy.createOrGetDefaultTenant(deftenant)


         //try saving this tenant if possible
        try {
          tenant.save(flush:true)
        } catch (Exception e) {
          log.debug("Tenant could not be saved in tenant resolver service " +e)
        }

      }


    }

    return tenant;
  }



  def revertToDefaultTenant() {
    currentTenant = null;
    return defaultTenant
  }

  def getTenantId() {

    //make security check based on the current server name change
    if(!resolveServerName()?.equalsIgnoreCase(this.currentServerName)) {
      //switch tenant
      def newTenant  = resolveDomainTenant()
      if(newTenant!=defaultTenant) {
        //we have a new domain and should logout if necessary.
        if(PluginManagerHolder.pluginManager.hasGrailsPlugin('spring-security-core')) {
          def springSecurityService = applicationContext.getBean("springSecurityService")
          if(springSecurityService?.isLoggedIn()) {
            springSecurityService?.reauthenticate();
          }

        }
                  //todo add support for shiro security.


        defaultTenant = newTenant;
        currentTenant = null;
      }

    }

    if (currentTenant) {
      return currentTenant?.id
    } else {

      if (!defaultTenant) {
        return resolvedefaultTenant()?.id
      } else {
        return defaultTenant?.id
      }

    }
  }

  @Override
  String getTenantCollectionName(String originalCollectionName) {

    if (!defaultTenant) {
      resolvedefaultTenant()
    }

    //check with ? because in bootstrap it will be NULL!
    if (currentTenant) {
      return originalCollectionName + currentTenant?.getCollectionNameSuffix()
    } else {
      return originalCollectionName + defaultTenant?.getCollectionNameSuffix()
    }

  }


  @Override
  String getTenantDatabaseName(String originalDatabaseName) {
    if (!defaultTenant) {
      resolvedefaultTenant()
    }


    //check with ? because in bootstrapping situations the tenant will be NULL!
    if (currentTenant) {
      return originalDatabaseName + currentTenant?.getDatabaseNameSuffix()
    } else {
      def suffix = defaultTenant?.getDatabaseNameSuffix()
      return originalDatabaseName + suffix
    }

  }

  @Override
  void resetTodefaultTenant() {
    resolvedefaultTenant()
  }

  @Override
  public void setTenantId(Object tenantid) {
    //find the tenant and set it
    def tenantClassName = config?.grails?.mongo?.tenant?.tenantclassname ?: "se.webinventions.Tenant"
    currentTenant = applicationContext?.getBean(tenantClassName).get(tenantid)
    //currentTenant = tenantid
  }

  public setTenant(Object tenant) {
    if (!defaultTenant) {
      defaultTenant = tenant
    } else {
      currentTenant = tenant
    }

  }

  public Object getTenant() {
    if (currentTenant) {return currentTenant}
    else { return defaultTenant}
  }

  void resetToDefaultTenant() {
    revertToDefaultTenant()
  }

  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }
}
