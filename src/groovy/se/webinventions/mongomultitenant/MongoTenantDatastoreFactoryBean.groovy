

package se.webinventions.mongomultitenant



import com.mongodb.Mongo;
import org.grails.datastore.gorm.events.AutoTimestampEventListener
import org.grails.datastore.gorm.events.DomainEventListener
import org.springframework.beans.factory.FactoryBean
import org.springframework.context.ApplicationContext

import org.grails.datastore.mapping.model.MappingContext
import org.springframework.context.ApplicationContextAware

/**
 * Factory bean for constructing a {@link MongoTenantDatastore} instance.
 * 
 * @author Per Sundberg
 *
 */
class MongoTenantDatastoreFactoryBean implements FactoryBean<MongoTenantDatastore>,  ApplicationContextAware{

	Mongo mongo
	MappingContext mappingContext
	Map<String,String> config = [:]
  MongodbTenantResolver tenantResolverProxy
  MongoTenantDatastore datastore
   ApplicationContext applicationContext

	@Override
	public MongoTenantDatastore getObject() throws Exception {
		
		if(!datastore) {
      if(mongo != null)
             datastore = new MongoTenantDatastore(mappingContext, mongo,config,applicationContext)
          else {
            datastore = new MongoTenantDatastore(mappingContext, config,applicationContext)
          }

      datastore.setTenantResolverProxy(tenantResolverProxy)

             applicationContext.addApplicationListener(new AutoTimestampEventListener(datastore))
        applicationContext.addApplicationListener(new DomainEventListener(datastore))
          datastore.afterPropertiesSet()

    }
		return datastore;
	}

	@Override
	public Class<?> getObjectType() { MongoTenantDatastore }

	@Override
	boolean isSingleton() { true }

}
