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

package com.alipay.antchain.bridge.relayer.bootstrap.repo;

import java.util.List;
import java.util.concurrent.locks.Lock;
import javax.annotation.Resource;

import cn.hutool.core.collection.ListUtil;
import com.alipay.antchain.bridge.relayer.bootstrap.TestBase;
import com.alipay.antchain.bridge.relayer.commons.constant.BlockchainStateEnum;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.model.AnchorProcessHeights;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.commons.model.DomainCertWrapper;
import com.alipay.antchain.bridge.relayer.core.service.anchor.tasks.BlockTaskTypeEnum;
import com.alipay.antchain.bridge.relayer.core.service.anchor.tasks.NotifyTaskTypeEnum;
import com.alipay.antchain.bridge.relayer.dal.entities.AnchorProcessEntity;
import com.alipay.antchain.bridge.relayer.dal.entities.DomainCertEntity;
import com.alipay.antchain.bridge.relayer.dal.mapper.AnchorProcessMapper;
import com.alipay.antchain.bridge.relayer.dal.mapper.DomainCertMapper;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.redisson.api.RedissonClient;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BlockchainRepositoryTest extends TestBase {

    @BeforeClass
    public static void setup() throws Exception {
    }
    
    @Resource
    private IBlockchainRepository blockchainRepository;

    @Resource
    private DomainCertMapper domainCertMapper;

    @Resource
    private AnchorProcessMapper anchorProcessMapper;

    @Resource
    private RedissonClient redisson;

    private void saveSomeBlockchains() {

        blockchainRepository.saveBlockchainMeta(testchain1Meta);

        DomainCertEntity entity = new DomainCertEntity();
        entity.setBlockchainId(testchain1Meta.getBlockchainId());
        entity.setProduct(testchain1Meta.getProduct());
        entity.setDomain(antchainSubject.getDomainName().getDomain());
        entity.setDomainSpace(antchainSubject.getParentDomainSpace().getDomain());
        entity.setIssuerOid(antchainDotCommCert.getIssuer().encode());
        entity.setSubjectOid(antchainSubject.getApplicant().encode());
        entity.setDomainCert(antchainDotCommCert.encode());
        domainCertMapper.insert(entity);

        blockchainRepository.saveBlockchainMeta(testchain2Meta);

        entity = new DomainCertEntity();
        entity.setBlockchainId(testchain2Meta.getBlockchainId());
        entity.setProduct(testchain2Meta.getProduct());
        entity.setDomain(catchainSubject.getDomainName().getDomain());
        entity.setDomainSpace(catchainSubject.getParentDomainSpace().getDomain());
        entity.setIssuerOid(catchainDotCommCert.getIssuer().encode());
        entity.setSubjectOid(catchainSubject.getApplicant().encode());
        entity.setDomainCert(catchainDotCommCert.encode());
        domainCertMapper.insert(entity);

        blockchainRepository.saveBlockchainMeta(testchain3Meta);

        entity = new DomainCertEntity();
        entity.setBlockchainId(testchain3Meta.getBlockchainId());
        entity.setProduct(testchain3Meta.getProduct());
        entity.setDomain(dogchainSubject.getDomainName().getDomain());
        entity.setDomainSpace(dogchainSubject.getParentDomainSpace().getDomain());
        entity.setIssuerOid(dogchainDotCommCert.getIssuer().encode());
        entity.setSubjectOid(dogchainSubject.getApplicant().encode());
        entity.setDomainCert(dogchainDotCommCert.encode());
        domainCertMapper.insert(entity);

        anchorProcessMapper.insert(
                new AnchorProcessEntity(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId() + "getAnchorProcessHeight",
                        BlockTaskTypeEnum.POLLING.getCode(),
                        100L
                )
        );
    }

    @Test
    public void test3() throws Exception {
        Lock myLock = redisson.getLock("testtest");
        new Thread(
                () -> {
                    while (true) {
                        myLock.lock();
                        System.out.println("here aaa");
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        myLock.unlock();
                    }
                }
        ).start();

        new Thread(
                () -> {
                    while (true) {
                        myLock.lock();
                        System.out.println("here bbb");
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        myLock.unlock();
                    }
                }
        ).start();

        Thread.sleep(100_000);
    }

    @Test
    public void testSaveBlockchainMeta() {
        BlockchainMeta blockchainMeta = new BlockchainMeta("testchain", "testchain.id", "", "", blockchainProperties1);
        blockchainRepository.saveBlockchainMeta(blockchainMeta);
        Assert.assertThrows(AntChainBridgeRelayerException.class, () -> blockchainRepository.saveBlockchainMeta(blockchainMeta));
    }

    @Test
    public void testGetAllBlockchainMetaByState() {
        saveSomeBlockchains();
        List<BlockchainMeta> result = blockchainRepository.getBlockchainMetaByState(BlockchainStateEnum.RUNNING);
        Assert.assertTrue(result.size() >= 1);

        testchain1Meta.getProperties().setAnchorRuntimeStatus(BlockchainStateEnum.STOPPED);
        Assert.assertTrue(blockchainRepository.updateBlockchainMeta(testchain1Meta));

        result = blockchainRepository.getBlockchainMetaByState(BlockchainStateEnum.STOPPED);

        Assert.assertTrue(result.size() >= 1);
    }

    @Test
    public void testGetBlockchainMetaByDomain() {
        saveSomeBlockchains();
        BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMetaByDomain(
                antchainSubject.getDomainName().getDomain()
        );
        Assert.assertNotNull(blockchainMeta);
        Assert.assertEquals(testchain1Meta.getBlockchainId(), blockchainMeta.getBlockchainId());
    }

    @Test
    public void testGetAnchorProcessHeight() {
        saveSomeBlockchains();

        Assert.assertEquals(
                100L,
                blockchainRepository.getAnchorProcessHeight(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId() + "getAnchorProcessHeight",
                        BlockTaskTypeEnum.POLLING.getCode()
                ).longValue()
        );
    }

    @Test
    public void testGetAnchorProcessHeights() {
        saveSomeBlockchains();

        anchorProcessMapper.insert(
                new AnchorProcessEntity(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId(),
                        BlockTaskTypeEnum.POLLING.getCode(),
                        200L
                )
        );
        anchorProcessMapper.insert(
                new AnchorProcessEntity(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId(),
                        BlockTaskTypeEnum.SYNC.getCode(),
                        150L
                )
        );
        anchorProcessMapper.insert(
                new AnchorProcessEntity(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId(),
                        BlockTaskTypeEnum.NOTIFY.toNotifyWorkerHeightType(NotifyTaskTypeEnum.CROSSCHAIN_MSG_WORKER.getCode()),
                        100L
                )
        );

        AnchorProcessHeights anchorProcessHeights = blockchainRepository.getAnchorProcessHeights(
                testchain1Meta.getProduct(),
                testchain1Meta.getBlockchainId()
        );
        Assert.assertNotNull(anchorProcessHeights);
        Assert.assertEquals(
                200L,
                anchorProcessHeights.getProcessHeights().get(BlockTaskTypeEnum.POLLING.getCode()).longValue()
        );
        Assert.assertEquals(
                150L,
                anchorProcessHeights.getProcessHeights().get(BlockTaskTypeEnum.SYNC.getCode()).longValue()
        );
        Assert.assertEquals(
                100L,
                anchorProcessHeights.getProcessHeights().get(
                        BlockTaskTypeEnum.NOTIFY.toNotifyWorkerHeightType(
                                NotifyTaskTypeEnum.CROSSCHAIN_MSG_WORKER.getCode()
                        )
                ).longValue()
        );
    }

    @Test
    public void testSetAnchorProcessHeight() {

        blockchainRepository.setAnchorProcessHeight(
                testchain1Meta.getProduct(),
                testchain1Meta.getBlockchainId() + "setAnchorProcessHeight",
                BlockTaskTypeEnum.POLLING.getCode(),
                999L
        );

        Assert.assertEquals(
                999L,
                blockchainRepository.getAnchorProcessHeight(
                        testchain1Meta.getProduct(),
                        testchain1Meta.getBlockchainId()  + "setAnchorProcessHeight",
                        BlockTaskTypeEnum.POLLING.getCode()
                ).longValue()
        );
    }

    @Test
    public void test0_GetAllBlockchainMeta() {
        saveSomeBlockchains();

        Assert.assertEquals(3, blockchainRepository.getAllBlockchainMeta().size());
    }

    @Test
    public void testHasBlockchain() {
        saveSomeBlockchains();
        Assert.assertTrue(blockchainRepository.hasBlockchain(antchainSubject.getDomainName().getDomain()));

        Assert.assertTrue(blockchainRepository.hasBlockchain(testchain1Meta.getProduct(), testchain1Meta.getBlockchainId()));
    }

    @Test
    public void testGetBlockchainMetaByPluginServerId() {
        saveSomeBlockchains();

        Assert.assertTrue(
                1 <= blockchainRepository.getBlockchainMetaByPluginServerId("p-QYj86x8Zd").size()
        );
    }
    
    @Test
    public void testGetBlockchainMeta() {
        saveSomeBlockchains();
        
        BlockchainMeta blockchainMeta = blockchainRepository.getBlockchainMeta(testchain1Meta.getProduct(), testchain1Meta.getBlockchainId());
        Assert.assertNotNull(blockchainMeta);
        Assert.assertEquals(testchain1Meta.getMetaKey(), blockchainMeta.getMetaKey());
        Assert.assertEquals(testchain1Meta.getProperties().getAmClientContractAddress(), blockchainMeta.getProperties().getAmClientContractAddress());
    }
    
    @Test
    public void testGetBlockchainDomain() {
        saveSomeBlockchains();

        Assert.assertEquals(
                antchainSubject.getDomainName().getDomain(),
                blockchainRepository.getBlockchainDomain(testchain1Meta.getProduct(), testchain1Meta.getBlockchainId())
        );
    }
    
    @Test
    public void testGetBlockchainDomainsByState() {
        saveSomeBlockchains();

        List<String> domains = blockchainRepository.getBlockchainDomainsByState(BlockchainStateEnum.RUNNING);
        Assert.assertTrue(
                domains.containsAll(
                        ListUtil.of(
                                catchainSubject.getDomainName().getDomain(),
                                dogchainSubject.getDomainName().getDomain()
                        )
                )
        );
    }

    @Test
    public void testGetDomainCert() {
        saveSomeBlockchains();

        DomainCertWrapper domainCertWrapper = blockchainRepository.getDomainCert(catchainSubject.getDomainName().getDomain());
        Assert.assertNotNull(domainCertWrapper);
        Assert.assertEquals(catchainSubject.getDomainName().getDomain(), domainCertWrapper.getDomain());
        Assert.assertEquals(catchainSubject.getParentDomainSpace().getDomain(), domainCertWrapper.getDomainSpace());
    }
}
