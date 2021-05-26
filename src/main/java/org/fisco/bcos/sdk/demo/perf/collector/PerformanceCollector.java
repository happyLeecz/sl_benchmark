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
package org.fisco.bcos.sdk.demo.perf.collector;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.fisco.bcos.sdk.model.JsonRpcResponse;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceCollector {
    private static Logger logger = LoggerFactory.getLogger(PerformanceCollector.class);
    private AtomicLong less50 = new AtomicLong(0);
    private AtomicLong less100 = new AtomicLong(0);
    private AtomicLong less200 = new AtomicLong(0);
    private AtomicLong less400 = new AtomicLong(0);
    private AtomicLong less1000 = new AtomicLong(0);
    private AtomicLong less2000 = new AtomicLong(0);
    private AtomicLong timeout2000 = new AtomicLong(0);
    private AtomicLong totalCost = new AtomicLong(0);

    // 交易总数
    private Integer total = 0;
    // 收到的交易回执的数量
    private AtomicInteger received = new AtomicInteger(0);
    // 收到的错误的交易回执的数量
    private AtomicInteger error = new AtomicInteger(0);
    // 开始的时间戳
    private Long startTimestamp = System.currentTimeMillis();

    /**
     * 得到交易总数
     *
     * @return
     */
    public Integer getTotal() {
        return this.total;
    }

    /**
     * 设置交易总数
     *
     * @param total
     */
    public void setTotal(Integer total) {
        this.total = total;
    }

    /**
     * 得到收到的交易回执数量
     *
     * @return
     */
    public Integer getReceived() {

        return this.received.get();
    }

    /**
     * 设置收到的交易回执的数量
     *
     * @param received
     */
    public void setReceived(Integer received) {
        // getAndSet ：以原子方式设置为给定值，并返回以前的值。
        this.received.getAndSet(received);
    }

    /**
     * 设置开始时间戳
     *
     * @param startTimestamp
     */
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    /**
     * 得到开始时间戳
     *
     * @return
     */
    public Long getStartTimestamp() {
        return this.startTimestamp;
    }

    public void onRpcMessage(JsonRpcResponse response, Long cost) {
        try {
            boolean errorMessage = false;
            if (response.getError() != null && response.getError().getCode() != 0) {
                logger.warn("receive error jsonRpcResponse: {}", response.toString());
                errorMessage = true;
            }
            stat(errorMessage, cost);
        } catch (Exception e) {
            logger.error("onRpcMessage exception: {}", e.getMessage());
        }
    }

    /**
     * 处理回执
     *
     * @param receipt
     * @param cost 该交易耗费的时间
     */
    public void onMessage(TransactionReceipt receipt, Long cost) {
        try {

            // 标志该交易回执是否有错误信息
            boolean errorMessage = false;

            if (!receipt.isStatusOK()) {
                logger.error(
                        "error receipt, status: {}, output: {}, message: {}",
                        receipt.getStatus(),
                        receipt.getOutput(),
                        receipt.getMessage());
                // 存在错误信息
                errorMessage = true;
            }

            // 统计状态
            stat(errorMessage, cost);
        } catch (Exception e) {
            logger.error("error:", e);
        }
    }

    /**
     * 统计回执状态
     *
     * @param errorMessage
     * @param cost 该交易耗费的时间
     */
    public void stat(boolean errorMessage, Long cost) {

        // 处理错误的统计
        if (errorMessage) {
            error.addAndGet(1);
        }

        // 收到的数量
        if ((received.get() + 1) % (total / 10) == 0) {
            System.out.println(
                    "                                                       |received:"
                            + String.valueOf((received.get() + 1) * 100 / total)
                            + "%");
        }

        // 交易返回所耗费的时间分布，单位毫秒
        if (cost < 50) {
            less50.incrementAndGet();
        } else if (cost < 100) {
            less100.incrementAndGet();
        } else if (cost < 200) {
            less200.incrementAndGet();
        } else if (cost < 400) {
            less400.incrementAndGet();
        } else if (cost < 1000) {
            less1000.incrementAndGet();
        } else if (cost < 2000) {
            less2000.incrementAndGet();
        } else {
            timeout2000.incrementAndGet();
        }

        // 增加到总耗时
        totalCost.addAndGet(cost);

        // 收到的数量如果大于等于发送的总交易量
        if (received.incrementAndGet() >= total) {

            System.out.println("total");

            // 当前的时间戳
            long curr = System.currentTimeMillis();
            // 总耗时
            Long totalTime = curr - this.startTimestamp;

            System.out.println(">>>>>>>>>>>>>>>>>>>>>> start timestamp " + this.startTimestamp);
            System.out.println(">>>>>>>>>>>>>>>>>>>>>> current time " + curr);
            System.out.println(">>>>>>>>>>>>>>>>>>>>>> received " + received.intValue());
            System.out.println(">>>>>>>>>>>>>>>>>>>>>> total " + total + "\n");

            System.out.println("Total transactions:  " + String.valueOf(total));
            System.out.println("Total time: " + String.valueOf(totalTime) + "ms");
            System.out.println(
                    "TPS(include error requests): "
                            + String.valueOf(total / ((double) totalTime / 1000)));
            System.out.println(
                    "TPS(exclude error requests): "
                            + String.valueOf(
                                    (double) (total - error.get()) / ((double) totalTime / 1000)));
            System.out.println("Avg time cost: " + String.valueOf(totalCost.get() / total) + "ms");
            System.out.println(
                    "Error rate: "
                            + String.valueOf((error.get() / (double) received.get()) * 100)
                            + "%");

            System.out.println("Time area:");
            System.out.println(
                    "0    < time <  50ms   : "
                            + String.valueOf(less50)
                            + "  : "
                            + String.valueOf((double) less50.get() / total * 100)
                            + "%");
            System.out.println(
                    "50   < time <  100ms  : "
                            + String.valueOf(less100)
                            + "  : "
                            + String.valueOf((double) less100.get() / total * 100)
                            + "%");
            System.out.println(
                    "100  < time <  200ms  : "
                            + String.valueOf(less200)
                            + "  : "
                            + String.valueOf((double) less200.get() / total * 100)
                            + "%");
            System.out.println(
                    "200  < time <  400ms  : "
                            + String.valueOf(less400)
                            + "  : "
                            + String.valueOf((double) less400.get() / total * 100)
                            + "%");
            System.out.println(
                    "400  < time <  1000ms : "
                            + String.valueOf(less1000)
                            + "  : "
                            + String.valueOf((double) less1000.get() / total * 100)
                            + "%");
            System.out.println(
                    "1000 < time <  2000ms : "
                            + String.valueOf(less2000)
                            + "  : "
                            + String.valueOf((double) less2000.get() / total * 100)
                            + "%");
            System.out.println(
                    "2000 < time           : "
                            + String.valueOf(timeout2000)
                            + "  : "
                            + String.valueOf((double) timeout2000.get() / total * 100)
                            + "%");
        }
    }
}
