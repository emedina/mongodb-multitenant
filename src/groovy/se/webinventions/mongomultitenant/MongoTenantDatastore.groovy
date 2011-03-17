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
import java.util.concurrent.ConcurrentHashMap

import org.springframework.datastore.mapping.mongo.config.MongoMappingContext
import org.springframework.datastore.mapping.model.MappingContext
import org.springframework.datastore.mapping.engine.EntityInterceptor

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-03-07
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
class MongoTenantDatastore extends MongoDatastore {


  Logger log = Logger.getLogger(getClass())


  //override the private field in the super class. and take care that we override all methods that use this field also!
  protected Map<PersistentEntity, MongoTemplate> mongoTemplates = new ConcurrentHashMap<PersistentEntity, MongoTemplate>();

  //tenant id maps to the hashmap used for the persistent entity
  protected Map<Object, Map<PersistentEntity, MongoTemplate>> mongoTenantTemplates = new ConcurrentHashMap<Object, ConcurrentHashMap<PersistentEntity, MongoTemplate>>();



  //map different mappingcontexts to different tenants and also interceptors to different tenant objects.

    protected Map<Object,MappingContext> mappingContextTenants = new ConcurrentHashMap<Object,MappingContext>();
    protected Map<Object,List<EntityInterceptor>> interceptorsTenants = new ConcurrentHashMap<Object,List<EntityInterceptor>>()




  def config = ConfigurationHolder.getConfig();
  def tenantResolverProxy

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

  /**
   * We override this method only because we need to override the private field in the superclass
   * @param entity
   * @return
   */
  @Override
  public MongoTemplate getMongoTemplate(PersistentEntity entity) {

    if (isTenantEntity(entity)) {
      def tenantId = tenantResolverProxy.getTenantId()

      //if the tenant exist in the hashmap we return it, if it doesn't we have to create a new template
      //(lazy init) for this tenant and this persistent entity
      HashMap<PersistentEntity,MongoTemplate> tenantHashMap

      if (mongoTenantTemplates.containsKey(tenantId)) {
        tenantHashMap = mongoTenantTemplates.get(tenantId);
        //now we also need to check weather the persisten entity is in the acual hashmap.
        if (tenantHashMap?.containsKey(entity)) {
          return tenantHashMap.get(entity)
        } else {
          createMongoTemplate(entity,getMongo())    //also creates the hashmap
          tenantHashMap = mongoTenantTemplates.get(tenantId);
          return tenantHashMap.get(entity)
        }
      } else {
        //create the hashmap and template for this entity
        createMongoTemplate(entity,getMongo())
        tenantHashMap = mongoTenantTemplates.get(tenantId);
        return tenantHashMap.get(entity)
      }

    } else {
      return mongoTemplates.get(entity);
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

    MongoTemplate mt
    Boolean tenantTemplateCreated = false;
    if (foundInTenantIncludeList(entity)) {
      mt = createTenantTemplate(mongoInstance, databaseName, collectionName);
    } else if (notFoundInTenantExcludeListIfListExists(entity)) {
      mt = createTenantTemplate(mongoInstance, databaseName, collectionName);
    } else {
      log.warn("mongo multitenant options not specified, no tenant action will be taken.. ")
    }

    if (!mt) {
      log.info("Class " + entity.getJavaClass().getName() + " is not a multitenant, assigning template as normal")
      mt = new MongoTemplate(mongoInstance, databaseName, collectionName);
    }

    else {
      tenantTemplateCreated = true;
      log.info("Class " + entity.getJavaClass().getName() + "is assigned as multitenant template in datastore!")

    }

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
      throw new DatastoreConfigurationException("Failed to configure Mongo template for entity [" + entity + "]: " + e.getMessage(), e);
    }

    if (tenantTemplateCreated) {

      def tenantId = tenantResolverProxy?.getTenantId()


      def tenantHashMap

      //check if the hashmap exists for this tenant otherwise create it
      //lazy initialization
      if (!mongoTenantTemplates.containsKey(tenantId)) {
        tenantHashMap = new ConcurrentHashMap<PersistentEntity, MongoTemplate>()
        mongoTenantTemplates.put(tenantId,tenantHashMap);
      } else {
        tenantHashMap = mongoTenantTemplates?.get(tenantId)
      }

      tenantHashMap.put(entity, mt);

    } else {

      //put it in normal list
      mongoTemplates.put(entity, mt);

    }

    initializeIndices(entity, mt);

  }

  protected MongoTemplate createTenantTemplate(mongoInstance, databaseName, collectionName) {
    return new MongoTemplate(mongoInstance, tenantResolverProxy.getTenantDatabase(databaseName), tenantResolverProxy.getTenantCollection(collectionName));
  }

}
