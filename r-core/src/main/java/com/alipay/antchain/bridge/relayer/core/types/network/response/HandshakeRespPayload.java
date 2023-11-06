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

package com.alipay.antchain.bridge.relayer.core.types.network.response;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandshakeRespPayload implements IResponsePayload {
    public static HandshakeRespPayload decodeFromJson(String json) {
        return JSON.parseObject(json, HandshakeRespPayload.class);
    }

    @JSONField(name = "network_id")
    private String remoteNetworkId;

    @JSONField(name = "remote_node_info")
    private String remoteNodeInfo;

    @Override
    public String encode() {
        return JSON.toJSONString(this);
    }
}
