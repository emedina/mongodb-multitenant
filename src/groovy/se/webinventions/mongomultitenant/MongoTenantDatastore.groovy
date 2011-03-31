package se.webinventions.mongomultitenant

import org.springframework.datastore.mapping.mongo.MongoDatastore
import static org.springframework.datastore.mapping.config.utils.ConfigUtils.read;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.data.document.mongodb.DbCallback;
import org.springframework.data.document.mongodb.MongoFactoryBean;
import org.springframework.data.document.mongodb.MongoTemplate;
import org.springframework.datastore.mapping.core.AbstractDatastore;
import org.springframework.datastore.mapping.core.Session;
import org.springframework.datastore.mapping.document.config.DocumentMappingContext;
import org.springframework.datastore.mapping.model.ClassMapping;
import org.springframework.datastore.mapping.model.DatastoreConfigurationException;
import org.springframework.datastore.mapping.model.MappingContext;
import org.springframework.datastore.mapping.model.PersistentEntity;
import org.springframework.datastore.mapping.model.PersistentProperty;
import org.springframework.datastore.mapping.model.PropertyMapping;
import org.springframework.datastore.mapping.mongo.config.MongoAttribute;
import org.springframework.datastore.mapping.mongo.config.MongoCollection;
import org.springframework.datastore.mapping.mongo.config.MongoMappingContext;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoOptions;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.apache.log4j.Logger
import org.springframework.datastore.mapping.mongo.MongoSession;

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-03-07
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
class MongoTenantDatastore extends MongoDatastore implements InitializingBean, MappingContext.Listener  {

  public static final String PASSWORD = "password";
  public static final String USERNAME = "username";
  public static final String MONGO_PORT = "port";
  public static final String MONGO_HOST = "host";

  protected Mongo mongo;
  protected MongoOptions mongoOptions = new MongoOptions();

  Logger log = Logger.getLogger(getClass())

