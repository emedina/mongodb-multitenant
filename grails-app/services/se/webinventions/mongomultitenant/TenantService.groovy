package se.webinventions.mongomultitenant

import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.commons.GrailsDomainClass

class TenantService {

    static transactional = false
    static scope="session"

  def config = ConfigurationHolder.getConfig();

  //probably a session scoped bean as well but this is up to the implementator to decide.
  MongodbTenantResolver tenantResolver = SpringUtil.getBean("tenantResolver");

  /**
   * This method allows you to temporarily switch tenants to perform some operations.  Before
   * the method exits, it will set the tenantId back to what it was before.
   *
   * @param tenantId
   * @param closure
   * @throws Throwable
   */
  public void doWithTenant(Object tenantId, Closure closure) throws Throwable {
      Object currentTenantId = tenantResolver.getTenantId()
      tenantResolver.setTenantId(tenantId)
      Throwable caught = null;
      try {
          closure.call();
      } catch (Throwable t) {
          caught = t;
      } finally {
          tenantResolver.setTenantId(currentTenantId);
      }
      if (caught != null) {
          throw caught;
      }
  }

  public TenantProvider createNewTenant(String name) {

    def tenantsPerDb = config?.grails?.mongo?.tenant?.tenantsPerDb ?: 500

    if(tenantsPerDb instanceof Integer) {
      tenantsPerDb = 500
    }

    //determine the tenants db number
    def tenants = SpringUtil.getApplicationContext().getBean("se.webinventions.Tenant").findAll("from se.webinventions.Tenant")
    def noOfTenants = tenants?.size()
    Integer dbNum = Math.floor(((double)noOfTenants)/((double)tenantsPerDb))
    TenantProvider tp = SpringUtil.getApplicationContext().getBean("se.webinventions.Tenant").newInstance()
    String newId = UUID.randomUUID().toString();
    tp.setId(UUID.randomUUID().toString(newId))
    tp.setName(name)
    tp.setCollectionNameSuffix("_"+name+"_"+newId)
    tp.setDatabaseNameSuffix("_"+tenantsPerDb.toString())


    return tp; //saving has to be done by the user of the method in case heY/she wants to add more properties..
  }

}
