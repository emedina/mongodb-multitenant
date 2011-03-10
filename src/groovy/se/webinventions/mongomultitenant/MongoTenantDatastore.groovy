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
  protected Map<Object, Map<PersistentEntity, MongoTemplate>> mongoTenantTemplates = new ConcurrentHashMap<Object, ConcurrentHashMap<PersistentEntity, MongoTemplate>>();

  def config = ConfigurationHolder.getConfig();
  MongodbTenantResolver tenantResolver = SpringUtil.getBean("tenantResolver");


  protected Boolean isTenantEntity(PersistentEntity entity) {
    if (foundInTenantIncludeList(entity) || !foundInTenantExcludeList(entity)) {
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

    if(isTenantEntity(entity)) {
      def tenantId = tenantResolver.getTenantId()
      def tenantHashMap = mongoTenantTemplates.get(tenantId);
      return tenantHashMap?.get(entity);
    } else {
      return mongoTemplates.get(entity);
    }

  }


  private Boolean foundInTenantExcludeList(PersistentEntity entity) {

    def config = ConfigurationHolder.config
    List<Class> excludes = config?.grails?.mongo?.tenant?.excludingdomainclasses

    if (excludes) {
      Boolean found = false;
      excludes.each { cls ->
        if (cls.isInstance(entity)) {
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
    List includes = config?.grails?.mongo?.tenant?.includingdomainclasses

    if (includes instanceof List) {

      includes.each{ cls->
        if (cls.isInstance(entity)) {
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
    } else if (foundInTenantExcludeList(entity)) {
      mt = createTenantTemplate(mongoInstance, databaseName, collectionName);
    } else {
      log.warn("mongo multitenant options not specified, no tenant action will be taken.. ")
    }

    if (!mt) {
      log.info("Class " + entity.class.getName() + " is not a multitenant, assigning template as normal")
      mt = new MongoTemplate(mongoInstance, databaseName, collectionName);
    }

    else {
      tenantTemplateCreated = true;
      log.info("Class " + entity.class.getName() + "is assigned as multitenant template in datastore!")

    }

    String username = read(String.class, USERNAME, connectionDetails, null);
    String password = read(String.class, PASSWORD, connectionDetails, null);

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

      def tenantId = tenantResolver?.getTenantId()

      def tenantHashMap = mongoTenantTemplates.get(tenantId)

      //lazy initialization
      if (!tenantHashMap) {
        tenantHashMap = new ConcurrentHashMap<PersistentEntity, MongoTemplate>()
        tenantHashMap.put(entity, mt);
        mongoTenantTemplates.put(tenantId, tenantHashMap);
      } else {
        tenantHashMap.put(entity, mt);
      }

    } else {

      //put it in normal list
      mongoTemplates.put(entity, mt);

    }

    initializeIndices(entity, mt);

  }

  protected MongoTemplate createTenantTemplate(mongoInstance, databaseName, collectionName) {
    return new MongoTemplate(mongoInstance, tenantResolver.getTenantDatabase(databaseName), tenantResolver.getTenantCollection(collectionName));
  }

}
