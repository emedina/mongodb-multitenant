import grails.util.GrailsUtil
import org.springframework.data.document.mongodb.MongoTemplate;

/**
 Overrides the mongotemplate default template to incorporate a multitenant resolver addon to the default name
 based on a custom tenantResolver bean (session scoped service bean most likely )
 */
class MongoDbMultitenantBootstrap {

	def init = { servletContext ->


         MongoTemplate.metaClass.getDefaultCollectionName = {
           def tenantRes = getBean("tenantResolver")
           def tid = tenantRes.getTenantId()

           return tid.toString()+"_"+delegate.getDefaultCollectionName()
         }
         log.info("override: MongoTemplate.metaClass.getDefaultCollectionName to add tenantResolver bean getTenantId method to the collection name ")
         MongoTemplate.metaClass.getDefaultCollection = {

           return delegate.getCollection(delegate.getDefaultCollectionName);
         }
         log.info("override: MongoTemplate.metaClass.getDefaultCollection to get the tenant based collection instead  ")
	}
	def destroy = {
	}
}
