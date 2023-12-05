package com.alipay.antchain.bridge.relayer.core.service.process;

import java.util.Base64;
import javax.annotation.Resource;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.sdp.AbstractSDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.relayer.commons.constant.AuthMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.AuthMsgTrustLevelEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.SDPMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.UpperProtocolTypeBeyondAMEnum;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import com.alipay.antchain.bridge.relayer.commons.model.*;
import com.alipay.antchain.bridge.relayer.core.manager.blockchain.IBlockchainManager;
import com.alipay.antchain.bridge.relayer.core.manager.gov.IGovernManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerNetworkManager;
import com.alipay.antchain.bridge.relayer.core.types.exception.CrossChainChannelNotExistException;
import com.alipay.antchain.bridge.relayer.core.types.exception.SendAuthMessageException;
import com.alipay.antchain.bridge.relayer.core.types.exception.UnknownRelayerForDestDomainException;
import com.alipay.antchain.bridge.relayer.core.types.network.IRelayerClientPool;
import com.alipay.antchain.bridge.relayer.core.types.network.RelayerClient;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.IScheduleRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.impl.BlockchainIdleDCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AM消息处理器
 * <p>
 * 根据不同的上层AM协议，处理流程不一样
 * <p>
 * 如果是MSG消息，则需要将取UDAG数据后，将消息拆分为一条发送消息落库
 */
@Component
@Slf4j
public class AuthenticMessageProcess {

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private IBlockchainManager blockchainManager;

    @Resource
    private IGovernManager governManager;

    @Resource
    private IRelayerNetworkManager relayerNetworkManager;

    @Resource
    private BlockchainIdleDCache blockchainIdleDCache;

    @Resource
    private IRelayerClientPool relayerClientPool;

    @Resource
    private IScheduleRepository scheduleRepository;

    @Value("${relayer.service.process.sdp.acl_on:true}")
    private boolean sdpACLOn;

    @Value("${relayer.service.process.ccmsg.fail_limit:10}")
    private int ccmsgFailLimit;

