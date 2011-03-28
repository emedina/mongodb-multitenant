package se.webinventions.mongomultitenant

import org.springframework.data.document.mongodb.MongoTemplate
import com.mongodb.Mongo
import com.mongodb.DBCollection
import org.apache.log4j.Logger

import java.util.concurrent.ConcurrentHashMap
import org.springframework.datastore.mapping.mongo.config.MongoCollection
import org.springframework.datastore.mapping.model.PersistentEntity
import com.mongodb.CommandResult
import com.mongodb.DBObject
import org.springframework.data.document.mongodb.DbCallback
import org.springframework.data.document.mongodb.CollectionCallback
import org.springframework.data.document.mongodb.CollectionOptions
import org.springframework.data.document.mongodb.MongoReader
import org.springframework.data.document.mongodb.query.IndexDefinition
import org.springframework.data.document.mongodb.query.Query
import org.springframework.data.document.mongodb.CursorPreparer
import org.springframework.data.document.mongodb.MongoWriter
import com.mongodb.WriteResult
import org.springframework.data.document.mongodb.query.Update

/**
 * Created by IntelliJ IDEA.
 * This class acts as a wrapper around tenant templates. It keeps track of which tenant 'template' should be called/used based on
 * a tenant service. It intercepts all method calls to the class and delegates to the correct instance. should it not exist
 * it is created on the fly (lazy) , we extend the MongoTemplate just having to avoid to implement all MongoOperations interface methods
 * if the interface changes in the future.
 */
class MongoTenantTemplateDelegator extends MongoTemplate {


  Logger log = Logger.getLogger(MongoTenantTemplateDelegator.class)
  MongodbTenantResolver tenantResolverProxy
  private Map<Object, MongoTemplate> tenantTemplates = new ConcurrentHashMap<Object, MongoTemplate>();

  String originalDatabaseName, originalCollectionName
  Mongo originalMongoInstance
  MongoTenantDatastore creatorStore
  PersistentEntity peUponCreate
  MongoCollection collectionUpconCreate;


//Todo: Make it work with groovy interceptable instead.. only seems to be called when method is invoked directly on class and not on
  //super class methods. :/

/*  @Override
  def invokeMethod(String name, args) {

    if(log) {
      log.debug "Method $name invoked"
    }

    //delegate to the correct template for the tenant
    def currentTenant = tenantResolverProxy?.getTenantId();
    def delegateTo
    if(!tenantTemplates?.containsKey(currentTenant) && tenantResolverProxy) {
      tenantTemplates.put(currentTenant,new MongoTemplate(originalMongoInstance,tenantResolverProxy.getTenantDatabaseName(originalDatabaseName),tenantResolverProxy.getTenantCollectionName(originalCollectionName)))
      delegateTo = tenantTemplates.get(currentTenant);
      creatorStore.initializeTemplate(delegateTo,collectionUpconCreate,peUponCreate);

    } else if(tenantResolverProxy) {
      delegateTo = tenantTemplates.get(currentTenant);
    }



    if(delegateTo) {
      def originalMethod = MongoTemplate.metaClass.getMetaMethod(name, args)
      return originalMethod?.invoke(delegateTo, args)
    } else {
      log?.warn("could not delegate to tenant");
      //check if we are calling constructor of this object

      def method = MongoTenantTemplateDelegator.metaClass.getMetaMethod(name,args);
      return method?.invoke(this,args);

    }

  }
  */

  public MongoTenantTemplateDelegator(Mongo mongoInstance, String databaseName,
                                      String collectionName,MongodbTenantResolver tenantResolverProxy,
                                      MongoTenantDatastore store,MongoCollection collUponCreate,PersistentEntity peUponCreate) {


      super(mongoInstance,databaseName,collectionName);

      this.collectionUpconCreate = collUponCreate
      this.peUponCreate = peUponCreate
      this.originalCollectionName = collectionName
      this.originalDatabaseName = databaseName; //saved just for reference what it was from the beginning, used to calculate tenant database name
      this.originalMongoInstance = mongoInstance
      this.creatorStore = store;

      this.tenantResolverProxy = tenantResolverProxy;
      def tenantid = tenantResolverProxy.getTenantId()
      if(!tenantTemplates.containsKey(tenantid)) {
        tenantTemplates.put(tenantid,new MongoTemplate(mongoInstance,tenantResolverProxy.getTenantDatabaseName(databaseName),tenantResolverProxy.getTenantCollectionName(collectionName)));
        def mt = tenantTemplates.get(tenantid);
        if(mt) {
          creatorStore.initializeTemplate(mt,collectionUpconCreate,peUponCreate);
        } else
        {log?.warn("Could not initialize tenant template upon creation")}

      }


  }

  private MongoTemplate getDelagate() {


    def currentTenant = tenantResolverProxy?.getTenantId();
    def delegateTo
        if(!tenantTemplates?.containsKey(currentTenant) && tenantResolverProxy) {
          tenantTemplates.put(currentTenant,new MongoTemplate(originalMongoInstance,tenantResolverProxy.getTenantDatabaseName(originalDatabaseName),tenantResolverProxy.getTenantCollectionName(originalCollectionName)))
          delegateTo = tenantTemplates.get(currentTenant);
          creatorStore.initializeTemplate(delegateTo,collectionUpconCreate,peUponCreate);

        } else if(tenantResolverProxy) {
          delegateTo = tenantTemplates.get(currentTenant);
        }

      return delegateTo
  }




  /**************** OVERRIDING DEFENITIONS *****************************/





  void afterPropertiesSet() {
    getDelagate()?.afterPropertiesSet()
  }

  String getDefaultCollectionName() {
    return getDelagate()?.getDefaultCollectionName()
  }

