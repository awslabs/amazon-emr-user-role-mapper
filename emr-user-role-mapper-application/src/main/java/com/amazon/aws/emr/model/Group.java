// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Models a POSIX group.
 */
@AllArgsConstructor
@Builder
@Value
public class Group {

    String name;
    Integer gid;
    List<String> users;

    public static Group createFromGroupEntry(String line) {
        String[] items = line.split(":");

        if (items.length < 3) {
            throw new IllegalArgumentException("Need at least 3 items from file and there's only: " + items.length);
        }

        String name = items[0];
        int gid = Integer.parseInt(items[2]);
        // Some groups may not have any members
        List<String> users = (items.length == 4) ? Arrays.stream(items[3].split(",")).collect(Collectors.toList()) :
                new ArrayList<>();
        return new Group(name, gid, users);
    }
}
