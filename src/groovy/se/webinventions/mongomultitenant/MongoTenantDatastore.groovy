package se.webinventions.mongomultitenant

import org.springframework.datastore.mapping.mongo.MongoDatastore
import org.springframework.datastore.mapping.model.PersistentEntity
import com.mongodb.Mongo
import org.springframework.datastore.mapping.document.config.DocumentMappingContext
import org.springframework.datastore.mapping.mongo.config.MongoCollection
import org.springframework.datastore.mapping.model.ClassMapping
import org.springframework.data.document.mongodb.MongoTemplate
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.apache.log4j.Logger
import com.mongodb.DBCollection
import org.springframework.dao.DataAccessException
import com.mongodb.MongoException
import com.mongodb.DB
import org.springframework.data.document.mongodb.DbCallback
import com.mongodb.WriteConcern
import org.springframework.datastore.mapping.model.DatastoreConfigurationException

import org.springframework.datastore.mapping.mongo.config.MongoMappingContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-03-07
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
class MongoTenantDatastore extends MongoDatastore {



  Logger log = Logger.getLogger(getClass())

  def config = ConfigurationHolder.getConfig();
  def tenantResolverProxy
  protected Map<PersistentEntity, MongoTemplate> mongoTemplates = new ConcurrentHashMap<PersistentEntity, MongoTemplate>();

/**
 * Constructor for creating a MongoDatastore using an existing Mongo instance. In this case
 * the connection details are only used to supply a USERNAME and PASSWORD
 *
 * @param mappingContext The MappingContext
 * @param mongo The existing Mongo instance
 */
  public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails, MongodbTenantResolver resolver) {
    super(mappingContext, mongo, connectionDetails)
    this.tenantResolverProxy = resolver


  }

  public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails, Object resolver) {
    super(mappingContext, mongo, connectionDetails)
    this.tenantResolverProxy = resolver

  }


  public MongoTenantDatastore(MongoMappingContext mappingContext,
                              Map<String, String> connectionDetails, MongodbTenantResolver resolver) {
    super(mappingContext, connectionDetails)
    this.tenantResolverProxy = resolver
  }

  public MongoTenantDatastore(MongoMappingContext mappingContext,
                              Map<String, String> connectionDetails, Object resolver) {
    super(mappingContext, connectionDetails)
    this.tenantResolverProxy = resolver
  }


  protected Boolean isTenantEntity(PersistentEntity entity) {
    if (foundInTenantIncludeList(entity) || notFoundInTenantExcludeListIfListExists(entity)) {
      return true
    }
    else {
      return false
    }

  }


  private Boolean notFoundInTenantExcludeListIfListExists(PersistentEntity entity) {

    def config = ConfigurationHolder.config
    def excludes = config?.grails?.mongo?.tenant?.excludingdomainclasses

    if (excludes instanceof List) {
      Boolean found = false;
      excludes.each {Class cls ->

        String entName = entity.getJavaClass().getName()
        String clsName = cls.getName()

        if (entName.equalsIgnoreCase(clsName)) {
          found = true;

        }

      }
      if (!found) {
        return true
      }
    } else {
      //if the list doesn't exist it is included per default
      return false;
    }
  }

  private Boolean foundInTenantIncludeList(PersistentEntity entity) {
    def includes = config?.grails?.mongo?.tenant?.includingdomainclasses

    if (includes instanceof List) {

      includes.each { Class cls ->

        String entName = entity.getJavaClass().getName()
             String clsName = cls.getName()

        if (entName.equalsIgnoreCase(clsName)) {
          return true
        }
      }

    } else {
      return false
    }
    return false;
  }

  /**
   * Creates tenant templates or normal templates based on whether the entity is maarked as a tenant in config.groovy
   * @param entity
   * @param mongoInstance
   */
  @Override
  protected void createMongoTemplate(PersistentEntity entity, Mongo mongoInstance) {
    DocumentMappingContext dc = (DocumentMappingContext) getMappingContext();
    String collectionName = entity.getDecapitalizedName();
    String databaseName = dc.getDefaultDatabaseName();
    ClassMapping<MongoCollection> mapping = entity.getMapping();
    final MongoCollection mongoCollection = mapping.getMappedForm() != null ? mapping.getMappedForm() : null;

    if (mongoCollection != null) {
      if (mongoCollection.getCollection() != null)
        collectionName = mongoCollection.getCollection();
      if (mongoCollection.getDatabase() != null)
        databaseName = mongoCollection.getDatabase();

    }

    //determine if the entity should be mapped as multitenant or as a normal non multitenant

    Object mt
    Boolean tenantTemplateCreated = false;
    if (foundInTenantIncludeList(entity)) {
      mt = createTenantTemplate(mongoInstance, databaseName, collectionName,mongoCollection,entity);
    } else if (notFoundInTenantExcludeListIfListExists(entity)) {
      mt = createTenantTemplate(mongoInstance, databaseName, collectionName,mongoCollection,entity);
    } else {
      log.info("mongo multitenant options not specified for class, no tenant action will be taken.. ")
    }

    if (!mt) {
      log.info("Class " + entity.getJavaClass().getName() + " is not a multitenant, assigning template as normal")
      mt = new MongoTemplate(mongoInstance, databaseName, collectionName);
    }

    else {
      tenantTemplateCreated = true;
      log.info("Class " + entity.getJavaClass().getName() + "is assigned as multitenant template in datastore!")

    }

    initializeTemplate(mt,mongoCollection, entity)

  //put it in normal list
    if(!mongoTemplates.containsKey(entity))
          mongoTemplates.put(entity, mt);

  }

  public void initializeTemplate(MongoTemplate mt, MongoCollection mongoCollection, PersistentEntity entity) {

    String username = connectionDetails?.get("username") ?: null
    String password = connectionDetails?.get("password") ?: null

    if (username != null && password != null) {
      mt.setUsername(username);
      mt.setPassword(password);
    }

    if (mongoCollection != null) {
      final WriteConcern writeConcern = mongoCollection.getWriteConcern();
      if (writeConcern != null) {
        mt.executeInSession(new DbCallback<Object>() {
          @Override
          public Object doInDB(DB db) throws MongoException,
              DataAccessException {

            if (writeConcern != null) {
              DBCollection collection = db.getCollection(mt.getDefaultCollectionName());
              collection.setWriteConcern(writeConcern);
            }
            return null;
          }
        });
      }

    }

    try {
      mt.afterPropertiesSet();
    } catch (Exception e) {
      log.warn("Failed to configure Mongo template, perhaps already initialized..  " + e.getMessage(), e);
    }




    initializeIndices(entity, mt);


  }

  protected MongoTenantTemplateDelegator createTenantTemplate(mongoInstance, databaseName, collectionName, MongoCollection mongoCollection, PersistentEntity entity) {
    return new MongoTenantTemplateDelegator(mongoInstance, databaseName,collectionName, tenantResolverProxy,this,mongoCollection,entity);
  }

  @Override
  public MongoTemplate getMongoTemplate(PersistentEntity entity) {
		return mongoTemplates.get(entity);
	}
}
