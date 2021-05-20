/**
 * Copyright 2014-2020 [fisco-dev]
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fisco.bcos.sdk.demo.perf;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.demo.contract.ParallelOk;
import org.fisco.bcos.sdk.demo.perf.model.DagUserInfo;
import org.fisco.bcos.sdk.demo.perf.parallel.ParallelOkDemo;
import org.fisco.bcos.sdk.demo.util.Generator;
import org.fisco.bcos.sdk.model.ConstantConfig;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.utils.ThreadPoolService;

public class ParallelOkPerfNew {
    private static Client client;
    private static DagUserInfo serialDagUserInfo = new DagUserInfo(Generator.getGi());
    private static DagUserInfo parallelDagUserInfo = new DagUserInfo(Generator.getGi());
    private static ThreadPoolService threadPoolService;

    public static void Usage() {
        System.out.println(" Usage:");
        System.out.println("=========== test ===========");
        System.out.println(
                "\t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.ParallelOkPerf [groupID] [total] [tps] [conflictRate] [groups] [tps].");
    }
    // [groupID] [total] [conflictRate] [groups] [tps]
    public static void main(String[] args)
            throws ContractException, IOException, InterruptedException {
        try {
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl = ParallelOkPerfNew.class.getClassLoader().getResource(configFileName);
            if (configUrl == null) {
                System.out.println("The configFile " + configFileName + " doesn't exist!");
                return;
            }
            if (args.length < 5) {
                Usage();
                return;
            }
            Integer groupId = Integer.valueOf(args[0]);
            Integer total = Integer.valueOf(args[1]);
            Integer conflictRate = Integer.valueOf(args[2]);
            Integer groups = Integer.valueOf(args[3]);
            Integer tps = Integer.valueOf(args[4]);

            String configFile = configUrl.getPath();
            BcosSDK sdk = BcosSDK.build(configFile);
            client = sdk.getClient(Integer.valueOf(groupId));
            threadPoolService =
                    new ThreadPoolService(
                            "ParallelOkPerf",
                            sdk.getConfig().getThreadPoolConfig().getMaxBlockingQueueSize());

            // ******************************************************
            // 一、生成交易测试用例
            int[][][] tansactions =
                    Generator.generateTransactionTestCases(total, conflictRate, groups);
            ParallelOk parallelOk;
            ParallelOkDemo parallelOkDemo;
            // 二、部署合约
            parallelOk = ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());
            // 三、生成用户
            parallelOkDemo = new ParallelOkDemo(parallelOk, serialDagUserInfo, threadPoolService);
            System.out.println("the real user number is " + Generator.getGi());
            parallelOkDemo.userAdd(BigInteger.valueOf(Generator.getGi()), BigInteger.valueOf(tps));
            // 四、串行
            serialDagUserInfo.loadDagTransferUser();
            parallelOk =
                    ParallelOk.load(
                            serialDagUserInfo.getContractAddr(),
                            client,
                            client.getCryptoSuite().getCryptoKeyPair());
            System.out.println(
                    "====== ParallelOk trans, load success, address: "
                            + parallelOk.getContractAddress());
            parallelOkDemo = new ParallelOkDemo(parallelOk, serialDagUserInfo, threadPoolService);
            parallelOkDemo.userTransfer(
                    BigInteger.valueOf(total), BigInteger.valueOf(tps), tansactions);
            // 获取交易之后的每个用户的余额数据
            parallelOkDemo.getBalanceResult(BigInteger.valueOf(tps));

            // ******************************************************
            // 五、部署合约
            parallelOk = ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());
            // 六、生成用户
            parallelOkDemo = new ParallelOkDemo(parallelOk, parallelDagUserInfo, threadPoolService);
            parallelOkDemo.userAdd(BigInteger.valueOf(Generator.getGi()), BigInteger.valueOf(tps));
            // 七、开启并行
            parallelOk.enableParallel();
            // 八、并行
            parallelDagUserInfo.loadDagTransferUser();
            parallelOk =
                    ParallelOk.load(
                            parallelDagUserInfo.getContractAddr(),
                            client,
                            client.getCryptoSuite().getCryptoKeyPair());
            System.out.println(
                    "====== ParallelOk trans, load success, address: "
                            + parallelOk.getContractAddress());
            parallelOkDemo = new ParallelOkDemo(parallelOk, parallelDagUserInfo, threadPoolService);
            parallelOkDemo.userTransfer(
                    BigInteger.valueOf(total), BigInteger.valueOf(tps), tansactions);
            // 获取交易之后的每个用户的余额数据
            parallelOkDemo.getBalanceResult(BigInteger.valueOf(tps));

            // ******************************************************
            // 九、正确性比对
            verify();
            // 十、性能评估

        } catch (Exception e) {
            System.out.println("ParallelOkPerf test failed, error info: " + e.getMessage());
            System.exit(0);
        }
    }

    public static void verify() throws InterruptedException {
        int serialDaglUserInfoSize = serialDagUserInfo.getUserList().size();
        int parallelDagUserInfoSize = parallelDagUserInfo.getUserList().size();
        AtomicInteger sameCount = new AtomicInteger(0);
        AtomicInteger notSameCount = new AtomicInteger(0);
        assert serialDaglUserInfoSize == parallelDagUserInfoSize : "user size is not the same";
        int size = serialDaglUserInfoSize;
        for (int i = 0; i < size; i++) {
            final int userIndex = i;
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    BigInteger serialBalance =
                                            serialDagUserInfo
                                                    .getUserList()
                                                    .get(userIndex)
                                                    .getAmount();
                                    BigInteger parallelBalance =
                                            parallelDagUserInfo
                                                    .getUserList()
                                                    .get(userIndex)
                                                    .getAmount();
                                    if (serialBalance.compareTo(parallelBalance) != 0) {
                                        notSameCount.incrementAndGet();
                                    } else {
                                        sameCount.incrementAndGet();
                                    }
                                }
                            });
        }
        while (sameCount.intValue() + notSameCount.intValue() < size) {
            Thread.sleep(1000);
        }

        System.out.println("verify:");
        System.out.println("\ttotal transactions count is " + size);
        System.out.println("\tcorrect transactions count in parallel is " + sameCount.intValue());
        System.out.println(
                "\tincorrect transactions count in parallel is " + notSameCount.intValue());
    }
}
