// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UserTest {

    @Test
    public void valid_format_succeeds() {
        String line = "u4:x:506:507::/home/u4:/bin/bash";
        User user = User.createFromPasswdEntry(line);
        assertThat(user, is(User.builder().
                name("u4")
                .uid(506)
                .gid(507)
                .comment("")
                .home("/home/u4")
                .shell("/bin/bash")
                .build()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid_creation_throws() {
        String line = "u1:x";
        User.createFromPasswdEntry(line);
    }
}
