package se.webinventions.mongomultitenant

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-05-12
 * Time: 13:51
 * To change this template use File | Settings | File Templates.
 */
public interface TenantDomainMapProvider {
    public String getDomainUrl()
    public void setDomainUrl(String newurl)
    public TenantProvider getTenant()
    public void setTenant(TenantProvider ten)
}