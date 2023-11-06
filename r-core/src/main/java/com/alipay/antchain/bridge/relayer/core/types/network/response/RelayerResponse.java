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

import java.security.PublicKey;
import java.security.Signature;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.RelayerCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.ObjectIdentityUtil;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVTypeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVUtils;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVField;
import com.alipay.antchain.bridge.relayer.commons.model.RelayerNodeInfo;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerNetworkManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Relaye请求响应
 */
@Getter
@Setter
@Slf4j
public class RelayerResponse {

    public static final int SUCCESS = 0;

    public static final int FAILED = -1;

    public static final short TLV_TYPE_RELAYER_RESPONSE_CODE = 0;

    public static final short TLV_TYPE_RELAYER_RESPONSE_MSG = 1;

    public static final short TLV_TYPE_RELAYER_RESPONSE_PAYLOAD = 2;

    public static final short TLV_TYPE_RELAYER_RESPONSE_REMOTE_RELAYER_CERT = 3;

    public static final short TLV_TYPE_RELAYER_RESPONSE_REMOTE_SIG_ALGO = 4;

    public static final short TLV_TYPE_RELAYER_RESPONSE_SIG = 5;

    public static RelayerResponse createSuccessResponse(
            IResponsePayload payload,
            IRelayerNetworkManager relayerNetworkManager
    ) {
        return createResponse(
                SUCCESS,
                "",
                payload,
                relayerNetworkManager
        );
    }

    public static RelayerResponse createFailureResponse(
            String errorMsg,
            IResponsePayload payload,
            IRelayerNetworkManager relayerNetworkManager
    ) {
        return createResponse(
                FAILED,
                errorMsg,
                payload,
                relayerNetworkManager
        );
    }

    public static RelayerResponse createFailureResponse(
            String errorMsg,
            IRelayerNetworkManager relayerNetworkManager
    ) {
        return createResponse(
                FAILED,
                errorMsg,
                null,
                relayerNetworkManager
        );
    }

    public static RelayerResponse createResponse(
            int errorCode,
            String message,
            IResponsePayload payload,
            IRelayerNetworkManager relayerNetworkManager
    ) {
        RelayerResponse relayerResponse = new RelayerResponse();
        relayerResponse.setResponseCode(errorCode);
        relayerResponse.setResponseMessage(message);
        relayerResponse.setResponsePayload(ObjectUtil.isNull(payload) ? "" : payload.encode());
        relayerNetworkManager.signRelayerResponse(relayerResponse);

        return relayerResponse;
    }

    public static RelayerResponse decode(byte[] rawData) {
        return TLVUtils.decode(rawData, RelayerResponse.class);
    }

    @TLVField(tag = TLV_TYPE_RELAYER_RESPONSE_CODE, type = TLVTypeEnum.UINT8)
    private int responseCode;

    @TLVField(tag = TLV_TYPE_RELAYER_RESPONSE_MSG, type = TLVTypeEnum.STRING, order = TLV_TYPE_RELAYER_RESPONSE_MSG)
    private String responseMessage;

    @TLVField(tag = TLV_TYPE_RELAYER_RESPONSE_PAYLOAD, type = TLVTypeEnum.STRING, order = TLV_TYPE_RELAYER_RESPONSE_PAYLOAD)
    private String responsePayload;

    @TLVField(
            tag = TLV_TYPE_RELAYER_RESPONSE_REMOTE_RELAYER_CERT,
            type = TLVTypeEnum.BYTES,
            order = TLV_TYPE_RELAYER_RESPONSE_REMOTE_RELAYER_CERT
    )
    private AbstractCrossChainCertificate remoteRelayerCertificate;

    @TLVField(
            tag = TLV_TYPE_RELAYER_RESPONSE_REMOTE_SIG_ALGO,
            type = TLVTypeEnum.STRING,
            order = TLV_TYPE_RELAYER_RESPONSE_REMOTE_SIG_ALGO
    )
    private String sigAlgo;

    @TLVField(
            tag = TLV_TYPE_RELAYER_RESPONSE_SIG,
            type = TLVTypeEnum.BYTES,
            order = TLV_TYPE_RELAYER_RESPONSE_SIG
    )
    private byte[] signature;

    public byte[] rawEncode() {
        return TLVUtils.encode(this, TLV_TYPE_RELAYER_RESPONSE_SIG);
    }

    public byte[] encode() {
        return TLVUtils.encode(this);
    }

    /**
     * 验签
     *
     * @return
     */
    public boolean verify() {

        try {
            RelayerCredentialSubject relayerCredentialSubject = RelayerCredentialSubject.decode(
                    remoteRelayerCertificate.getCredentialSubject()
            );
            PublicKey publicKey = ObjectIdentityUtil.getPublicKeyFromSubject(
                    relayerCredentialSubject.getApplicant(),
                    relayerCredentialSubject.getSubjectInfo()
            );

            Signature verifier = Signature.getInstance(sigAlgo);
            verifier.initVerify(publicKey);
            verifier.update(rawEncode());

            return verifier.verify(signature);

        } catch (Exception e) {
            throw new RuntimeException("failed to verify response sig", e);
        }
    }

    public boolean isSuccess() {
        return responseCode == SUCCESS;
    }

    public String calcRelayerNodeId() {
        return RelayerNodeInfo.calculateNodeId(remoteRelayerCertificate);
    }
}
