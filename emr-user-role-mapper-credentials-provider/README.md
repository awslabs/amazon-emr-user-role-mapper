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

### General Instructions

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

* IMPORTANT: Presto does not support impersonation when interacting with Metadata services like Glue Data Catalog or Hive Metastore. Trino does and support for it is coming.

### Using Glue Data Catalog as your meta store.

If using Glue Data Catalog, there must be a single policy in mappings.json to allow the presto user to access Glue Data Catalog.  

For example:

```json
{
"PrincipalPolicyMappings": [
  {
    "username": "presto",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_X>"]
  }
}
```

where POLICY_X is a policy that has access to Glue Data Catalog and s3:ListBucket for any s3 buckets that you wish Presto to access.

Note: As of PrestoDB 0.232, it does not perform the necessary impersonation in rare cases which is why s3:ListBucket permissions is needed. 

### Using Hive Metastore

Presto does not support impersonation when interacting with Hive Metastore. For this reason, the presto user will need to have a mapping to a role that has access to S3. 

```json
{
"PrincipalPolicyMappings": [
  {
    "username": "hive",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_X>"]
  }
}

```

where POLICY_X is a policy that has access to Glue Data Catalog and s3:ListBucket for any s3 buckets that you wish Presto to access.

Also, if you are using StorageBasedAuthorizer as the authorization layer for Hive Metastore, you will need to provide S3 privileges to the presto user as well. This is so that the authorizer can access S3 during it's authorization checks.

## Hive setup

* "rolemapper.impersonation.allowed.users" property must include "hive" user in your user-role-mapper.properties file to allow hive to impersonate other users.
