#!/bin/bash -l

USER_ROLE_MAPPER_PORT=9944

WHITELIST_EC2_MD_IPTABLE_RULE_COMMENT="(added by userrolemapper) whitelist ec2 metadata service for user:"
BLACKLIST_EC2_MD_IPTABLE_RULE_COMMENT="(added by userrolemapper) redirect ec2 metadata requests of LF users to interceptor"

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
# ACCEPT     tcp  --  anywhere             instance-data.ec2.internal  tcp dpt:http owner UID match hadoop
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

    insert_iptable_rule_to_whitelist_privileged_user hadoop

    # whitelist userrolemapper as role mapper is the proxy to EC2 metadata service
    insert_iptable_rule_to_whitelist_privileged_user userrolemapper
}


function start() {

    set -x

    PID_FILE=/emr/userrolemapper/run/userrolemapper.pid
    EMRUSERROLEMAPPER_HOME=/usr/share/aws/emr/user-role-mapper
    CLASSPATH="${EMRUSERROLEMAPPER_HOME}/lib/*:${EMRUSERROLEMAPPER_HOME}/conf/"
    CONF="${EMRUSERROLEMAPPER_HOME}/conf/rolemapper.properties"

    # Create cgroup with fixed resources for running EMR Secret Agent
    sudo service cgconfig start
    sudo cgcreate -g memory,cpu:userrolemapper-group
    sudo cgset -r memory.limit_in_bytes=1024M userrolemapper-group
    sudo cgset -r memory.memsw.limit_in_bytes=768M userrolemapper-group
    sudo cgset -r cpu.rt_runtime_us=2000000 userrolemapper-group
    sudo cgset -r cpu.rt_period_us=5000000 userrolemapper-group

    # Launch java process under emrsecretagent user
    LAUNCH_CMD='/usr/bin/java -Xmx512m -Xms300m -XX:OnOutOfMemoryError="kill -9 %p" \
        -XX:MinHeapFreeRatio=10 -cp '$CLASSPATH' -Dlog4j.configuration=file:'${EMRUSERROLEMAPPER_HOME}/conf'/log4j.properties com.amazon.emr.UserRoleMappingServer &'

    sudo -u userrolemapper -H sh -c "$LAUNCH_CMD"

    # Test to see PID=$! works. Keeping it here for logging purpose.
    PID=$!
    echo 'Result from PID=$!: '$PID

    # Get the PID and assign cgroup to the newly launched java process. Command below excludes the process that runs grep itself.
    # PID=$! doesn't work so manually grep PID from ps.
    userrolemapper_pid=$(ps -eo uname:20,pid,cmd | grep "RoleMappingServer.*[/]usr/bin/java.*emr.RoleMappingServer" | awk '{print $2}')
    echo "Mapper process id is $userrolemapper_pid"

    sudo cgclassify -g cpu,memory:userrolemapper-group $userrolemapper_pid

    echo $userrolemapper_pid > $PID_FILE
    sleep 5

    setup_iptable_rules
}


if [ $# -eq 0 ]; then
    start
else
 sudo   kill -9 $1