    // TODO: 当支持TP-PROOF之后，应该从网络中获得到UCP，其携带着TP-PROOF
    public boolean doProcess(AuthMsgWrapper amMsgWrapper) {

        log.info(
                "process auth msg : (src_domain: {}, id: {}, if_remote: {})",
                amMsgWrapper.getDomain(),
                amMsgWrapper.getAuthMsgId(),
                amMsgWrapper.isNetworkAM()
        );

        if (
                amMsgWrapper.getProcessState() == AuthMsgProcessStateEnum.PROCESSED
                        || amMsgWrapper.getProcessState() == AuthMsgProcessStateEnum.REJECTED
        ) {
            log.error("auth msg repeat process : {}", amMsgWrapper.getAuthMsgId());
            return true;
        } else if (
                amMsgWrapper.getTrustLevel() == AuthMsgTrustLevelEnum.NEGATIVE_TRUST
                        && amMsgWrapper.getProcessState() != AuthMsgProcessStateEnum.PROVED
        ) {
            log.error("auth msg with NEGATIVE_TRUST its state error : {}-{}", amMsgWrapper.getAuthMsgId(), amMsgWrapper.getProcessState());
            return false;
        }

        try {
            if (!amMsgWrapper.isNetworkAM()) {
                processLocalAM(amMsgWrapper);
            } else {
                processRemoteAM(amMsgWrapper, null);
            }

            if (amMsgWrapper.getProcessState() == AuthMsgProcessStateEnum.PROCESSED) {
                log.info(
                        "process high layer protocol {} of am message : (src_domain: {}, id: {}, if_remote: {})",
                        UpperProtocolTypeBeyondAMEnum.parseFromValue(amMsgWrapper.getAuthMessage().getUpperProtocol()).name(),
                        amMsgWrapper.getDomain(),
                        amMsgWrapper.getAuthMsgId(),
                        amMsgWrapper.isNetworkAM()
                );
                // 处理上层协议
                if (amMsgWrapper.getAuthMessage().getUpperProtocol() == UpperProtocolTypeBeyondAMEnum.SDP.getCode()) {
                    processSDPMsg(amMsgWrapper);
                } else {
                    throw new RuntimeException("unsupported am upper protocol type: " + amMsgWrapper.getProtocolType());
                }
            }

            return crossChainMessageRepository.updateAuthMessage(amMsgWrapper);
        } catch (SendAuthMessageException e) {
            amMsgWrapper.setFailCount(amMsgWrapper.getFailCount() + 1);
            if (amMsgWrapper.getFailCount() > ccmsgFailLimit) {
                amMsgWrapper.setProcessState(AuthMsgProcessStateEnum.FAILED);
                log.error("am {} out of retry times: ", amMsgWrapper.getAuthMsgId(), e);
            } else {
                log.warn("am {} with fail count {} process failed", amMsgWrapper.getAuthMsgId(), amMsgWrapper.getFailCount(), e);
            }
            return crossChainMessageRepository.updateAuthMessage(amMsgWrapper);
        } catch (CrossChainChannelNotExistException e) {
            log.error("failed to process sdp (send: {}, dest: {}) because that crosschain channel not found: ",
                    e.getSenderDomain(), e.getReceiverDomain(), e);
            scheduleRepository.markForDomainRouterQuery(e.getSenderDomain(), e.getReceiverDomain());
            return true;
        } catch (AntChainBridgeRelayerException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.SERVICE_CORE_PROCESS_AUTH_MSG_PROCESS_FAILED,
                    e,
                    "process auth msg failed: (src_domain: {}, id: {}, if_remote: {})",
                    amMsgWrapper.getDomain(),
                    amMsgWrapper.getAuthMsgId(),
                    amMsgWrapper.isNetworkAM()
            );
        }
    }

    private void processLocalAM(AuthMsgWrapper authMsgWrapper) {

        // 填充区块链信息
        if (!StrUtil.isAllNotEmpty(authMsgWrapper.getProduct(), authMsgWrapper.getBlockchainId())) {
            DomainCertWrapper domainCertWrapper = blockchainManager.getDomainCert(authMsgWrapper.getDomain());
            if (ObjectUtil.isNull(domainCertWrapper)) {
                throw new RuntimeException("domain cert not exist: " + authMsgWrapper.getDomain());
            }

            authMsgWrapper.setProduct(domainCertWrapper.getBlockchainProduct());
            authMsgWrapper.setBlockchainId(domainCertWrapper.getBlockchainId());
        }

        if (StrUtil.isEmpty(authMsgWrapper.getAmClientContractAddress())) {
            // 填充合约信息
            BlockchainMeta blockchainMeta = blockchainManager.getBlockchainMeta(
                    authMsgWrapper.getProduct(),
                    authMsgWrapper.getBlockchainId()
            );
            authMsgWrapper.setAmClientContractAddress(
                    blockchainMeta.getProperties().getAmClientContractAddress()
            );
        }

        authMsgWrapper.setProcessState(AuthMsgProcessStateEnum.PROCESSED);
    }

    // TODO: 当支持TP-PROOF之后，应该从网络中获得到UCP，其携带着TP-PROOF
    private void processRemoteAM(AuthMsgWrapper authMsgWrapper, UniformCrosschainPacketContext ucpContext) {
        // TODO : 无论是哪种信任等级，都应该被标记为PROCESSED，之后增加duty任务内容，
        //  将信任等级是POSITIVE_TRUST且PROCESSED的消息，传递其TP-PROOF
        authMsgWrapper.setProcessState(AuthMsgProcessStateEnum.PROCESSED);
    }

    private void processSDPMsg(AuthMsgWrapper authMsgWrapper) {

        SDPMsgWrapper sdpMsgWrapper = parseSDPMsgFrom(authMsgWrapper);

        // 接收者在本地
        if (StrUtil.isNotEmpty(sdpMsgWrapper.getReceiverBlockchainId())) {
            this.blockchainIdleDCache.setLastAMProcessTime(
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainId()
            );
            processLocalSDPMsg(sdpMsgWrapper);
            return;
        }

        processRemoteSDPMsg(sdpMsgWrapper);
    }

    public SDPMsgWrapper parseSDPMsgFrom(AuthMsgWrapper authMsgWrapper) {

        SDPMsgWrapper sdpMsgWrapper = new SDPMsgWrapper();

        sdpMsgWrapper.setSdpMessage(
                (AbstractSDPMessage) SDPMessageFactory.createSDPMessage(
                        authMsgWrapper.getAuthMessage().getPayload()
                )
        );

        sdpMsgWrapper.setAuthMsgWrapper(authMsgWrapper);

        if (StrUtil.isEmpty(sdpMsgWrapper.getReceiverBlockchainDomain())) {
            log.error(
                    "receiver domain is empty from am (src_domain: {}, id: {}, if_remote: {})",
                    authMsgWrapper.getDomain(),
                    authMsgWrapper.getAuthMsgId(),
                    authMsgWrapper.isNetworkAM()
            );
            sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.MSG_ILLEGAL);
            sdpMsgWrapper.setTxFailReason("Empty receiver domain");
            return sdpMsgWrapper;
        }

        if (blockchainManager.hasBlockchain(sdpMsgWrapper.getReceiverBlockchainDomain())) {
            BlockchainMeta receiverBlockchainMeta = blockchainManager.getBlockchainMetaByDomain(
                    sdpMsgWrapper.getReceiverBlockchainDomain()
            );
            if (ObjectUtil.isNull(receiverBlockchainMeta)) {
                log.error(
                        "receiver blockchain not exist for domain {} from am (src_domain: {}, id: {}, if_remote: {})",
                        sdpMsgWrapper.getReceiverBlockchainDomain(),
                        authMsgWrapper.getDomain(),
                        authMsgWrapper.getAuthMsgId(),
                        authMsgWrapper.isNetworkAM()
                );
                sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.MSG_ILLEGAL);
                sdpMsgWrapper.setTxFailReason("Blockchain supposed existed but not");
                return sdpMsgWrapper;
            }
            sdpMsgWrapper.setReceiverBlockchainId(receiverBlockchainMeta.getBlockchainId());
            sdpMsgWrapper.setReceiverBlockchainProduct(receiverBlockchainMeta.getProduct());
            sdpMsgWrapper.setReceiverAMClientContract(receiverBlockchainMeta.getProperties().getAmClientContractAddress());
        }

        sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.PENDING);

        log.info(
                "parse auth msg to sdp msg : ( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} )",
                sdpMsgWrapper.getVersion(),
                sdpMsgWrapper.getSenderBlockchainDomain(),
                sdpMsgWrapper.getMsgSender(),
                sdpMsgWrapper.getReceiverBlockchainDomain(),
                sdpMsgWrapper.getMsgReceiver(),
                sdpMsgWrapper.getMsgSequence(),
                authMsgWrapper.getAuthMsgId()
        );

        return sdpMsgWrapper;
    }

    private void processRemoteSDPMsg(SDPMsgWrapper sdpMsgWrapper) {
        String relayerNodeId = relayerNetworkManager.findRemoteRelayer(sdpMsgWrapper.getReceiverBlockchainDomain());
        if (ObjectUtil.isNull(relayerNodeId)) {
            throw new UnknownRelayerForDestDomainException(
                    StrUtil.format("relayer not exist for dest domain {}", sdpMsgWrapper.getReceiverBlockchainDomain())
            );
        }
        if (
                blockchainManager.hasBlockchain(sdpMsgWrapper.getSenderBlockchainDomain())
                        && !relayerNetworkManager.hasCrossChainChannel(sdpMsgWrapper.getSenderBlockchainDomain(), sdpMsgWrapper.getReceiverBlockchainDomain())
        ) {
            throw new CrossChainChannelNotExistException(
                    sdpMsgWrapper.getSenderBlockchainDomain(),
                    sdpMsgWrapper.getReceiverBlockchainDomain(),
                    StrUtil.format("crosschain channel not exist for local sdp msg (send: {}, dest: {})",
                            sdpMsgWrapper.getSenderBlockchainDomain(), sdpMsgWrapper.getReceiverBlockchainDomain())
            );
        }
        try {
            RelayerClient relayerClient = relayerClientPool.getRelayerClientByDomain(sdpMsgWrapper.getReceiverBlockchainDomain());
            if (ObjectUtil.isNull(relayerClient)) {
                relayerClient = relayerClientPool.getRelayerClient(
                        relayerNetworkManager.getRelayerNode(relayerNodeId, false),
                        sdpMsgWrapper.getReceiverBlockchainDomain()
                );
            }
            relayerClient.amRequest(
                    sdpMsgWrapper.getSenderBlockchainDomain(),
                    sdpMsgWrapper.getAuthMsgWrapper().getUcpId(),
                    Base64.getEncoder().encodeToString(sdpMsgWrapper.getAuthMsgWrapper().getAuthMessage().encode()),
                    "",
                    new String(sdpMsgWrapper.getAuthMsgWrapper().getRawLedgerInfo())
            );
        } catch (Exception e) {
            throw new SendAuthMessageException(
                    e,
                    "failed to send message " +
                            "( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} ) " +
                            "to remote relayer {}",
                    sdpMsgWrapper.getVersion(),
                    sdpMsgWrapper.getSenderBlockchainDomain(),
                    sdpMsgWrapper.getMsgSender(),
                    sdpMsgWrapper.getReceiverBlockchainDomain(),
                    sdpMsgWrapper.getMsgReceiver(),
                    sdpMsgWrapper.getMsgSequence(),
                    sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId(),
                    relayerNodeId
            );
        }

        sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.REMOTE_PENDING);
        crossChainMessageRepository.putSDPMessage(sdpMsgWrapper);

        log.info(
                "successful to send message " +
                        "( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} ) " +
                        "to remote relayer {}",
                sdpMsgWrapper.getVersion(),
                sdpMsgWrapper.getSenderBlockchainDomain(),
                sdpMsgWrapper.getMsgSender(),
                sdpMsgWrapper.getReceiverBlockchainDomain(),
                sdpMsgWrapper.getMsgReceiver(),
                sdpMsgWrapper.getMsgSequence(),
                sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId(),
                relayerNodeId
        );
    }

    private void processLocalSDPMsg(SDPMsgWrapper sdpMsgWrapper) {
        switch (sdpMsgWrapper.getProcessState()) {
            case PENDING:
                // 检查ACL规则，若规则不满足，则状态置为am_msg_rejected
                checkCrossChainACLRule(sdpMsgWrapper);
                if (sdpMsgWrapper.getProcessState() == SDPMsgProcessStateEnum.MSG_REJECTED) {
                    log.warn(
                            "sdp msg ( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} ) is rejected by ACL",
                            sdpMsgWrapper.getVersion(),
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            sdpMsgWrapper.getMsgSender(),
                            sdpMsgWrapper.getReceiverBlockchainDomain(),
                            sdpMsgWrapper.getMsgReceiver(),
                            sdpMsgWrapper.getMsgSequence(),
                            sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId()
                    );
                }
                break;
            case MSG_ILLEGAL:
                log.error(
                        "process illegal sdp msg ( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} ) on receiving locally",
                        sdpMsgWrapper.getVersion(),
                        sdpMsgWrapper.getSenderBlockchainDomain(),
                        sdpMsgWrapper.getMsgSender(),
                        sdpMsgWrapper.getReceiverBlockchainDomain(),
                        sdpMsgWrapper.getMsgReceiver(),
                        sdpMsgWrapper.getMsgSequence(),
                        sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId()
                );
                break;
        }

        crossChainMessageRepository.putSDPMessage(sdpMsgWrapper);
        log.info(
                "successful to process sdp msg ( version: {}, from_blockchain: {}, sender: {}, receiver_blockchain: {}, receiver: {}, seq: {}, am_id: {} ) locally",
                sdpMsgWrapper.getVersion(),
                sdpMsgWrapper.getSenderBlockchainDomain(),
                sdpMsgWrapper.getMsgSender(),
                sdpMsgWrapper.getReceiverBlockchainDomain(),
                sdpMsgWrapper.getMsgReceiver(),
                sdpMsgWrapper.getMsgSequence(),
                sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId()
        );
    }

    private void checkCrossChainACLRule(SDPMsgWrapper sdpMsgWrapper) {

        if (!sdpACLOn || sdpMsgWrapper.isBlockchainSelfCall()) {
            return;
        }

        if (
                !governManager.verifyCrossChainMsgACL(
                        sdpMsgWrapper.getSenderBlockchainDomain(),
                        sdpMsgWrapper.getMsgSender(),
                        sdpMsgWrapper.getReceiverBlockchainDomain(),
                        sdpMsgWrapper.getMsgReceiver()
                )
        ) {
            sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.MSG_REJECTED);
            sdpMsgWrapper.setTxFailReason("msg rejected by ACL");
        }
    }
}
