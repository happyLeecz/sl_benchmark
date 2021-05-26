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
    private static DagUserInfo serialDagUserInfo = new DagUserInfo();
    private static DagUserInfo parallelDagUserInfo = new DagUserInfo();
    private static ThreadPoolService threadPoolService;

    public static void Usage() {
        System.out.println(" Usage:");
        System.out.println("=========== test ===========");
        System.out.println(
                "\t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.ParallelOkPerfNew [groupID] [total] [conflictRate] [groups] [qps].");
        System.out.println(
                "\t eg. java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.ParallelOkPerfNew 1 50000 10 2 4000");
    }

    public static void main(String[] args)
            throws ContractException, IOException, InterruptedException {
        try {

            // 获取命令行参数
            if (args.length < 5) {
                Usage();
                return;
            }
            // 子链/群组/channel等的id
            Integer groupId = Integer.valueOf(args[0]);
            // 发送的tps数量
            Integer total = Integer.valueOf(args[1]);
            // 冲突率 0-100
            Integer conflictRate = Integer.valueOf(args[2]);
            // 根据相关性将交易进行分组，组数，若冲突率为0，则此处填0
            Integer groups = Integer.valueOf(args[3]);
            // 发送速率
            Integer qps = Integer.valueOf(args[4]);

            // 开始时间戳
            long currentSeconds = System.currentTimeMillis() / 1000L;

            // 加载配置文件
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl = ParallelOkPerfNew.class.getClassLoader().getResource(configFileName);
            if (configUrl == null) {
                System.out.println("The configFile " + configFileName + " doesn't exist!");
                return;
            }
            String configFile = configUrl.getPath();

            // 实例化bcos的sdk
            BcosSDK sdk = BcosSDK.build(configFile);
            client = sdk.getClient(Integer.valueOf(groupId));

            // 线程池
            threadPoolService =
                    new ThreadPoolService(
                            "ParallelOkPerf",
                            sdk.getConfig().getThreadPoolConfig().getMaxBlockingQueueSize());

            // ******************************************************
            // 一、生成交易测试用例集
            int[][][] tansactions =
                    Generator.generateTransactionTestCases(total, conflictRate, groups);

            // 二、部署合约
            ParallelOk parallelOk;
            ParallelOkDemo parallelOkDemo;
            parallelOk = ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());

            // 三、串行生成用户
            parallelOkDemo = new ParallelOkDemo(parallelOk, serialDagUserInfo, threadPoolService);
            System.out.println("Total number of users to be created:  " + Generator.getGi());
            parallelOkDemo.userAdd(
                    BigInteger.valueOf(Generator.getGi()), BigInteger.valueOf(qps), currentSeconds);

            // 四、串行转账
            parallelOkDemo.userTransfer(
                    BigInteger.valueOf(total), BigInteger.valueOf(qps), tansactions);

            // 五、获取交易之后的每个用户的余额数据
            parallelOkDemo.queryAccount(BigInteger.valueOf(qps));

            // ******************************************************
            // 六、部署合约
            parallelOk = ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());

            // 七、开启并行
            parallelOk.enableParallel();

            // 八、并行生成用户
            parallelOkDemo = new ParallelOkDemo(parallelOk, parallelDagUserInfo, threadPoolService);
            parallelOkDemo.userAdd(
                    BigInteger.valueOf(Generator.getGi()), BigInteger.valueOf(qps), currentSeconds);

            // 九、并行转账
            parallelOkDemo.userTransfer(
                    BigInteger.valueOf(total), BigInteger.valueOf(qps), tansactions);

            // 十、获取交易之后的每个用户的余额数据
            parallelOkDemo.queryAccount(BigInteger.valueOf(qps));

            // ******************************************************
            // 十一、正确性比对
            verify();
            // 十二、性能评估

        } catch (Exception e) {
            System.out.println("ParallelOkPerf test failed, error info: " + e.getMessage());
        }
        System.exit(0);
    }

    /**
     * 串行结果和并行结果 进行正确性检验
     * @throws InterruptedException
     */
    public static void verify() throws InterruptedException {
        int serialDaglUserInfoSize = serialDagUserInfo.size();
        int parallelDagUserInfoSize = parallelDagUserInfo.size();
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
                                            serialDagUserInfo.getDTU(userIndex).getAmount();
                                    BigInteger parallelBalance =
                                            parallelDagUserInfo.getDTU(userIndex).getAmount();
                                    if (serialBalance.compareTo(parallelBalance) != 0) {
                                        notSameCount.incrementAndGet();
                                    } else {
                                        sameCount.incrementAndGet();
                                    }
                                }
                            });
        }
        while (sameCount.intValue() + notSameCount.intValue() < size) {
            Thread.sleep(400);
        }

        System.out.println("verify:");
        System.out.println("\tthe number of user accounts is " + size);
        System.out.println(
                "\tthe number of correct user accounts in parallel is " + sameCount.intValue());
        System.out.println(
                "\tthe number of incorrect user accounts in parallel is "
                        + notSameCount.intValue());
    }
}