  DBCollection getDefaultCollection() {
     return getDelagate()?.getDefaultCollection()
  }

  CommandResult executeCommand(String s) {
     return getDelagate()?.executeCommand(s)
  }

  CommandResult executeCommand(DBObject dbObject) {
     return getDelagate()?.executeCommand(dbObject)
  }

  def <T> T execute(DbCallback<T> tDbCallback) {
     return getDelagate()?.execute(tDbCallback)
  }

  def <T> T execute(CollectionCallback<T> tCollectionCallback) {
     return getDelagate()?.execute(tCollectionCallback)
  }

  def <T> T execute(String s, CollectionCallback<T> tCollectionCallback) {
     return getDelagate()?.execute(s,tCollectionCallback)
  }

  def <T> T executeInSession(DbCallback<T> tDbCallback) {
     return getDelagate()?.executeInSession(tDbCallback)
  }

  DBCollection createCollection(String s) {
     return getDelagate()?.createCollection(s)
  }

  DBCollection createCollection(String s, CollectionOptions collectionOptions) {
     return getDelagate()?.createCollection(s,collectionOptions)
  }

  Set<String> getCollectionNames() {
     return getDelagate()?.getCollectionNames()
  }

  DBCollection getCollection(String s) {
     return getDelagate()?.getCollection(s)
  }

  boolean collectionExists(String s) {
     return getDelagate()?.collectionExists(s)
  }

  void dropCollection(String s) {
     getDelagate()?.dropCollection(s)
  }

  def <T> List<T> getCollection(Class<T> tClass) {
     return getDelagate()?.getCollection(tClass)
  }

  def <T> List<T> getCollection(String s, Class<T> tClass) {
     return getDelagate()?.getCollection(s,tClass)
  }

  def <T> List<T> getCollection(String s, Class<T> tClass, MongoReader<T> tMongoReader) {
     return getDelagate()?.getCollection(s,tClass,tMongoReader)
  }

  void ensureIndex(IndexDefinition indexDefinition) {
      getDelagate()?.ensureIndex(indexDefinition)
  }

  void ensureIndex(String s, IndexDefinition indexDefinition) {
    getDelegate().ensureIndex(s,indexDefinition)
  }

  def <T> T findOne(Query query, Class<T> tClass) {
    return getDelagate()?.findOne(query,tClass)
  }

  def <T> T findOne(Query query, Class<T> tClass, MongoReader<T> tMongoReader) {
    return getDelagate()?.findOne(query,tClass,tMongoReader)
  }

  def <T> T findOne(String s, Query query, Class<T> tClass) {
    return getDelagate()?.findOne(s,query,tClass)
  }

  def <T> T findOne(String s, Query query, Class<T> tClass, MongoReader<T> tMongoReader) {
    return getDelagate()?.findOne(s,query,tClass,tMongoReader)
  }

  def <T> List<T> find(Query query, Class<T> tClass) {
    return getDelagate()?.find(query,tClass)
  }

  def <T> List<T> find(Query query, Class<T> tClass, MongoReader<T> tMongoReader) {
    return getDelagate()?.find(query,tClass,tMongoReader)
  }

  def <T> List<T> find(String s, Query query, Class<T> tClass) {
    return getDelagate()?.find(s,query,tClass)
  }

  def <T> List<T> find(String s, Query query, Class<T> tClass, MongoReader<T> tMongoReader) {
    return getDelagate()?.find(s,query,tClass,tMongoReader)
  }

  def <T> List<T> find(String s, Query query, Class<T> tClass, CursorPreparer cursorPreparer) {
    return getDelagate()?.find(s,query,tClass,cursorPreparer)
  }

  void insert(Object o) {
     getDelagate()?.insert(o)
  }

  void insert(String s, Object o) {
     getDelagate()?.insert(s,o)
  }

  def <T> void insert(T t, MongoWriter<T> tMongoWriter) {
      getDelagate()?.insert(t,tMongoWriter)
  }

  def <T> void insert(String s, T t, MongoWriter<T> tMongoWriter) {
     getDelagate()?.insert(s,t,tMongoWriter)
  }

  void insertList(List<? extends Object> objects) {
     getDelagate()?.insertList(objects)
  }

  void insertList(String s, List<? extends Object> objects) {
     getDelagate()?.insertList(s,objects)
  }

  def <T> void insertList(List<? extends T> ts, MongoWriter<T> tMongoWriter) {
     getDelagate()?.insertList(ts,tMongoWriter)
  }

  def <T> void insertList(String s, List<? extends T> ts, MongoWriter<T> tMongoWriter) {
    getDelagate()?.insertList(s,ts,tMongoWriter)
  }

  void save(Object o) {
    getDelagate()?.save(o)
  }

  void save(String s, Object o) {
    getDelagate()?.save(s,o)
  }

  def <T> void save(T t, MongoWriter<T> tMongoWriter) {
    getDelagate()?.save(t,tMongoWriter)
  }

  def <T> void save(String s, T t, MongoWriter<T> tMongoWriter) {
    getDelagate()?.save(s,t,tMongoWriter)
  }

  WriteResult updateFirst(Query query, Update update) {
    return getDelagate()?.updateFirst(query,update)
  }

  WriteResult updateFirst(String s, Query query, Update update) {
    return getDelagate()?.updateFirst(s,query,update)
  }

  WriteResult updateMulti(Query query, Update update) {
    return getDelagate()?.updateMulti(query,update)
  }

  WriteResult updateMulti(String s, Query query, Update update) {
    return getDelagate()?.updateMulti(s,query,update)
  }

  void remove(Query query) {
     getDelagate()?.remove(query)
  }

  void remove(String s, Query query) {
    getDelagate()?.remove(s,query)
  }
}
