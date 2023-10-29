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

package com.alipay.antchain.bridge.relayer.dal.repository;

import java.util.List;

import com.alipay.antchain.bridge.relayer.commons.model.AnchorProcessHeights;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;

public interface IBlockchainRepository {

    Long getAnchorProcessHeight(String product, String blockchainId, String heightType);

    AnchorProcessHeights getAnchorProcessHeights(String product, String blockchainId);

    void setAnchorProcessHeight(String product, String blockchainId, String heightType, Long height);

    void saveBlockchainMeta(BlockchainMeta blockchainMeta);

    boolean updateBlockchainMeta(BlockchainMeta blockchainMeta);

    List<BlockchainMeta> getAllBlockchainMeta();

    BlockchainMeta getBlockchainMeta(String product, String blockchainId);

    boolean hasBlockchain(String product, String blockchainId);

    String getBlockchainDomain(String product, String blockchainId);
}
