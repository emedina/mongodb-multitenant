includeTargets << grailsScript("Init")

target(main: "This will create the default domain classes that maps tenants towards servernames") {
  def ant = new AntBuilder();


  ant.mkdir(dir: 'grails-app/domain/se/webinventions')
  new File("grails-app/domain/se/webinventions/TenantDomainMap.groovy").write('''

package se.webinventions

/**
 * Class to map tenant to a specific url.
 */
class TenantDomainMap {

  Tenant tenant
  String domainName

    static constraints = {
    }
}

''')


   ant.mkdir(dir: 'grails-app/domain/se/webinventions')
  new File("grails-app/domain/se/webinventions/Tenant.groovy").write('''

package se.webinventions

import se.webinventions.mongomultitenant.TenantProvider

class Tenant implements TenantProvider {

    String id = UUID.randomUUID().toString()
    String collectionNameSuffix
    String databaseNameSuffix
    String name = ""
    static constraints = {
    }

  static mapping = {
     name index: true

   }

}

''')


}

setDefaultTarget(main)

