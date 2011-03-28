package se.webinventions.mongomultitenant

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-03-08
 * Time: 14:06
 * To change this template use File | Settings | File Templates.
 */
public interface MongodbTenantResolver {

  /**
   * Get the tenant database name, you can use the originalDatabaseName as a 'starting' point if you want
   * and just .e.g. append a tenant id to it or whatever you like. A good idea is to have the tenants database names
   * and stored in a nontenant persistent entity
   *
   * @param originalDatabaseName the database name it should have had if it wasn't a multi tenant
   * @return
   */

  public String getTenantDatabaseName(String originalDatabaseName)

  /**
   * Get the collection name used for this tenant. You can .e.g. store the collection name for the tenant in a db entity
   * that is non tenant. So for example, first resolve the tenant based on URL then get the tenants collection name
   * from the db domain that is not a multitenant
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
   *   override the current set tenant with this one, used by TenantService to do 'doWithTenant'
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



}