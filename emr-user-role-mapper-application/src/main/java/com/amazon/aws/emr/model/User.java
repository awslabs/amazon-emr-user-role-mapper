// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Model for a user.
 */
@AllArgsConstructor
@Builder
@Value
@Slf4j
public class User {
    String name;
    Integer uid;
    Integer gid;
    String comment;
    String home;
    String shell;

    public static User createFromPasswdEntry(String line) {
        String[] items = line.split(":");

        if (items.length != 7) {
            log.error("Incorrect number of fields found in passwd file {}. Please check man 5 passwd", items.length);
            throw new IllegalArgumentException("Need 7 items from file and there's only: " + items.length);
        }

        String name = items[0];
        // items[1] is encrypted password. x character indicates that encrypted password is stored in /etc/shadow file.
        int uid = Integer.parseInt(items[2]);
        int gid = Integer.parseInt(items[3]);
        String comment = items[4];
        String home = items[5];
        String shell = items[6];

        return new User(name, uid, gid, comment, home, shell);
    }
}
