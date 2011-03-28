

package se.webinventions.mongomultitenant

import org.grails.datastore.gorm.events.AutoTimestampInterceptor 
import org.grails.datastore.gorm.events.DomainEventInterceptor 
import org.springframework.beans.factory.FactoryBean;
import org.springframework.datastore.mapping.model.MappingContext;
import org.springframework.datastore.mapping.mongo.MongoDatastore;

import com.mongodb.Mongo;

/**
 * Factory bean for constructing a {@link MongoTenantDatastore} instance.
 * 
 * @author Per Sundberg
 *
 */
class MongoTenantDatastoreFactoryBean implements FactoryBean<MongoTenantDatastore>{

	Mongo mongo
	MappingContext mappingContext
	Map<String,String> config = [:]
  MongodbTenantResolver tenantResolverProxy
  MongoDatastore datastore

	@Override
	public MongoTenantDatastore getObject() throws Exception {
		
		if(!datastore) {
      if(mongo != null)
             datastore = new MongoTenantDatastore(mappingContext, mongo,config,tenantResolverProxy)
          else {
            datastore = new MongoTenantDatastore(mappingContext, config,tenantResolverProxy)
          }

          datastore.addEntityInterceptor(new DomainEventInterceptor())
          datastore.addEntityInterceptor(new AutoTimestampInterceptor())
          datastore.afterPropertiesSet()

    }
		return datastore;
	}

	@Override
	public Class<?> getObjectType() { MongoTenantDatastore }

	@Override
	boolean isSingleton() { true }

}
