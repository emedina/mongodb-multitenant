package se.webinventions.mongomultitenant

import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.codehaus.groovy.grails.web.converters.ConverterUtil
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication
import org.apache.log4j.Logger
import com.mongodb.Mongo
import org.springframework.web.context.request.RequestContextHolder
import javax.servlet.http.HttpSession
import org.springframework.datastore.mapping.transactions.SessionHolder
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.datastore.mapping.core.ConnectionNotFoundException
import org.springframework.datastore.mapping.core.Session


class TenantService implements ApplicationContextAware {

  static transactional = false
  static scope = "session"
  static proxy = true

  def applicationContext
  DefaultGrailsApplication grailsApplication
  def tenantResolverProxy
  MongoTenantDatastore mongoDatastore
  def sessionFactory


  def config = ConfigurationHolder.getConfig();

  void setApplicationContext(ApplicationContext apctx) {
    this.applicationContext = apctx
  }

  private Session getSession() {
    def sess
      try {
        sess = mongoDatastore.retrieveSession()

      } catch (ConnectionNotFoundException ex) {
        Logger log = Logger.getLogger(getClass())
        log.warn("could not get session from datastore .. " +ex)
      }



    return sess;

  }

  //probably a session scoped bean as well but this is up to the implementator to decide.

  /**
   * This method allows you to temporarily switch tenants to perform some operations.  Before
   * the method exits, it will set the tenantId back to what it was before.
   *
   * @param tenantId
   * @param closure
   * @throws Throwable
   */
  public void doWithTenant(Object tenantId, Closure closure) throws Throwable {
    if (!tenantResolverProxy) {
      tenantResolverProxy = applicationContext.getBean("tenantResolverProxy")
    }

    if (!mongoDatastore) {
      mongoDatastore = applicationContext.getBean("mongoDatastore")
    }

    Object currentTenantId = tenantResolverProxy.getTenantId()
    tenantResolverProxy.setTenantId(tenantId)

    //will force a new session with new objects for the correct tenant id
    def sess  = getSession()


    Throwable caught = null;
    try {

      closure.call();
    } catch (Throwable t) {
      caught = t;
        tenantResolverProxy.setTenantId(currentTenantId);
    } finally {
      tenantResolverProxy.setTenantId(currentTenantId);
    }
    if (caught != null) {
      throw caught;
    }
  }

  public TenantProvider createOrGetDefaultTenant(String name) {
    Logger log = Logger.getLogger(getClass())
    def tenantClassName = config?.grails?.mongo?.tenant?.tenantclassname ?: "se.webinventions.Tenant"
    def domainClass = grailsApplication.getClassForName(tenantClassName)

    TenantProvider tp
    try {
      tp = domainClass.findByName(name)

    } catch (Exception e) {

    } finally {
      if (!tp) {
        tp = createNewTenant(name)

        try {
          if (tp?.validate()) {
            tp?.save(flush: true)
            log.info("got and saved default tenant")
          }
          else {
            log.warn("Could not save default tenant due to validation errors? " + tp?.errors)
          }

        } catch (Exception e) {
          log.warn("could not save default tenant" + e)
        }

      }
      return tp;
    }


  }

  public TenantProvider createNewTenant(String name) {
    Logger log = Logger.getLogger(getClass())

    def tenantsPerDb = config?.grails?.mongo?.tenant?.tenantsPerDb ?: 500

    if (tenantsPerDb instanceof Integer) {
      tenantsPerDb = 500
    }

    //determine the tenants db number

    //def domainClass = grailsApplication.getClassForName("se.webinventions.TenantDomainMap")
    def tenantClassName = config?.grails?.mongo?.tenant?.tenantclassname ?: "se.webinventions.Tenant"

    def domainClass = grailsApplication.getClassForName(tenantClassName)

    def tenants
    try {
      tenants = domainClass?.list()

    } catch (Exception e) {
      log.info("We are probably bootstrapping so list() could not be invoked on Tenant object to check number of teants when creating a new tenant..")
      tenants = []
    }

    def noOfTenants = tenants?.size()
    Integer dbNum = Math.floor(((double) noOfTenants) / ((double) tenantsPerDb))

    //TenantProvider tp = applicationContext.getBean("se.webinventions.Tenant").newInstance()


    TenantProvider tp = domainClass.newInstance();

    tp.setName(name)
    tp.setCollectionNameSuffix("_" + name)
    tp.setDatabaseNameSuffix("_" + dbNum.toString())

    try {
      tp.save(flush: true)
      tp.setCollectionNameSuffix(tp.getCollectionNameSuffix() + "_" + tp.id);
      tp.save(flush: true)
    } catch (Exception e) {
      log.debug("could not save tenant on creation (bootstrapping??) " + e)

    }


    return tp; //saving has to be done by the user of the method in case heY/she wants to add more properties..
  }

}
