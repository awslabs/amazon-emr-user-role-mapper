# URM Credentials Provider

This module is necessary if you are going to use URM with Apache PrestoDB/SQL and/or Hive (using impersonation). This credentials provider works by looking at who the current user is using Hadoops UserGroupInformation and retrieves credentials for that user. 

# Setup instructions

## Pre-requistites

- Your cluster must be kerberized and URM must be installed according to the instructions for URM.

## Instructions
Run:
```sh
mvn clean install
```

and copy target/urm-credentials-provider-0.x-SNAPSHOT-with-dependencies.jar to a location in S3.

Then create a Bootstrap action that does the following:

```bash
sudo aws s3 cp s3://<location in s3 of jar above> /usr/share/aws/emr/emrfs/auxlib/ 
```

## EMR Cluster setup

Set the following configuration:

```json
   {
       "Classification":"emrfs-site",
       "Properties":{
          "fs.s3.customAWSCredentialsProvider":"com.amazonaws.emr.urm.credentialsprovider.URMCredentialsProviderChain"
       },
       "Configurations":[
       ]
   }
```

By default, the hive and presto users will use this credentials provider, and everyone else will use the existing DefaultCredentialsProviderChain. If you wish to change this list, add the "urm.credentialsprovider.impersonation.users" property to emrfs-site.xml, like below:

```json
   {
       "Classification":"emrfs-site",
       "Properties":{
          "fs.s3.customAWSCredentialsProvider":"com.amazonaws.emr.urm.credentialsprovider.URMCredentialsProviderChain",
          "urm.credentialsprovider.impersonation.users":"hive,presto,admin"
       },
       "Configurations":[
       ]
   }
```

## Presto setup

* Set the following configuration:

```json
   {
       "Classification":"presto-connector-hive",
       "Properties":{
          "hive.hdfs.impersonation.enabled":"true"
       },
       "Configurations":[
       ]
   }
```

* "rolemapper.impersonation.allowed.users" property must include "presto" user in your user-role-mapper.properties file to allow presto to impersonate other users.

* When your cluster comes up, ensure that the jar is available as a symlink in /usr/lib/presto/plugin/hive-hadoop2/
```bash
ls -lrt /usr/lib/presto/plugin/hive-hadoop2/urm-credentials-provider*.jar
lrwxrwxrwx 1 root root 73 Dec 12 02:24 /usr/lib/presto/plugin/hive-hadoop2/urm-credentials-provider-0.1-SNAPSHOT.jar -> /usr/share/aws/emr/emrfs/auxlib/urm-credentials-provider-0.1-SNAPSHOT.jar
```
* If using Glue Data Catalog, there must be a single policy in mappings.json to allow the presto user to access Glue Data Catalog. Presto does not support impersonation when interacting with Metadata services like Glue Data Catalog or Hive Metastore. 

## Hive setup

* "rolemapper.impersonation.allowed.users" property must include "hive" user in your user-role-mapper.properties file to allow hive to impersonate other users.
