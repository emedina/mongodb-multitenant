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
class MongoTenantTemplateDelegator implements GroovyInterceptable  {


  Logger log = Logger.getLogger(MongoTenantTemplateDelegator.class)

  String originalDatabaseName, originalCollectionName
  Mongo originalMongoInstance
  MongoTenantDatastore creatorStore
  PersistentEntity peUponCreate
  MongoCollection collectionUpconCreate;


//Todo: Make it work with groovy interceptable instead.. only seems to be called when method is invoked directly on class and not on
  //super class methods. :/

  @Override
  def invokeMethod(String name, args) {

    if(log) {
      log.debug "Method $name invoked"
    }

    //delegate to the correct template for the tenant

    def delegateTo = creatorStore.getTenantDelegate(peUponCreate,originalMongoInstance);




    if(delegateTo) {
      def originalMethod = MongoTemplate.metaClass.getMetaMethod(name, args)
      return originalMethod?.invoke(delegateTo, args)
    } else {
      log?.warn("could not delegate to tenant");
      //check if we are calling constructor of this object

    }

  }


  public MongoTenantTemplateDelegator(Mongo mongoInstance, String databaseName,
                                      String collectionName,
                                      MongoTenantDatastore store,MongoCollection collUponCreate,PersistentEntity peUponCreate) {



      this.collectionUpconCreate = collUponCreate
      this.peUponCreate = peUponCreate
      this.originalCollectionName = collectionName
      this.originalDatabaseName = databaseName; //saved just for reference what it was from the beginning, used to calculate tenant database name
      this.originalMongoInstance = mongoInstance
      this.creatorStore = store;






  }





}
