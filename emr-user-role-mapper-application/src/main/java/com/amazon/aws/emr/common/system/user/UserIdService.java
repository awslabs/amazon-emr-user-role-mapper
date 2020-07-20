// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.common.system.user;

import java.util.OptionalInt;

public interface UserIdService {
    OptionalInt resolveSystemUID(String localAddr, int localPort, String remoteAddr, int remotePort);
}
