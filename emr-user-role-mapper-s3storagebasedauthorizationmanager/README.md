# S3 storage based authorization manager for Hive Metastore service.

This is a plug in intended for Hive Metastore Service (HMS) for its authorization manager. Referring to the below link for more info regarding storage based authorization manager for Hive Metastore Service: https://cwiki.apache.org/confluence/display/Hive/Storage+Based+Authorization+in+the+Metastore+Server. This plugin can be used within Hive Metastore Server to conduct authorization for metadata requests, such as reading and updating tables. It works by impersonating the end user, and making requests to S3 using the end users permissions. If its successful, it will allow the operation. Else, it will reject the operation. 

## Build

Change *hive.version* in pom.xml for the version of hive that you will be using. It should work with Hive 3.x, however, if Hive 2.x is needed, then some code changes may be necessary. 

```
git clone https://github.com/awslabs/amazon-emr-user-role-mapper.git
cd s3storagebasedauthorizationmanager
mvn clean install
```

## Install

copy the artifact jar over to path "/lib/hive/auxlib".  
Follow the aformentioned hive link for specific hive-site.xml config settings.  
Change the hive.security.metastore.authorization.manager value in /etc/hive/conf/hive-site.xml to:  

```
  com.amazonaws.emr.urm.hive.urmstoragebasedauthorizer.S3StorageBasedAuthorizationProvider  
```

Add hive.security.metastore.authenticator.manager property to hive-site.xml and its value to:  
```
  org.apache.hadoop.hive.ql.security.HadoopDefaultMetastoreAuthenticator.  
```

then restart hive metastore service and hive server2 service

## Special notes for PrestoSQL/Trino

Trino(PrestoSql) currently is able to do impersonation when interacting with HMS when you set hive.metastore.thrift.impersonation.enabled=true for file
/etc/presto/conf/catalog/hive.properties. This will enable the plugin to work for Trino as well. However, in order to fully enable all the WRITE operations, we also need to update the following configs to true:
hive.allow-drop-table, hive.allow-rename-table, hive.allow-add-column, hive.allow-drop-column and hive.allow-rename-column
