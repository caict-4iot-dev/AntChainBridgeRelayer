package com.alipay.antchain.bridge.relayer.core.manager.bbc;

import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import com.alipay.antchain.bridge.relayer.core.types.pluginserver.IBBCServiceClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SDPMsgClientHeteroBlockchainImpl implements ISDPMsgClientContract {

    private IBBCServiceClient bbcServiceClient;

    public SDPMsgClientHeteroBlockchainImpl(IBBCServiceClient bbcServiceClient) {
        this.bbcServiceClient = bbcServiceClient;
    }

    @Override
    public void setAmContract(String amContract) {
        this.bbcServiceClient.setAmContract(amContract);
        this.bbcServiceClient.setLocalDomain(bbcServiceClient.getDomain());
    }

    @Override
    public long querySDPMsgSeqOnChain(String senderDomain, String from, String receiverDomain, String to) {
        try {
            return this.bbcServiceClient.querySDPMessageSeq(senderDomain, from, receiverDomain, to);
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.CORE_BBC_CALL_ERROR,
                    String.format(
                            "failed to queryP2PMsgSeqOnChain for channel ( senderDomain: %s, from: %s, receiverDomain: %s, to: %s )",
                            senderDomain, from, receiverDomain, to
                    )
            );
        }
    }

    @Override
    public void deployContract() {
        this.bbcServiceClient.setupSDPMessageContract();
    }
}
