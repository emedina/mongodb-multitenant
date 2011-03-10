package se.webinventions.mongomultitenant


import org.springframework.web.context.request.RequestContextHolder
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.codehaus.groovy.grails.commons.ConfigurationHolder

/**
 *
 */
class DomainTenantResolverService implements MongodbTenantResolver {
  //TODO: implement cashing (hosts) of already fetched tenants.

  static transactional = false
  static scope = "session"

  def config = ConfigurationHolder.getConfig();

  TenantProvider defaultTenant //tenant that is cohering to the current url etc (default resovlved)
  TenantProvider currentTenant  //tenant that is forced by eg.g. admin


  def resolvedefaultTenant() {
    TenantProvider tenant = resolveDomainTenant()
    defaultTenant = tenant;

    return defaultTenant
  }


  private GrailsWebRequest getCurrentRequestAttr() {
    return RequestContextHolder.currentRequestAttributes()
  }

  /**
   * perhaps make this public +send in the request attribute
   * and use it from a filter and make the service work on application context level instead
   * @return
   */
  private TenantProvider resolveDomainTenant() {
    def currentServerName = getCurrentRequestAttr().getRequest().getServerName();
    def domainTenantMappings = SpringUtil.getApplicationContext().getBean("se.webinventions.TenantDomainMap").findAll("from se.webinventions.TenantDomainMap")
    def tenant;
    domainTenantMappings.each { dom ->

      if (currentServerName.toString().equalsIgnoreCase(dom.domainName)) {
        tenant = dom?.tenant;
      }
    }

    return tenant;
  }



  def revertToDefaultTenant() {
    currentTenant = null;
    return defaultTenant
  }

  def getTenantId() {
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
  String getTenantCollection(String originalCollectionName) {

    if (currentTenant) {
      return originalCollectionName + currentTenant.getCollectionNameSuffix()
    } else {
      return originalCollectionName + defaultTenant.getCollectionNameSuffix()
    }

  }


  @Override
  String getTenantDatabase(String originalDatabaseName) {

    if (currentTenant) {
      return originalDatabaseName + currentTenant.getDatabaseNameSuffix()
    } else {
      return originalDatabaseName + defaultTenant.getDatabaseNameSuffix()
    }

  }

  @Override
  void resetTodefaultTenant() {
    resolvedefaultTenant()
  }

  @Override
  public void setTenantId(Object tenantid) {
    //find the tenant and set it
    currentTenant = SpringUtil.getApplicationContext().getBean("se.webinventions.Tenant").findById(tenantid)
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
}
