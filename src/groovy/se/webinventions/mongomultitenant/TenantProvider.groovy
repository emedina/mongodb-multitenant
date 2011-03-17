package se.webinventions.mongomultitenant

import org.bson.types.ObjectId

/**
 * Created by IntelliJ IDEA.
 * User: per
 * Date: 2011-03-10
 * Time: 09:44
 * To change this template use File | Settings | File Templates.
 */
public interface TenantProvider {


  Object getId()
  String getCollectionNameSuffix()
  void setCollectionNameSuffix(String collectionName)
  String getDatabaseNameSuffix()
  void setDatabaseNameSuffix(String databaseName)
  void setName(String name)
  String getName()

}