  def config = ConfigurationHolder.getConfig();
  MongodbTenantResolver tenantResolverProxy
  protected Map<PersistentEntity, Object> mongoTemplates = new ConcurrentHashMap<PersistentEntity, Object>();
  protected Map<Object,Map<PersistentEntity, MongoTemplate>> mongoTenantTemplates = new ConcurrentHashMap<Object,Map<PersistentEntity, MongoTemplate>>();

/**
 * Constructor for creating a MongoDatastore using an existing Mongo instance. In this case
 * the connection details are only used to supply a USERNAME and PASSWORD
 *
 * @param mappingContext The MappingContext
 * @param mongo The existing Mongo instance
 */
 public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails, MongodbTenantResolver resolver) {
    this(mappingContext, mongo, connectionDetails)
    this.tenantResolverProxy = resolver


  }

  public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails, Object resolver) {
    this(mappingContext, mongo, connectionDetails)
    this.tenantResolverProxy = resolver

  }


  public MongoTenantDatastore(MongoMappingContext mappingContext,
                              Map<String, String> connectionDetails, MongodbTenantResolver resolver) {
    this(mappingContext, connectionDetails)
    this.tenantResolverProxy = resolver
  }

  public MongoTenantDatastore(MongoMappingContext mappingContext,
                              Map<String, String> connectionDetails, Object resolver) {
    this(mappingContext, connectionDetails)
    this.tenantResolverProxy = resolver
  }


	/**
	 * Constructs a MongoTenantDatastore using the default database name of "test" and defaults for the host and port.
	 * Typically used during testing.
	 */
	public MongoTenantDatastore() {
		this(new MongoMappingContext("test"), Collections.<String, String>emptyMap());
	}

	/**
	 * Constructs a MongoDatastore using the given MappingContext and connection details map.
	 *
	 * @param mappingContext The MongoMappingContext
	 * @param connectionDetails The connection details containing the {@link #MONGO_HOST} and {@link #MONGO_PORT} settings
	 */
	public MongoTenantDatastore(MongoMappingContext mappingContext,
			Map<String, String> connectionDetails, MongoOptions mongoOptions) {

		this(mappingContext, connectionDetails);
		if(mongoOptions != null)
			this.mongoOptions = mongoOptions;
	}

	/**
	 * Constructs a MongoDatastore using the given MappingContext and connection details map.
	 *
	 * @param mappingContext The MongoMappingContext
	 * @param connectionDetails The connection details containing the {@link #MONGO_HOST} and {@link #MONGO_PORT} settings
	 */
	public MongoTenantDatastore(MongoMappingContext mappingContext,
			Map<String, String> connectionDetails) {
		super(mappingContext, connectionDetails);

		if(mappingContext != null)
			mappingContext.addMappingContextListener(this);

        initializeConverters(mappingContext);
        mappingContext.getConverterRegistry().addConverter(new Converter<String, ObjectId>() {
			@Override
			public ObjectId convert(String source) {
				return new ObjectId(source);
			}
        });
        mappingContext.getConverterRegistry().addConverter(new Converter<ObjectId, String>() {
        	@Override
        	public String convert(ObjectId source) {
	        	return source.toString();
        	}
        });
	}

	public MongoTenantDatastore(MongoMappingContext mappingContext) {
		this(mappingContext, Collections.<String, String>emptyMap());
	}

	/**
	 * Constructor for creating a MongoDatastore using an existing Mongo instance
	 * @param mappingContext The MappingContext
	 * @param mongo The existing Mongo instance
	 */
	public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo) {
		this(mappingContext, Collections.<String, String>emptyMap());
		this.mongo = mongo;
	}

	/**
	 * Constructor for creating a MongoDatastore using an existing Mongo instance. In this case
	 * the connection details are only used to supply a USERNAME and PASSWORD
	 *
	 * @param mappingContext The MappingContext
	 * @param mongo The existing Mongo instance
	 */
	public MongoTenantDatastore(MongoMappingContext mappingContext, Mongo mongo, Map<String, String> connectionDetails) {
		this(mappingContext, connectionDetails);
		this.mongo = mongo;
	}


	public Mongo getMongo() {
		return mongo;
	}

	@Override
	protected Session createSession(Map<String, String> connectionDetails) {
		return new MongoSession(this, getMappingContext());
	}

	public void afterPropertiesSet() throws Exception {
		if(this.mongo == null) {
			ServerAddress defaults = new ServerAddress();
			MongoFactoryBean dbFactory = new MongoFactoryBean();
			dbFactory.setHost( read(String.class, MONGO_HOST, connectionDetails, defaults.getHost()) );
			dbFactory.setPort( read(Integer.class, MONGO_PORT, connectionDetails, defaults.getPort()) );
			if(mongoOptions != null ) {
				dbFactory.setMongoOptions(mongoOptions);
			}
			dbFactory.afterPropertiesSet();

			this.mongo = dbFactory.getObject();
		}



		for (PersistentEntity entity : mappingContext.getPersistentEntities()) {
			createMongoTemplate(entity, mongo);
		}
	}


    public void persistentEntityAdded(PersistentEntity entity) {
		createMongoTemplate(entity, this.mongo);
	}








  ///////////////////////////

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
      mt = createTenantTemplateDelegator(mongoInstance, databaseName, collectionName,mongoCollection,entity);
    } else if (notFoundInTenantExcludeListIfListExists(entity)) {
      mt = createTenantTemplateDelegator(mongoInstance, databaseName, collectionName,mongoCollection,entity);
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

  protected void createMongoTenantTemplate(PersistentEntity entity, Mongo mongoInstance) {
    DocumentMappingContext dc = (DocumentMappingContext) getMappingContext();
    String collectionName = tenantResolverProxy.getTenantCollectionName(entity.getDecapitalizedName());
    String databaseName = tenantResolverProxy.getTenantDatabaseName(dc.getDefaultDatabaseName());


    ClassMapping<MongoCollection> mapping = entity.getMapping();
    MongoCollection tenantMongoCollection = mapping.getMappedForm() != null ? mapping.getMappedForm() : null;



    if (tenantMongoCollection != null) {



      if (tenantMongoCollection.getCollection() != null) {
        collectionName = tenantResolverProxy.getTenantCollectionName(tenantMongoCollection.getCollection());

      }
      tenantMongoCollection.setCollection(collectionName)

      if (tenantMongoCollection.getDatabase() != null) {
        databaseName = tenantResolverProxy.getTenantDatabaseName(tenantMongoCollection.getDatabase());

      }
      tenantMongoCollection.setDatabase(databaseName)



    }

    def tenantid = tenantResolverProxy.getTenantId()
    MongoTemplate mt = new MongoTemplate(mongoInstance,databaseName, collectionName);

    //add it to the tenant lists
    if(!mongoTenantTemplates.containsKey(tenantid)) {
        mongoTenantTemplates.put(tenantid,new ConcurrentHashMap<PersistentEntity, MongoTemplate>() )

    }

    mongoTenantTemplates.get(tenantid).put(entity,mt);

    initializeTenantTemplate(mt,tenantMongoCollection,entity);

  }

  public void initializeTenantTemplate(Object mt, MongoCollection mongoCollection, PersistentEntity entity) {

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
       //FAILS WITH NULLPOINTER
       //Todo: Fails with nullpointer, find out why and fix... seems to work ok now anyhow without this
      // mt.afterPropertiesSet();
     } catch (Exception e) {
       log.warn("Failed to configure Mongo template, perhaps already initialized..  " + e.getMessage(), e);
     }




     initializeTenantIndices(entity, mt);


   }

    protected void initializeTenantIndices(final PersistentEntity entity, final Object template) {
        template.execute(new DbCallback<Object>() {
            public Object doInDB(DB db) throws MongoException, DataAccessException {
                final DBCollection collection = db.getCollection(template.getDefaultCollectionName());

                final ClassMapping<MongoCollection> classMapping = entity.getMapping();
                if(classMapping != null) {
                	final MongoCollection mappedForm = classMapping.getMappedForm();
                	if(mappedForm != null) {
                		for (Map compoundIndex : mappedForm.getCompoundIndices()) {
                			DBObject indexDef = new BasicDBObject(compoundIndex);
                			collection.ensureIndex(indexDef);
						}

                	}
                }

                for (PersistentProperty<MongoAttribute> property : entity.getPersistentProperties()) {
                    final boolean indexed = isIndexed(property);

                    if(indexed) {

                    	final MongoAttribute mongoAttributeMapping = property.getMapping().getMappedForm();

                        DBObject dbObject = new BasicDBObject();
                        final String fieldName = getMongoFieldNameForProperty(property);
						dbObject.put(fieldName,1);
                        DBObject options = new BasicDBObject();
						if(mongoAttributeMapping != null ) {
							final Map attributes = mongoAttributeMapping.getIndexAttributes();
							if(attributes != null) {
								if(attributes.containsKey(MongoAttribute.INDEX_TYPE)) {
									dbObject.put(fieldName, attributes.remove(MongoAttribute.INDEX_TYPE));
								}
								options.putAll(attributes);
							}
                        }
						if(options.toMap().isEmpty()) {
							collection.ensureIndex(dbObject);
						}
						else {
							collection.ensureIndex(dbObject, options);
						}
                    }
                }

                return null;
            }

			String getMongoFieldNameForProperty(
					PersistentProperty<MongoAttribute> property) {
		        PropertyMapping<MongoAttribute> pm = property.getMapping();
		        String propKey = null;
		        if(pm.getMappedForm()!=null) {
		            propKey = pm.getMappedForm().getField();
		        }
		        if(propKey == null) {
		            propKey = property.getName();
		        }
		        return propKey;
			}
        });
    }

  public void initializeTemplate(Object mt, MongoCollection mongoCollection, PersistentEntity entity) {

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

      /**
     * Indexes any properties that are mapped with index:true
     * @param entity The entity
     * @param template The template
     */
    protected void initializeIndices(final PersistentEntity entity, final Object template) {
        template.execute(new DbCallback<Object>() {
            public Object doInDB(DB db) throws MongoException, DataAccessException {
                final DBCollection collection = db.getCollection(template.getDefaultCollectionName());

                final ClassMapping<MongoCollection> classMapping = entity.getMapping();
                if(classMapping != null) {
                	final MongoCollection mappedForm = classMapping.getMappedForm();
                	if(mappedForm != null) {
                		for (Map compoundIndex : mappedForm.getCompoundIndices()) {
                			DBObject indexDef = new BasicDBObject(compoundIndex);
                			collection.ensureIndex(indexDef);
						}

                	}
                }

                for (PersistentProperty<MongoAttribute> property : entity.getPersistentProperties()) {
                    final boolean indexed = isIndexed(property);

                    if(indexed) {

                    	final MongoAttribute mongoAttributeMapping = property.getMapping().getMappedForm();

                        DBObject dbObject = new BasicDBObject();
                        final String fieldName = getMongoFieldNameForProperty(property);
						dbObject.put(fieldName,1);
                        DBObject options = new BasicDBObject();
						if(mongoAttributeMapping != null ) {
							final Map attributes = mongoAttributeMapping.getIndexAttributes();
							if(attributes != null) {
								if(attributes.containsKey(MongoAttribute.INDEX_TYPE)) {
									dbObject.put(fieldName, attributes.remove(MongoAttribute.INDEX_TYPE));
								}
								options.putAll(attributes);
							}
                        }
						if(options.toMap().isEmpty()) {
							collection.ensureIndex(dbObject);
						}
						else {
							collection.ensureIndex(dbObject, options);
						}
                    }
                }

                return null;
            }

			String getMongoFieldNameForProperty(
					PersistentProperty<MongoAttribute> property) {
		        PropertyMapping<MongoAttribute> pm = property.getMapping();
		        String propKey = null;
		        if(pm.getMappedForm()!=null) {
		            propKey = pm.getMappedForm().getField();
		        }
		        if(propKey == null) {
		            propKey = property.getName();
		        }
		        return propKey;
			}
        });
    }

  public getTenantDelegate(PersistentEntity entity,Mongo mongoInstance) {
    def currentTenant = tenantResolverProxy?.getTenantId();
    def delegateTo
        if(!mongoTenantTemplates?.containsKey(currentTenant) && tenantResolverProxy) {

          createMongoTenantTemplate(entity,mongoInstance)
          delegateTo = mongoTenantTemplates.get(currentTenant).get(entity);
        } else if(tenantResolverProxy) {
          if(mongoTenantTemplates.get(currentTenant).containsKey(entity))
            delegateTo = mongoTenantTemplates.get(currentTenant).get(entity);
          else {
            createMongoTenantTemplate(entity,mongoInstance)
            delegateTo = mongoTenantTemplates.get(currentTenant).get(entity);
          }
        }

      return delegateTo

  }

  protected MongoTenantTemplateDelegator createTenantTemplateDelegator(mongoInstance, databaseName, collectionName, MongoCollection mongoCollection, PersistentEntity entity) {
    return new MongoTenantTemplateDelegator(mongoInstance, databaseName,collectionName, this,mongoCollection,entity);
  }


  public MongoTemplate getMongoTemplate(PersistentEntity entity) {


    if(isTenantEntity(entity)) {
      return ensureAndGetTenantEntity(entity)
    } else {
      return mongoTemplates.get(entity)
    }

	}

  MongoTemplate ensureAndGetTenantEntity(PersistentEntity persistentEntity) {
      return getTenantDelegate(persistentEntity,this.getMongo()) as MongoTemplate;
  }
}
