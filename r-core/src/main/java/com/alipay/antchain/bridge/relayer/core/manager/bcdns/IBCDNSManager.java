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

package com.alipay.antchain.bridge.relayer.core.manager.bcdns;

import java.util.List;
import java.util.Map;

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.BCDNSTrustRootCredentialSubject;

public interface IBCDNSManager {

    Map<String, AbstractCrossChainCertificate> getAllTrustRootCerts();

    AbstractCrossChainCertificate getTrustRootCert(String domainSpace);

    Map<String, AbstractCrossChainCertificate> getTrustRootCertChain(String domainSpace);

    List<String> getDomainSpaceChain(String domainSpace);

    AbstractCrossChainCertificate getTrustRootCertForRootDomain();

    BCDNSTrustRootCredentialSubject getTrustRootCredentialSubject(String domainSpace);

    BCDNSTrustRootCredentialSubject getTrustRootCredentialSubjectForRootDomain();

    boolean validateCrossChainCertificate(AbstractCrossChainCertificate certificate);
}
