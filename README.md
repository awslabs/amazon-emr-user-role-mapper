## Amazon EMR User Role Mapper

This capability is intended to enable customers to use multi-tenant EMR clusters and provide their users from different organizations/departments to have segregated access to data in AWS. This application runs a proxy over the Instance Metadata Service available on EMR/EC2 hosts. The application determines the end user using the OS information for the connecting socket, and then invokes the mapping framework to map the user/group.

## Build

`mvn clean install`

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

