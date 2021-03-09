# Amazon EMR
# 
# Copyright 2020, Amazon.com, Inc. or its affiliates. All Rights Reserved.
# 
# Licensed under the Amazon Software License (the "License").
# You may not use this file except in compliance with the License.
# A copy of the License is located at
# 
#   http://aws.amazon.com/asl/
#
# or in the "license" file accompanying this file. This file is distributed
# on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
# express or implied. See the License for the specific language governing
# permissions and limitations under the License.

if [[ $# -lt 2 ]]; then
  echo "Two parameters are required to call this script. Usage: "
  echo "$0 <S3 Bucket of URM Artifacts> <S3 Path of URM Artifacts> "
  exit -1
fi

BUCKET=$1
S3_PATH=$2

USER_ROLE_MAPPER_PORT=9944

WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT="(added by userrolemapper) whitelist ec2 metadata service for user:"
BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT="(added by userrolemapper) redirect ec2 metadata requests of LF users to interceptor"

if pgrep -f instance-controller >/dev/null ; then
  echo "Configuring URM on EMR Cluster"
  sudo_user="hadoop"
else
  echo "Configuring URM on Vanilla EC2 Host"
  sudo_user="ec2-user"
fi

# remove all rules that match the pattern
# this function will search for a rule that matches the given pattern and delete it, then repeat this until no more rules are found
function remove_matching_rules() {
    while true
    do
        # find the rule number of a rule that matches the input pattern
        rule_num=$(find_the_rule_number_for_pattern "$1")

        # if rule number is less or equal than zero, it means all matched rules are removed, we can stop
        if [ $rule_num -le 0 ]; then
            break
        fi

        # delete the rule using its rule number
        rule_text=$(sudo iptables -t nat -L OUTPUT $rule_num)
        echo "Deleting iptable rule $rule_num:"
        echo "    $rule_text"
        sudo iptables -t nat -D OUTPUT $rule_num
    done
}

# search (from bottom to top) for a rule that matches a given pattern, return its rule number
# if the returned rule number > 0, it means a rule is found; otherwise the rule is not found
#
# e.g. the current nat table's OUTPUT chain looks like below:
# Chain OUTPUT (policy ACCEPT)
# target     prot opt source               destination
# ACCEPT     tcp  --  anywhere             server-13-249-37-5.iad89.r.cloudfront.net(aws.amazon.com)  tcp dpt:http owner UID match knox
# ACCEPT     tcp  --  anywhere             instance-data.ec2.internal  tcp dpt:http owner UID match knox
# ACCEPT     tcp  --  anywhere             instance-data.ec2.internal  tcp dpt:http owner UID match emrsecretagent
#
# input: knox, output: 2
# input: secret, output: 3
# input: ghost, output: -2
function find_the_rule_number_for_pattern() {

    # list all the rules;
    # prefix matching lines with line number;
    # keep only line number;
    # keep only last line of the result
    rule_line_num=$(sudo iptables -L OUTPUT -t nat | grep -n "$1" | awk '{split($0, a, ":"); print a[1]}' | tail -n 1)

    # first 2 lines are header, so rule number = rule line number - 2
    echo $(($rule_line_num - 2))
}

# WHITELIST RULES MUST BE PLACED BEFORE THE BLACKLIST RULE
# insert an iptable rule to grant a user the access to EC2 metadata service
# first, check if such a user exists; if not, wait till it's created
# second, remove all duplicates of this rule in the chain
# last, insert this rule to the top of the chain
# Removing before inserting each rule is to guarantee the order of the inserted rules
function insert_iptable_rule_to_whitelist_privileged_user() {

    # we use while loop to check if a user exists before we can create a rule for him
    # TODO: for knox user, keep retrying until it is created. Improve this via https://i.amazon.com/EMRLAKEFORMATION-110
    while true; do
      if ! id -u $1 > /dev/null; then
        echo "User $1 is still not created yet, will sleep for 2s"
        sleep 2;
      else
        echo "Found user $1."
        break
      fi
    done

    # remove the existing rules with the same comment
    remove_matching_rules "$WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT$1"

    echo "Adding iptable rule to accept EC2 metadata service requests from $1"

    # insert rules at the top will push existing rules towards the end of the chain
    # this will allow us first block all users from calling EC2 metadata service
    # then whitelist privileged users one by one
    sudo iptables -t nat -I OUTPUT 1 -p tcp -d 169.254.169.254 --dport 80 -m owner --uid-owner $1 -j ACCEPT \
        -m comment --comment "$WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT$1"

    # verify the rule is inserted into the table
    sudo iptables -L OUTPUT -t nat | grep -q "$WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT$1"
    if [ $? -eq 0 ]; then
      rule_num=$(find_the_rule_number_for_pattern "$WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT$1")
      rule_text=$(sudo iptables -t nat -L OUTPUT $rule_num)
      echo "Successfully added iptable rule $rule_num:"
      echo "    $rule_text"
    else
      echo "Failed to create iptable rule for $1."
      exit 92
    fi
}

# insert an iptable rule to redirect all users' EC2 metadata service requests to interceptor
# first, remove all duplicates of this rule in the chain
# then, insert this rule to the top of the chain
# Removing before inserting each rule is to guarantee the order of the inserted rules
function insert_iptable_rule_to_intercept_unprivileged_users() {

    # remove the existing rules with the same comment
    remove_matching_rules "$BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT"

    echo "Adding the iptable rule to intercept EC2 metadata service requests from unprivileged users..."

    # insert rules at the top will push existing rules towards the end of the chain
    # this will allow us first block all users from calling EC2 metadata service
    # then whitelist privileged users one by one
    sudo iptables -t nat -I OUTPUT 1 -p tcp -d 169.254.169.254 --dport 80 -j DNAT --to-destination 127.0.0.1:$USER_ROLE_MAPPER_PORT\
        -m comment --comment "$BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT"

    # verify the rule is inserted into the table
    sudo iptables -L OUTPUT -t nat | grep -q "$BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT"
    if [ $? -eq 0 ]; then
      rule_num=$(find_the_rule_number_for_pattern "$BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT")
      rule_text=$(sudo iptables -t nat -L OUTPUT $rule_num)
      echo "Successfully added iptable rule $rule_num:"
      echo "    $rule_text"
    else
      echo "Failed to create iptable rule for unprivileged users."
      exit 93
    fi
}


function setup_iptable_rules() {

    # add rules in a stack fashion: first rule will be placed at the end, last at the top
    insert_iptable_rule_to_intercept_unprivileged_users

    # grant root user the access to EC2 metadata
    insert_iptable_rule_to_whitelist_privileged_user root

    # whitelist sudo user (ec2-user/hadoop) to access EC2 metadata
    # For EMR Cluster, IC is run by sudo user hadoop, IC constructor needs to call EC2 metadata
    insert_iptable_rule_to_whitelist_privileged_user $sudo_user

    # whitelist emrsecretagent as secret agent is the proxy to EC2 metadata service
    insert_iptable_rule_to_whitelist_privileged_user userrolemapper
}

echo "Creating user and dirs"
sudo useradd userrolemapper
sudo mkdir -p /var/run/emr-user-role-mapper/
sudo mkdir -p /usr/share/aws/emr/user-role-mapper/lib
mkdir -p /emr/user-role-mapper/{run,log,conf}

echo "Getting artifacts from S3"
echo "Getting log4j.properties from S3"
sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/log4j.properties /emr/user-role-mapper/conf/
echo "Getting user-role-mapper.properties from S3"
sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/user-role-mapper.properties /emr/user-role-mapper/conf/
echo "Getting and setting mappings.json"
sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/mappings.json /emr/user-role-mapper/conf/
sudo sed -i "s#\$AWS_ROLE#${ROLE_ARN}#g" /emr/user-role-mapper/conf/mappings.json
echo "Getting emr-user-role-mapper-application-1.1.0-jar-with-dependencies-and-exclude-classes.jar from S3"
sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/emr-user-role-mapper-application-1.1.0-jar-with-dependencies-and-exclude-classes.jar /usr/share/aws/emr/user-role-mapper/lib/

echo "Setting permissions"
sudo chown -R userrolemapper:$sudo_user /emr/user-role-mapper
sudo chmod -R 750 /emr/user-role-mapper/conf/*

# Check if we are on AL2
which systemctl 2>/dev/null
if [[ $? -eq 0 ]]; then
    echo "Getting emr-user-role-mapper.service from S3"
    sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/al2/emr-user-role-mapper.service /etc/systemd/system/
    sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/al2/emr-user-role-mapper /usr/bin/
    sudo chmod 750 /usr/bin/emr-user-role-mapper
    sudo chown userrolemapper:$sudo_user /usr/bin/emr-user-role-mapper
    echo "Reload config"
    sudo systemctl enable emr-user-role-mapper.service
    echo "Start the service"
    sudo systemctl start emr-user-role-mapper
    sudo systemctl status emr-user-role-mapper
else
    echo "Getting emr-user-role-mapper.conf from S3"
    sudo aws s3 cp s3://${BUCKET}/${S3_PATH}/al1/emr-user-role-mapper.conf /etc/init/
    echo "Reload config"
    sudo initctl reload-configuration
    echo "Start the service"
    sudo initctl start emr-user-role-mapper
    sudo status emr-user-role-mapper
fi

setup_iptable_rules
echo "Done!"
