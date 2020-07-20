// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class GroupTest {

    @Test
    public void valid_creation() {
        String line = "g1:x:508:u2,u4";
        Group g = Group.createFromGroupEntry(line);
        assertThat(g, is(Group.builder().
                name("g1")
                .gid(508)
                .users(Arrays.asList("u2", "u4")).build()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid_creation() {
        String line = "g1:x";
        Group.createFromGroupEntry(line);
    }
}
