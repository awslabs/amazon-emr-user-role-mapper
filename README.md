## Amazon EMR User Role Mapper

This capability is intended to enable customers to use multi-tenant EMR clusters and provide their users from different organizations/departments to have segregated access to data in AWS. This application runs a proxy over the Instance Metadata Service available on EMR/EC2 hosts. The application determines the end user using the OS information for the connecting socket, and then invokes the mapping framework to map the user/group.

## Build

```
git clone https://github.com/awslabs/amazon-emr-user-role-mapper.git
cd amazon-emr-user-role-mapper 
mvn clean install
```

## Installation

```
cd emr-user-role-mapper-application/usr
cp ../target/emr-user-role-mapper-application-1.0-jar-with-dependencies-and-exclude-classes.jar install/
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

Set shell variables for S3 Bucket and folder within that bucket where the install artifacts would be copied. Like -
```
export BUCKET=…
export S3FOLDER=…
aws s3 cp --recursive install s3://$BUCKET/$S3FOLDER
```

Launch the EMR cluster. It’s strongly recommended to use an EMR configuration that enables Kerberos on the cluster. 

```
aws emr create-cluster --release-label emr-5.29.0 --instance-type m3.xlarge --applications Name=Hive Name=Zeppelin Name=Livy --instance-count 2 --ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,KeyName=shkala-dev-iad --service-role EMR_DefaultRole   --name "URM cluster"   --kerberos-attributes Realm=EC2.INTERNAL,KdcAdminPassword=<>  --region us-east-1 --bootstrap-actions Name='Install URM',Path=s3://$BUCKET/$S3FOLDER/ba-script.sh,Args=[$BUCKET,$S3FOLDER] --security-configuration <>
```

ssh to the cluster and verify URM is running

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
2020-09-11 19:07:40   17969053 emr-user-role-mapper-application-1.0-jar-with-dependencies-and-exclude-classes.jar
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
- To just run unit tests run `mvn test`

### Managed policy union mapper
- The default mapper allows the mapping to specify different AWS Role, policies etc.
- The managed policy mapper uses a single AWS Role (specified in config), and the mappings
just contain a list of Managed Policies.
- The actual permissions are the union of Managed Policies provided they are allowed by the AWS Role.
- Changes in user-role-mapper.properties

```
rolemapper.class.name=com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl
rolemapper.role.arn=arn:aws:iam::<ACC_ID>:role/<BASE_ROLE>
```
- Format of JSON file

```
{
"PrincipalPolicyMappings": [
  {
    "username": "test-user1",
    "policies": ["arn:aws:iam::176430881729:policy/test-sk-p1"]
  },
  {
    "username": "test-gp1",
    "policies": ["arn:aws:iam::176430881729:policy/test-sk-p2"]
  }
]
}
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

