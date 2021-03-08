**S3 storage based authorization manager for Hive Metastore service.**

This is a plug in intended for Hive metastore service for its authorization manager.
Referring to the below link for more info regarding storage based authorization manager for Hive metastore:
https://cwiki.apache.org/confluence/display/Hive/Storage+Based+Authorization+in+the+Metastore+Server

**Build**

git clone https://github.com/shi-wen/s3HMSAuthorizationManager.git
cd s3storagebasedauthorizationmanager
mvn clean install

**Install**
copy the artifact jar over to path "/lib/hive/auxlib".
Follow the aformentioned hive link for specific hive-site.xml config settings. change the hive.security.metastore.authorization.manager value to be
org.sfdc.uip.hive.ql.security.authorization.S3StorageBasedAuthorizationProvider
then restart hive metastore service and hive server2 service

**License**
This project is licensed under the Apache-2.0 License


