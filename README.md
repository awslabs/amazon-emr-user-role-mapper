## Amazon EMR User Role Mapper

This capability is intended to enable customers to use multi-tenant EMR clusters and provide their users from different organizations/departments to have segregated access to data in AWS. This application runs a proxy over the Instance Metadata Service available on EMR/EC2 hosts. The application determines the end user using the OS information for the connecting socket, and then invokes the mapping framework to map the user/group.

## Build

```
git clone https://github.com/awslabs/amazon-emr-user-role-mapper.git
cd amazon-emr-user-role-mapper 
mvn clean install
```

## Installation

#### Package and copy artifacts to S3
```
cd emr-user-role-mapper-application/usr
cp ../target/emr-user-role-mapper-application-1.1.0-jar-with-dependencies-and-exclude-classes.jar install/
```

Edit the properties file in install/user-role-mapper.properties. 
 
```
rolemapper.s3.key=mappings.json`
rolemapper.s3.bucket=my-urm-bucket`
```

Here is a sample format of the JSON.

```
{
  "PrincipalRoleMappings": [
    {
      "username": "user1",
      "rolearn": "arn:aws:iam::123456789012:role/test-role1"
    },
   
    {
      "groupname": "group1",
      "rolearn": "arn:aws:iam::123456789012:role/test-role2",
      "duration": 1800
    }
  ]
}
```

#### Installing EMR User Role Mapper on EMR

Set shell variables for S3 Bucket and folder within that bucket where the install artifacts would be copied. Like -
```
export BUCKET=…
export S3FOLDER=…
aws s3 cp --recursive install s3://$BUCKET/$S3FOLDER
```

If launching on an EMR cluster, it’s strongly recommended to use an EMR configuration that enables Kerberos on the cluster. 

```
aws emr create-cluster --release-label emr-5.29.0 --instance-type m3.xlarge --applications Name=Hive Name=Zeppelin Name=Livy --instance-count 2 --ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,KeyName=shkala-dev-iad --service-role EMR_DefaultRole   --name "URM cluster"   --kerberos-attributes Realm=EC2.INTERNAL,KdcAdminPassword=<>  --region us-east-1 --bootstrap-actions Name='Install URM',Path=s3://$BUCKET/$S3FOLDER/ba-script.sh,Args=[$BUCKET,$S3FOLDER] --security-configuration <>
```

#### Launching on an EC2 instance

You can provide the following into the user data of the instance, or run them on the instance manually:

```
#!/bin/bash
export BUCKET=<ENTER BUCKET HERE>
export S3FOLDER=<ENTER FOLDER HERE>
sudo yum install -y java-1.8.0-openjdk
aws s3 cp s3://$BUCKET/$S3FOLDER/ba-script.sh /tmp/
chmod +x /tmp/ba-script.sh
/tmp/ba-script.sh $BUCKET $S3FOLDER
```

ssh to the cluster and verify URM is running

#### Validating the Installation

If launching on EMR release 5.29 or later, or EC2 running Amazon Linux 2, run:

```
[hadoop@ip-172-31-35-38 ~]$ sudo systemctl status emr-user-role-mapper
● emr-user-role-mapper.service - EMR process to map users to AWS IAM Roles.
   Loaded: loaded (/etc/systemd/system/emr-user-role-mapper.service; enabled; vendor preset: disabled)
   Active: active (running) since Thu 2020-12-17 19:09:50 UTC; 6min ago
  Process: 7284 ExecStart=/usr/bin/emr-user-role-mapper (code=exited, status=0/SUCCESS)
 Main PID: 7293 (java)
```

If launching on EMR release 5.28 or earlier (EC2 using Amazon Linux 1 is not supported) run:

```
sudo initctl status emr-user-role-mapper
emr-user-role-mapper start/running, process 5403
```

### Test URM
- Test AWS CLI with mapped and unmapped user

