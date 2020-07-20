// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.aws.emr.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PrincipalRoleMappings {
    @SerializedName("PrincipalRoleMappings")
    PrincipalRoleMapping[] principalRoleMappings;
}
