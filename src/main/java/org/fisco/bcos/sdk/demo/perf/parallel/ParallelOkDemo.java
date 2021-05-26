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
package org.fisco.bcos.sdk.demo.perf.parallel;

import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.fisco.bcos.sdk.demo.contract.ParallelOk;
import org.fisco.bcos.sdk.demo.perf.callback.ParallelOkCallback;
import org.fisco.bcos.sdk.demo.perf.collector.PerformanceCollector;
import org.fisco.bcos.sdk.demo.perf.model.DagTransferUser;
import org.fisco.bcos.sdk.demo.perf.model.DagUserInfo;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.utils.ThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelOkDemo {
    private static final Logger logger = LoggerFactory.getLogger(ParallelOkDemo.class);
    //    private AtomicInteger sended;
    private AtomicInteger getted = new AtomicInteger(0);

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final ParallelOk parallelOk;
    private final ThreadPoolService threadPoolService;
    //    private PerformanceCollector collector;
    private final DagUserInfo dagUserInfo;

    public ParallelOkDemo(
            ParallelOk parallelOk, DagUserInfo dagUserInfo, ThreadPoolService threadPoolService) {
        //        this.sended = new AtomicInteger(0);
        this.threadPoolService = threadPoolService;
        this.parallelOk = parallelOk;
        this.dagUserInfo = dagUserInfo;
        //        this.collector = new PerformanceCollector();
    }

    public void veryTransferData(BigInteger qps) throws InterruptedException {
        RateLimiter rateLimiter = RateLimiter.create(qps.intValue());
        System.out.println("===================================================================");
        AtomicInteger verifyFailed = new AtomicInteger(0);
        AtomicInteger verifySuccess = new AtomicInteger(0);

        final List<DagTransferUser> userInfo = dagUserInfo.getUserList();
        int userSize = userInfo.size();
        for (int i = 0; i < userSize; i++) {
            rateLimiter.acquire();
            final int userIndex = i;
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String user = userInfo.get(userIndex).getUser();
                                        BigInteger balance = parallelOk.balanceOf(user);
                                        BigInteger localAmount =
                                                userInfo.get(userIndex).getAmount();
                                        if (localAmount.compareTo(balance) != 0) {
                                            logger.error(
                                                    "local balance is not the same as the remote, user: {}, local balance: {}, remote balance: {}",
                                                    user,
                                                    localAmount,
                                                    balance);
                                            verifyFailed.incrementAndGet();
                                        } else {
                                            verifySuccess.incrementAndGet();
                                        }
                                    } catch (ContractException exception) {
                                        verifyFailed.incrementAndGet();
                                        logger.error(
                                                "get remote balance failed, error info: "
                                                        + exception.getMessage());
                                    }
                                }
                            });
        }
        while (verifySuccess.get() + verifyFailed.get() < userSize) {
            Thread.sleep(40);
        }

        System.out.println("validation:");
        System.out.println(" \tuser count is " + userSize);
        System.out.println(" \tverify_success count is " + verifySuccess);
        System.out.println(" \tverify_failed count is " + verifyFailed);
    }

    /**
     * 新增用户到智能合约
     *
     * @param userCount
     * @param qps
     * @param currentSeconds ：测试开始的时间戳
     * @throws InterruptedException
     * @throws IOException
     */
    public void userAdd(BigInteger userCount, BigInteger qps, long currentSeconds)
            throws InterruptedException, IOException {

        System.out.println(
                "==================================================================== add users");
        System.out.println("Start UserAdd test, count " + userCount);

        // 已经发送的交易数量
        AtomicInteger sended = new AtomicInteger(0);
        // 发送失败的数量
        AtomicInteger sendFailed = new AtomicInteger(0);

        //        long currentSeconds = System.currentTimeMillis() / 1000L;
        // 所花费时间的统计比例
        Integer area = userCount.intValue() / 10;

        // 发送交易速率控制器
        RateLimiter limiter = RateLimiter.create(qps.intValue());

        // 性能收集器
        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(userCount.intValue());
        // 开始时间
        long startTime = System.currentTimeMillis();
        // 为收集器设置统计开始的时间
        collector.setStartTimestamp(startTime);
        System.out.println("====================== start time" + startTime);

        // 每次循环选用一个线程，发送一个交易，以生成一个用户
        for (Integer i = 0; i < userCount.intValue(); i++) {

            // 本次循环的下标
            final Integer index = i;

            // 是否允许发送
            limiter.acquire();

            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // generate the user according to currentSeconds
                                    // 生成用户，测试开始的时间戳+当前下标
                                    String user =
                                            Long.toHexString(currentSeconds)
                                                    + Integer.toHexString(index);
                                    // 设置账户余额
                                    BigInteger amount = new BigInteger("1000000000");

                                    // 当前用户
                                    DagTransferUser dtu = new DagTransferUser();
                                    // 用户地址
                                    dtu.setUser(user);
                                    // 用户余额
                                    dtu.setAmount(amount);

                                    // 设置回调
                                    ParallelOkCallback callback =
                                            new ParallelOkCallback(
                                                    collector,
                                                    dagUserInfo,
                                                    ParallelOkCallback.ADD_USER_CALLBACK,
                                                    index);
                                    callback.setTimeout(0);
                                    callback.setUser(dtu);

                                    try {
                                        // 记录开始发送的时间
                                        callback.recordStartTime();
                                        // 发送交易
                                        parallelOk.set(user, amount, callback);

                                        // 当前已经发送的数量
                                        int current = sended.incrementAndGet();
                                        if (current >= area && ((current % area) == 0)) {
                                            long elapsed = System.currentTimeMillis() - startTime;
                                            double sendSpeed = current / ((double) elapsed / 1000);
                                            System.out.println(
                                                    "Already sended: "
                                                            + current
                                                            + "/"
                                                            + userCount
                                                            + " transactions"
                                                            + ",QPS="
                                                            + sendSpeed);
                                        }

                                    } catch (Exception e) {

                                        // 发送失败的情况
                                        logger.warn(
                                                "addUser failed, error info: {}", e.getMessage());
                                        sendFailed.incrementAndGet();
                                        TransactionReceipt receipt = new TransactionReceipt();
                                        receipt.setStatus("-1");
                                        receipt.setMessage(
                                                "userAdd failed, error info: " + e.getMessage());
                                        callback.onResponse(receipt);
                                    }
                                }
                            });
        }
        // 如果性能收集器没有收到足够多的交易，则打印日志
        while (collector.getReceived().intValue() != userCount.intValue()) {
            logger.info(
                    " sendFailed: {}, received: {}, total: {}",
                    sendFailed.get(),
                    collector.getReceived().intValue(),
                    collector.getTotal());
            Thread.sleep(100);
        }
        // 保存合约地址
        dagUserInfo.setContractAddr(parallelOk.getContractAddress());
        //        dagUserInfo.writeDagTransferUser();
        System.exit(0);
    }

    //    public void queryAccount(BigInteger qps) throws InterruptedException {
    //        final List<DagTransferUser> allUsers = dagUserInfo.getUserList();
    //        RateLimiter rateLimiter = RateLimiter.create(qps.intValue());
    //        AtomicInteger sent = new AtomicInteger(0);
    //        for (Integer i = 0; i < allUsers.size(); i++) {
    //            final Integer index = i;
    //            rateLimiter.acquire();
    //            threadPoolService
    //                    .getThreadPool()
    //                    .execute(
    //                            new Runnable() {
    //                                @Override
    //                                public void run() {
    //                                    try {
    //                                        BigInteger result =
    //
    // parallelOk.balanceOf(allUsers.get(index).getUser());
    //                                        allUsers.get(index).setAmount(result);
    //                                        int all = sent.incrementAndGet();
    //                                        if (all >= allUsers.size()) {
    //                                            System.out.println(
    //                                                    dateFormat.format(new Date())
    //                                                            + " Query account finished");
    //                                        }
    //                                    } catch (ContractException exception) {
    //                                        logger.warn(
    //                                                "queryAccount for {} failed, error info: {}",
    //                                                allUsers.get(index).getUser(),
    //                                                exception.getMessage());
    //                                        System.exit(0);
    //                                    }
    //                                }
    //                            });
    //        }
    //        while (sent.get() < allUsers.size()) {
    //            Thread.sleep(50);
    //        }
    //    }

    /**
     * 转账
     *
     * @param count
     * @param qps
     * @param transactions
     * @throws InterruptedException
     * @throws IOException
     */
    public void userTransfer(BigInteger count, BigInteger qps, int[][][] transactions)
            throws InterruptedException, IOException {

        // 已经发送的交易的数量
        AtomicInteger sended = new AtomicInteger(0);
        // 发送失败的交易数
        AtomicInteger sendFailed = new AtomicInteger(0);
        // 所花费时间的统计比例
        int division = count.intValue() / 10;

        System.out.println(
                "==================================================================== Querying account info...");
        queryAccount(qps);
        System.out.println("Sending transfer transactions...");

        // 发送速率控制器
        RateLimiter limiter = RateLimiter.create(qps.intValue());

        // 性能收集器
        PerformanceCollector collector = new PerformanceCollector();
        // 为收集器设置统计开始的时间
        long startTime = System.currentTimeMillis();
        collector.setStartTimestamp(startTime);
        // 为收集器设置要收集的交易总数
        collector.setTotal(count.intValue());

        // transactions为3维数组，第1维是分组
        for (Integer i = 0; i < transactions.length; i++) {
            // 第2维是交易，第3维是交易双方
            for (Integer j = 0; j < transactions[i].length; j++) {

                // 是否能够发送
                limiter.acquire();

                // 转出者的下标
                final int fromUserIndex = transactions[i][j][0];
                // 收款者的下标
                final int toUserIndex = transactions[i][j][1];

                // 每笔交易都由一个线程发送
                threadPoolService
                        .getThreadPool()
                        .execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {

                                            // 转账的金额
                                            BigInteger amount = BigInteger.valueOf(10);

                                            // 设置回调
                                            ParallelOkCallback callback =
                                                    new ParallelOkCallback(
                                                            collector,
                                                            dagUserInfo,
                                                            ParallelOkCallback.TRANS_CALLBACK,
                                                            null);
                                            callback.setTimeout(0);

                                            // 转账者
                                            DagTransferUser from =
                                                    dagUserInfo.getDTU(fromUserIndex);
                                            // 收款者
                                            DagTransferUser to = dagUserInfo.getDTU(toUserIndex);

                                            callback.setFromUser(from);
                                            callback.setToUser(to);
                                            callback.setAmount(amount);

                                            // 记录开始发送的时间
                                            callback.recordStartTime();

                                            // 发送转账交易
                                            parallelOk.transfer(
                                                    from.getUser(), to.getUser(), amount, callback);

                                            // 已经发送的交易数
                                            int current = sended.incrementAndGet();

                                            if (current >= division
                                                    && ((current % division) == 0)) {
                                                // 已经花费时间
                                                long elapsed =
                                                        System.currentTimeMillis() - startTime;
                                                // 当前qps
                                                double sendSpeed =
                                                        current / ((double) elapsed / 1000);
                                                System.out.println(
                                                        "Already sent: "
                                                                + current
                                                                + "/"
                                                                + count
                                                                + " transactions"
                                                                + ",QPS="
                                                                + sendSpeed);
                                            }
                                        } catch (Exception e) {

                                            logger.error(
                                                    "call transfer failed, error info: {}",
                                                    e.getMessage());

                                            // 构造发送失败的交易收据
                                            TransactionReceipt receipt = new TransactionReceipt();
                                            // 设置交易发送失败的状态
                                            receipt.setStatus("-1");
                                            receipt.setMessage(
                                                    "call transfer failed, error info: "
                                                            + e.getMessage());
                                            // 将该收据传入到性能收集器中
                                            collector.onMessage(receipt, Long.valueOf(0));

                                            // 发送失败的数量+1
                                            sendFailed.incrementAndGet();
                                        }
                                    }
                                });
            }
        }

        // 如果性能收集器没有收到足够多的交易，则打印日志
        while (collector.getReceived().intValue() != count.intValue()) {
            Thread.sleep(3000);
            logger.info(
                    "userTransfer: sendFailed: {}, received: {}, total: {}",
                    sendFailed.get(),
                    collector.getReceived().intValue(),
                    collector.getTotal());
        }
        //        veryTransferData(qps);
        System.exit(0);
    }

    /**
     * 查询区块链上的余额然后更新到本地
     *
     * @param qps
     * @throws InterruptedException
     */
    public void queryAccount(BigInteger qps) throws InterruptedException {

        // qps速率控制器
        RateLimiter rateLimiter = RateLimiter.create(qps.intValue());
        // 已经查询成功的数量
        AtomicInteger querySuccess = new AtomicInteger(0);
        // 总用户
        int userSize = dagUserInfo.size();

        // 查询每个用户
        for (int i = 0; i < userSize; i++) {

            // 是否允许发送
            rateLimiter.acquire();

            // 用户下标
            final int userIndex = i;

            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // 得到当前用户信息
                                        DagTransferUser user = dagUserInfo.getDTU(userIndex);

                                        // 查询用户余额
                                        BigInteger balance = parallelOk.balanceOf(user.getUser());

                                        // 设置本地用户的余额
                                        user.setAmount(balance);

                                        // 已经查询成功的数量
                                        int all = querySuccess.incrementAndGet();
                                        if (all >= userSize) {
                                            System.out.println(
                                                    "==================================================================== "
                                                            + dateFormat.format(new Date())
                                                            + " query account finished");
                                        }

                                    } catch (ContractException exception) {
                                        logger.warn(
                                                "queryAccount for {} failed, error info: {}",
                                                dagUserInfo.getDTU(userIndex).getUser(),
                                                exception.getMessage());
                                        // System.exit(0);
                                    }
                                }
                            });
        }

        // 等待所有查询完成
        while (querySuccess.intValue() < userSize) {
            Thread.sleep(50);
        }

        System.exit(0);
    }
}