```		
sudo -u unmapped-user aws s3 ls s3://my-custom-mapper/install
Unable to locate credentials. You can configure credentials by running "aws configure".
		
sudo -u mapped-user aws s3 ls s3://my-custom-mapper/install

2020-09-11 19:07:41       8521 ba-script.sh
2020-09-11 19:07:40   17969053 emr-user-role-mapper-application-1.1.0-jar-with-dependencies-and-exclude-classes.jar
2020-09-11 19:07:41       3280 emr-user-role-mapper.conf
2020-09-11 19:07:41        607 log4j.properties
2020-09-11 19:07:41        254 user-role-mapper.properties
```

### Integration tests
- Set the AWS credentials to be available to the environment before running the tests.
- Like here is one way to do it

```
export AWS_ACCESS_KEY_ID="ASIASSFAZTPATNBHFC56"
export AWS_SECRET_ACCESS_KEY="XXXXXXXXX"
export AWS_SESSION_TOKEN="YYYYYYYYY"
```
- The tests run only on OSX and Unix style OS. They skip on other OS.
- The invoking OS user is used in the tests.
- The tests create/fetch IAM Roles, Policies, S3 buckets. The caller credentials should have
permissions to perform these operations.
- The tests run in lexicographic order, and should be run in a single thread.
- To just run unit tests run `mvn test`

### Managed policy union mapper
The default mapper allows the mapping to specify different IAM Role, policies etc. However, this can be limiting as a user may match multiple policies. This role mapping implementation allows for matching IAM policies instead of IAM roles. When the list of policies are identified for a user based on the user’s name and groups, it uses those policies to scope down the role specified in the property 'rolemapper.role.arn' in the user-role-mapper.properties. 

For more information on assume-role and scoping down credentials, see [AssumeRole](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html), specifically the PolicyArns section and the Session Polices at [Access Policies](https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies.html).

To use this mapper implementation, follow these two steps:

* Step 1: Changed in user-role-mapper.properties to use the ManagedPolicyBasedUserRoleMapperImpl implementation and specify the base role to assume:

```
rolemapper.class.name=com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl
rolemapper.role.arn=arn:aws:iam::<ACC_ID>:role/<BASE_ROLE>
```


**Note 1:** The IAM Role that is specified needs to have permissions to all the actions and resources that are contained in all of the policies. If the role doesn't have permissions that is contained in a policy, then the policy will NOT give the role permissions. For example, if your role doesn't have permissions to access s3://mybucket/myprefix/ and there is a policy that provides s3:GetObject permissions to s3://mybucket/myprefix/*, access to any objects in the prefix will fail with access denied.

**Note 2:** IAM imposes a limit of up to 10 policies that can be attached to an IAM role. Mappings should not exceed this limit for a single user, otherwise URM will fail to assume a role for the user.


* Step 2: Add your mappings to mapping.json file

```
{
"PrincipalPolicyMappings": [
  {
    "username": "user1",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_X>"]
  },
  {
    "username": "user2",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_W>"]
  },
  {
    "groupname": "group1",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_Y>"]
  },
  {
    "groupname": "group2",
    "policies": ["arn:aws:iam::<ACC-ID>:policy/<POLICY_Z>"]
  }
]
}
```

#### Example: 

Suppose that user *user1* belongs to the *group1* group. When [assume-role](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html) is called to assume the role specified in 'rolemapper.role.arn', it will pass the policy ARN's for policies <POLICY_X> and <POLICY_Y>, granting the user permissions provided by <POLICY_X> and <POLICY_Y>.

### URM Custom Credentials provider

URM works by looking at who is the owner of socket connection of calling user and granting credentials for the user. In some cases, this authentication may not be sufficent as execution engines may execute user queries as the engines user. For example, the Hive user can execute queries as others. For this scenario, using the EMR User Role Mapper Credentials provider may be able to support these use cases as the provider will see who the impersonated user is, and get credentials for that particular user. The URM credentials provide depends on the use of [UserGroupInformation](https://hadoop.apache.org/docs/r1.2.1/Secure_Impersonation.html) from the Hadoop ecosystem and is required by the execution engine.  

To get more information, including installation instructions, see URM Credentials Provider [README](emr-user-role-mapper-credentials-provider/README.md) for more information.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## Limitations

URM does not work with tcp6 and IMDSv6 currently. Please do not use URM on installations that use tcp6 traffic along with IMDSv6.

## License

This project is licensed under the Apache-2.0 License.

