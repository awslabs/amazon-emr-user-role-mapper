// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common;

import com.amazon.aws.emr.mapping.DefaultUserRoleMapperImpl;

import com.amazon.aws.emr.mapping.ManagedPolicyBasedUserRoleMapperImpl;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Class to hold constants
 */
final public class Constants {
    public static final int ROLE_MAPPING_DEFAULT_MAX_THREADS = 30;
    public static final int ROLE_MAPPING_DEFAULT_MIN_THREADS = 10;
    public static final int ROLE_MAPPING_DEFAULT_IDLE_TIMEOUT_MS = 300 * 1000; // 5 min
    public static final int JETTY_PORT = 9944;

    /**
     * Class name for mapper class.
     */
    public static final String ROLE_MAPPER_CLASS = "rolemapper.class.name";
    /**
     * S3 Bucket for the role mapping file.
     */
    public static final String ROLE_MAPPING_S3_BUCKET = "rolemapper.s3.bucket";
    /**
     * AWS Role to be used for role mapping. This is used in {@link ManagedPolicyBasedUserRoleMapperImpl}
     */
    public static final String ROLE_MAPPING_ROLE_ARN = "rolemapper.role.arn";
    /**
     * S3 Bucket for the role mapping file.
     */
    public static final String ROLE_MAPPING_MAX_THREADS = "rolemapper.max.threads";
    /**
     * S3 Bucket for the role mapping file.
     */
    public static final String ROLE_MAPPING_MIN_THREADS = "rolemapper.min.threads";
    /**
     * Key for the role mapping file.
     */
    public static final String ROLE_MAPPING_S3_KEY = "rolemapper.s3.key";

    /**
     * Duration in mins to check for new mapping.
     */
    public static final String ROLE_MAPPPING_REFRESH_INTERVAL_MIN = "rolemapper.refresh.interval.minutes";

    public static final String ROLE_MAPPPING_DEFAULT_REFRESH_INTERVAL_MIN = "5";

    public static final int ROLE_MAPPING_MIN_REFRESH_INTERVAL_MIN = 1;

    /**
     * Default S3 Mapper Impl for JSON format.
     */
    public static final String ROLE_MAPPING_DEFAULT_CLASSNAME = DefaultUserRoleMapperImpl.class.getName();

    /**
     * Default S3 Mapper Impl for JSON format.
     */
    public static final String ROLE_MAPPING_MANAGED_POLICY_CLASSNAME = ManagedPolicyBasedUserRoleMapperImpl
        .class.getName();

    /**
     * Constants related with joda DateTime and JSON
     */
    // This format is to match the result from EC2 metadata service call
    public static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SS'Z'";
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN).withZone(ZoneOffset.UTC);

    /**
     * Strategy for resolving principal i.e. user or group. Set the below to "command" if default strategy is not
     * working
     */
    public static final String PRINCIPAL_RESOLVER_STRATEGY_KEY = "principal.resolver.strategy";

    // Default Principal resolver implementation using native system calls
    public static final String DEFAULT_PRINCIPAL_RESOLVER_STRATEGY = "native";

    public static final String IMPERSONATION_ALLOWED_USERS = "rolemapper.impersonation.allowed.users";

    private Constants() {
    }

    /**
     * Networking related constants
     */
    public static class Network {
        /**
         * Hex value of 127.0.0.1 in /proc/net/tcp file in reverse byte order
         */
        public static final String IPV4_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER = "0100007F";

        /**
         * Hex value of ::1 in /proc/net/tcp6 file in reverse byte order
         */
        public static final String IPV6_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER = "00000000000000000000000001000000";

        /**
         * Hex value of IPv4 127.0.0.1 in /proc/net/tcp6 file in reverse byte order
         */
        public static final String IPV4_MAPPED_IPV6_LOCALHOST_ADDR_IN_HEX_REVERSED_BYTE_ORDER = "0000000000000000FFFF00000100007F";

        /**
         * Hex value of 160 in /proc/net/tcp file in reverse byte order
         */
        public static final String IPV4_IMDS_ADDR_IN_HEX_REVERSED_BYTE_ORDER = "FEA9FEA9";

        /**
         * Hex value of 160 in /proc/net/tcp file in reverse byte order
         */
        public static final String IPV6_IMDS_ADDR_IN_HEX_REVERSED_BYTE_ORDER = "0000000000000000FFFF0000FEA9FEA9";

        /**
         * /proc/net/tcp filepath. Make it result of function call so compiler does not inline the value.
         * This way we can override during unit testing.
         */
        public static final String MODULE_PROC_NET_TCP_PATH = String.valueOf("/proc/net/tcp");

        /**
         * /proc/net/tcp6 filepath. Make it result of function call so compiler does not inline the value.
         * This way we can override during unit testing.
         */
        public static final String MODULE_PROC_NET_TCP6_PATH = String.valueOf("/proc/net/tcp6");
    }

    /**
     * Linux related constants
     */
    public static class Linux {

        // /proc related constants.
        public static class Proc {
            // Connection state used in /proc/net/tcp and /proc/net/tcp6 files.
            public static final int TCP_STATE_ESTABLISHED = 1;
        }
    }
}

