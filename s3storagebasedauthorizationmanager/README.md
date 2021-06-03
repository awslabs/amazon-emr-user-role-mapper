**S3 storage based authorization manager for Hive Metastore service.**

This is a plug in intended for Hive metastore service for its authorization manager.
Referring to the below link for more info regarding storage based authorization manager for Hive metastore:
https://cwiki.apache.org/confluence/display/Hive/Storage+Based+Authorization+in+the+Metastore+Server

**Build**

git clone https://github.com/awslabs/amazon-emr-user-role-mapper.git
cd s3storagebasedauthorizationmanager
mvn clean install

**Install**

copy the artifact jar over to path "/lib/hive/auxlib".  
Follow the aformentioned hive link for specific hive-site.xml config settings.  
Change the hive.security.metastore.authorization.manager value in /etc/hive/conf/hive-site.xml to:  
  org.sfdc.uip.hive.ql.security.authorization.S3StorageBasedAuthorizationProvider  

Add hive.security.metastore.authenticator.manager property to hive-site.xml and its value to:  
  org.apache.hadoop.hive.ql.security.HadoopDefaultMetastoreAuthenticator.  

then restart hive metastore service and hive server2 service

**Special notes for Trino**
Trino(presto sql) currently is able to do impersonation when interacting with HMS when you set hive.metastore.thrift.impersonation.enabled=true for file
/etc/presto/conf/catalog/hive.properties
This will enable the plugin to work for trino as well.
However in order to fully enable all the WRITE operations, we also need to update the following configs to true:
hive.allow-drop-table, hive.allow-rename-table, hive.allow-add-column, hive.allow-drop-column and hive.allow-rename-column

**License**
This project is licensed under the Apache-2.0 License


