/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.relayer.dal.entities;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("relayer_node")
public class RelayerNodeEntity extends BaseEntity {
    @TableField("node_id")
    private String nodeId;

    @TableField("node_crosschain_cert")
    private String nodeCrossChainCert;

    @TableField("node_sig_algo")
    private String nodeSigAlgo;

    @TableField("domains")
    private String domains;

    @TableField("endpoints")
    private String endpoints;

    @TableField("blockchain_content")
    private String blockchainContent;

    @TableField("properties")
    private byte[] properties;
}
