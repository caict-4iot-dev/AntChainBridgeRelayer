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

package com.alipay.antchain.bridge.relayer.bootstrap.config;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.PemUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.bcdns.RelayerCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.relayer.commons.model.RelayerNodeInfo;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.GRpcBBCPluginManager;
import com.alipay.antchain.bridge.relayer.core.manager.bbc.IBBCPluginManager;
import com.alipay.antchain.bridge.relayer.core.manager.bcdns.IBCDNSManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerCredentialManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerNetworkManager;
import com.alipay.antchain.bridge.relayer.core.service.receiver.ReceiverService;
import com.alipay.antchain.bridge.relayer.core.types.network.ws.WsSslFactory;
import com.alipay.antchain.bridge.relayer.dal.repository.IPluginServerRepository;
import com.alipay.antchain.bridge.relayer.server.network.WSRelayerServer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class RelayerCoreConfig {

    @Value("${relayer.plugin_server_manager.grpc.auth.tls.client.key.path:config/relayer.key}")
    private String clientKeyPath;

    @Value("${relayer.plugin_server_manager.grpc.auth.tls.client.ca.path:config/relayer.crt}")
    private String clientCaPath;

    @Value("${relayer.plugin_server_manager.grpc.thread.num:32}")
    private int clientThreadNum;

    @Value("${relayer.plugin_server_manager.grpc.heartbeat.thread.num:4}")
    private int clientHeartbeatThreadNum;

    @Value("${relayer.plugin_server_manager.grpc.heartbeat.delayed_time:5000}")
    private long heartbeatDelayedTime;

    @Value("${relayer.plugin_server_manager.grpc.heartbeat.error_limit:5}")
    private int errorLimitForHeartbeat;

    @Value("${relayer.network.node.crosschain_cert_path:null}")
    private String relayerCrossChainCertPath;

    @Value("${relayer.network.node.private_key_path}")
    private String relayerPrivateKeyPath;

    @Value("${relayer.network.node.issue_domain_space:}")
    private String relayerIssuerDomainSpace;

    @Value("${relayer.network.node.server.mode:https}")
    private String localNodeServerMode;

    @Value("${relayer.network.node.server.port:8082}")
    private int localNodeServerPort;

    @Value("#{systemConfigRepository.defaultNetworkId}")
    private String defaultNetworkId;

    @Value("${relayer.network.node.server.as_discovery:false}")
    private boolean isDiscoveryService;

    public AbstractCrossChainCertificate getLocalRelayerCrossChainCertificate() {
        AbstractCrossChainCertificate relayerCertificate = CrossChainCertificateFactory.createCrossChainCertificateFromPem(
                FileUtil.readBytes(relayerCrossChainCertPath)
        );
        Assert.isTrue(CrossChainCertificateUtil.isRelayerCert(relayerCertificate));
        return relayerCertificate;
    }

    public RelayerCredentialSubject getLocalRelayerCredentialSubject() {
        return RelayerCredentialSubject.decode(getLocalRelayerCrossChainCertificate().getCredentialSubject());
    }

    public String getLocalRelayerIssuerDomainSpace() {
        return ObjectUtil.isNull(relayerIssuerDomainSpace) ? CrossChainDomain.ROOT_DOMAIN_SPACE : relayerIssuerDomainSpace;
    }

    @SneakyThrows
    public PrivateKey getLocalPrivateKey() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(FileUtil.readBytes(relayerPrivateKeyPath));
        try {
            return PemUtil.readPemPrivateKey(inputStream);
        } catch (Exception e) {
            inputStream.reset();
            byte[] rawPemOb = PemUtil.readPem(inputStream);
            KeyFactory keyFactory = KeyFactory.getInstance(
                    PrivateKeyInfo.getInstance(rawPemOb).getPrivateKeyAlgorithm().getAlgorithm().getId()
            );
            return keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(
                            rawPemOb
                    )
            );
        }
    }

    public String getLocalRelayerNodeId() {
        return RelayerNodeInfo.calculateNodeId(getLocalRelayerCrossChainCertificate());
    }

    @Bean
    @Autowired
    public IBBCPluginManager bbcPluginManager(
            IPluginServerRepository pluginServerRepository,
            TransactionTemplate transactionTemplate
    ) {
        return new GRpcBBCPluginManager(
                clientKeyPath,
                clientCaPath,
                pluginServerRepository,
                transactionTemplate,
                new ThreadPoolExecutor(
                        clientThreadNum,
                        clientThreadNum,
                        3000,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(clientThreadNum * 20),
                        new ThreadFactoryBuilder().setNameFormat("plugin_manager-grpc-%d").build(),
                        new ThreadPoolExecutor.CallerRunsPolicy()
                ),
                new ScheduledThreadPoolExecutor(
                        clientHeartbeatThreadNum,
                        new ThreadFactoryBuilder().setNameFormat("plugin_manager-heartbeat-%d").build()
                ),
                heartbeatDelayedTime,
                errorLimitForHeartbeat
        );
    }

    @Bean
    @Autowired
    public WSRelayerServer wsRelayerServer(
            @Qualifier("wsRelayerServerExecutorService") ExecutorService wsRelayerServerExecutorService,
            WsSslFactory wsSslFactory,
            IRelayerNetworkManager relayerNetworkManager,
            IBCDNSManager bcdnsManager,
            IRelayerCredentialManager relayerCredentialManager,
            ReceiverService receiverService,
            RedissonClient redisson
    ) {
        try {
            return new WSRelayerServer(
                    localNodeServerMode,
                    localNodeServerPort,
                    defaultNetworkId,
                    wsRelayerServerExecutorService,
                    wsSslFactory,
                    relayerNetworkManager,
                    bcdnsManager,
                    relayerCredentialManager,
                    receiverService,
                    redisson,
                    isDiscoveryService
            );
        } catch (Exception e) {
            throw new BeanInitializationException(
                    "failed to initialize bean wsRelayerServer",
                    e
            );
        }
    }
}
