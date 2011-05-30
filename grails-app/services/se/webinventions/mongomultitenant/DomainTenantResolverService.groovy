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
  List<TenantChangeListener> listeners = new LinkedList<TenantChangeListener>();


  ApplicationContext applicationContext

  def config = ConfigurationHolder.getConfig();



  TenantProvider defaultTenant //tenant that is cohering to the current url etc (default resovlved)
  TenantProvider currentTenant  //tenant that is forced by eg.g. admin


  def resolvedefaultTenant() {
    TenantProvider tenant = resolveDomainTenant()
    defaultTenant = tenant;

    updateListeners(defaultTenant)
    return defaultTenant
  }


  private String resolveServerName() {
    def serverName = getCurrentRequestAttr()?.getRequest()?.getServerName();
    if (!serverName) {
      serverName = "bootstrap"
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



  public Object getTenantDomainMapping(TenantProvider tp) throws Exception {
    this.currentServerName = resolveServerName()
    Logger log = Logger.getLogger(getClass())

    def tenantMappingClassName = config?.grails?.mongo?.tenant?.tenantmappingclassname ?: "se.webinventions.TenantDomainMap"
    def domainClass = grailsApplication.getClassForName(tenantMappingClassName)

    if (!tenantServiceProxy) {
      tenantServiceProxy = applicationContext.getBean("tenantServiceProxy")
    }

    def domainTenantMappings
    def tenant;
    domainTenantMappings = domainClass.list()
    def foundMapping = null

    domainTenantMappings?.each { domtm ->

      if (currentServerName.toString().indexOf(domtm.getDomainUrl()) > -1) {
        if (foundMapping != null) {
          //determine if its a better match than the previous (more exact)


          if (currentServerName.toString().indexOf(domtm.getDomainUrl()) > -1 &&
              currentServerName.toString().indexOf(foundMapping.getDomainUrl()) > -1) {

            def fml = foundMapping.getDomainUrl().length()
            def dml = domtm.getDomainUrl().length()
            if (dml >= fml) {

              foundMapping = domtm
            }

          }
        } else {
          //first match
          foundMapping = domtm;
        }


      }

    }


    return foundMapping;
  }

  /**
   * perhaps make this public +send in the request attribute
   * and use it from a filter and make the service work on application context level instead
   * @return
   */
  private TenantProvider resolveDomainTenant() {
    Logger log = Logger.getLogger(getClass())
    def dommap
    def tenant
    try {
      dommap = getTenantDomainMapping()
    }
    catch (Exception e) {

      //we are in bootstrapping perhaps so the gorm methods are not yet available
      log.info("Bootstrapping so resolving tenant to bootstrapping tenant")
      def deftenantid = config?.grails?.mongo?.tenant?.defaultTenantId ?: 0

      tenant = tenantServiceProxy.createNewTenant("bootstrap_init_temp")
      tenant.id = deftenantid;

    };


    if (dommap) {
      if (dommap?.getTenant()) {
        tenant = dommap.getTenant()
      }

    }

    //if tenant is null still we need to find or create a default tenant with default options specified in config.groovy
    if (!tenant) {
      def deftenant = config?.grails?.mongo?.tenant?.defaultTenantName ?: "maindefaulttenant"



      tenant = tenantServiceProxy.createOrGetDefaultTenant(deftenant)

      //try saving this tenant if possible
      try {
        tenant.save(flush: true)
      } catch (Exception e) {
        log.debug("Tenant could not be saved in tenant resolver service " + e)
      }

    }




    return tenant;
  }

  private updateListeners(TenantProvider newtenant) {
    this.listeners.each { l ->
      l.tenantChanged()
    }
  }

  def revertToDefaultTenant() {
    currentTenant = null;
    updateListeners(defaultTenant)
    return defaultTenant
  }

  def getTenantId() {
    securityCheckDomainChangeAndTenantChange()

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

  def securityCheckDomainChangeAndTenantChange() {

    Logger log = Logger.getLogger(getClass())
    //make security check based on the current server name change
    if (!resolveServerName()?.equalsIgnoreCase(currentServerName)) {
      //switch tenant
      def newTenant = resolveDomainTenant()
      if (newTenant != defaultTenant) {
        //we have a new domain and should logout if necessary.
        if (PluginManagerHolder.pluginManager.hasGrailsPlugin('spring-security-core')) {
          def springSecurityService = applicationContext.getBean("springSecurityService")
          if (springSecurityService?.isLoggedIn()) {
            springSecurityService?.reauthenticate();
          }

        }
        //todo add support for shiro security or others..


        defaultTenant = newTenant;
        updateListeners(newTenant)
        currentTenant = null;
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
    securityCheckDomainChangeAndTenantChange();
    if (currentTenant) {return currentTenant}
    else { return defaultTenant}
  }

  void resetToDefaultTenant() {
    revertToDefaultTenant()
  }

  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }

  void addListener(TenantChangeListener l) {
    this.listeners.add(l)
  }
}
