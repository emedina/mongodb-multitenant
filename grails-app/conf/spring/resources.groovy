import se.webinventions.mongomultitenant.MongoTenantDatastore
import se.webinventions.mongomultitenant.DomainTenantResolverService
import se.webinventions.Tenant
// Place your Spring DSL code here
beans = {

  //this will override the mongoDatastore in the grails mongodbplugin so that we can handle multi tenants of
  //some domain classes which are configured as exclude or include (not both) list of domain classes i Config.groovy

  tenantResolver(DomainTenantResolverService)
  mongoDatastore(MongoTenantDatastore)

}
