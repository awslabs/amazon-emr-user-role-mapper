// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.user;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.OptionalInt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@Slf4j
public class LinuxUserIdServiceTest {

    private static final int LOCAL_SERVER_PORT = 9901;

    private UserIdService userIdService;

    @Before
    public void setup() throws Exception {
        Path tcp = null;
        Path tcp6 = null;
        try {
            java.net.URL url = getClass().getResource("/tcp");
            tcp = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't get tcp file");
        }
        try {
            java.net.URL url = this.getClass().getResource("/tcp6");
            tcp6 = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Can't get tcp6 file");
        }
        userIdService = new LinuxUserIdService(
                tcp.toString(),
                tcp6.toString());
    }

    @Test
    public void resolveUID_tcp_IMDS() {
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "172.30.6.181",
                39844, true);
        assertThat(uid.getAsInt(), is(509));
        uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "172.30.6.181",
                39860, true);
        assertThat(uid.getAsInt(), is(504));
        uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "172.30.6.181",
                39842, true);
        assertThat(uid.getAsInt(), is(506));
    }

    @Test
    public void resolveUID_tcp6_IMDS() throws NoSuchFieldException, IllegalAccessException {
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "172.30.6.181",
                42086, true);
        assertThat(uid.getAsInt(), is(485));
    }

    @Test
    public void resolveUID_unmatched_IMDS() throws NoSuchFieldException, IllegalAccessException {
        int nonExistingPort = 999999;
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "127.0.0.1",
                nonExistingPort, true);
        assertThat(uid.isPresent(), is(false));
    }

    @Test
    public void resolveUID_tcp_nonIMDS() {
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "127.0.0.1",
                42088, false);
        assertThat(uid.getAsInt(), is(510));
    }

    @Test
    public void resolveUID_tcp6_nonIMDS() {
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "127.0.0.1",
                42089, false);
        assertThat(uid.getAsInt(), is(470));
        uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "127.0.0.1",
                42090, false);
        assertThat(uid.getAsInt(), is(470));
    }

    @Test
    public void resolveUID_unmatched_nonIMDS() throws NoSuchFieldException, IllegalAccessException {
        int nonExistingPort = 999999;
        OptionalInt uid = userIdService.resolveSystemUID(
                "127.0.0.1",
                LOCAL_SERVER_PORT,
                "127.0.0.1",
                nonExistingPort, false);
        assertThat(uid.isPresent(), is(false));
    }
}
