package org.fisco.bcos.sdk.demo.util;

import java.util.Arrays;

public class Generator {
    static int gi = 0;
    /**
     * 生成交易测试用例，包括冲突交易和非冲突交易
     *
     * @param total 交易的总量
     * @param conflictRate 冲突率，取值范围 0 - 10，默认值为 0
     * @param groups 分组数，只针对冲突的测试用例，默认为 1
     */
    public static int[][][] generateTransactionTestCases(
            int total, Integer conflictRate, Integer groups) {
        gi = 0;
        conflictRate = conflictRate == null ? 0 : conflictRate;
        groups = groups == null ? 1 : groups;
        int[][][] transactions = new int[groups + 1][][];
        // 生成冲突交易
        generateConflictTransactionTestCases(total, conflictRate, groups, transactions);
        // 生成非冲突交易
        generateNonConflictTransactionTestCases(total - total * conflictRate / 10, transactions);
        return transactions;
    }

    /**
     * @param total 交易总量
     * @param conflictRate 冲突率
     * @param groups 分组数
     * @param transactions
     * @return 冲突交易数组
     */
    private static void generateConflictTransactionTestCases(
            int total, Integer conflictRate, Integer groups, int[][][] transactions) {
        int totalOfConflict = total * conflictRate / 10;
        // 计算每组的平均冲突交易数量，向上取整
        int conflictTransactionsPerGroup = (int) Math.ceil((double) totalOfConflict / groups);
        // 最后一个组需要移除的多余的冲突交易数，当不需要移除的时候就是 0
        int lastGroupToRemove = conflictTransactionsPerGroup * groups - totalOfConflict;
        //        // 交易组信息数组，第一维代表组，第二维代表组里的某个交易，第三维代表该交易的信息
        //        int[][][] conflictTransactionGroups = new int[groups][][];
        for (int i = 0; i < groups; i++) {
            System.out.println("[group " + (i + 1) + "] ####################### start");
            if (i == groups - 1)
                // 最后一组需要特判
                transactions[i] =
                        generateConflictTransactionTestCasesPerGroup(
                                conflictTransactionsPerGroup - lastGroupToRemove);
            else
                transactions[i] =
                        generateConflictTransactionTestCasesPerGroup(conflictTransactionsPerGroup);
            System.out.println();

            System.out.println("[group " + (i + 1) + "] ####################### start");
        }
    }
    /**
     * 分组生成冲突交易测试用例，测试用例之间的关系是倒二叉图，由下到上层级递增，最下面一层为第 1 层，第 i 层拥有 i 个交易，同层的交易没有冲突
     * 这样在确定冲突的交易数量之后就可以根据等差数列求和公式求出应该构造几层的倒二叉图，如果冲突的交易数构造的倒二叉图不是一个满倒二叉图的
     * 话，先构造一个满倒二叉图然后在最上面一层除去多余的交易
     *
     * @param total 该组的冲突交易总量
     * @return 该组的冲突交易数组
     */
    public static int[][] generateConflictTransactionTestCasesPerGroup(int total) {
        // a, b, c 为根据等差数列求和公式构造的一元二次方程参数，设层级为 n，Sn 为到第 n 层的累加和, d 为公差，Sn = n * a1 + n * (n - 1) * d
        // / 2
        // 在这里就为 Sn = n + n * (n - 1) / 2， 这里的 Sn 即为 函数参数 total，则一元二次方程为 n * n + n - 2 * total = 0
        // 其中 a = 1, b = 1, c = -2 * total
        int a = 1, b = 1, c = -2 * total;
        // 根据求根公式求出正数根也就是需要构造的层数，向上取整
        double res = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
        // 根据total构造的倒二叉图不是满倒二叉图的情况下，最上面一层需要除去的交易数，默认为0
        int removedItemsOnFirstLayer = 0;
        // 总层数
        int layers = (int) Math.ceil(res);
        System.out.println(res);
        System.out.println(layers);
        // 求出需要在最后一层除去的多余的交易数
        if (Math.ceil(res) != res) {
            removedItemsOnFirstLayer = (layers * layers + layers) / 2 - total;
        }
        // 交易测试用例中间结果的存储数组，第一维代表哪一层，第二维代表该层哪个交易，第三维代表该交易的信息
        int[][][] conflictTransactionContainer = new int[total][total][2];
        // 生成最上面一层的交易信息
        for (int i = 0; i < layers; i++) {
            conflictTransactionContainer[layers - 1][i][0] = gi++;
            conflictTransactionContainer[layers - 1][i][1] = gi++;
        }
        // 生成剩余层级的交易信息
        for (int i = layers - 1; i >= 1; i--) {
            for (int j = 0; j < i; j++) {
                conflictTransactionContainer[i - 1][j][0] = conflictTransactionContainer[i][j][0];
                conflictTransactionContainer[i - 1][j][1] =
                        conflictTransactionContainer[i][j + 1][1];
            }
        }
        for (int i = layers; i >= 1; i--) {
            System.out.println("<Layer." + i + "> -----------------------------start");
            for (int j = 0; j < i; j++)
                System.out.println(Arrays.toString(conflictTransactionContainer[i - 1][j]));
            System.out.println("<Layer." + i + "> -----------------------------end");
        }
        // 交易测试用例结果数组
        int[][] conflictTransactions = new int[total][2];
        int idx = 0;
        // 将最后一层的交易信息放入交易测试用例结果数组中，由于可能不是满倒二叉树，所以最后一层要单独处理
        for (int i = 0; i < layers - removedItemsOnFirstLayer; i++) {
            conflictTransactions[idx++] = conflictTransactionContainer[layers - 1][i];
        }
        // 将其余层级的交易信息放入结果数组中
        for (int i = layers - 2; i >= 0; i--) {
            for (int j = 0; j <= i; j++)
                conflictTransactions[idx++] = conflictTransactionContainer[i][j];
        }
        System.out.println("print transaction array -----------------------------");
        for (int i = 0; i < total; i++)
            System.out.println(Arrays.toString(conflictTransactions[i]));
        return conflictTransactions;
    }

    /**
     * 生成非冲突交易测试用例
     *
     * @param totalOfNonConflictTransactions 非冲突交易数
     * @param transactions
     * @return 非冲突交易数组
     */
    private static void generateNonConflictTransactionTestCases(
            int totalOfNonConflictTransactions, int[][][] transactions) {
        int lastIndex = transactions.length - 1;
        transactions[lastIndex] = new int[totalOfNonConflictTransactions][2];
        for (int i = 0; i < totalOfNonConflictTransactions; i++) {
            transactions[lastIndex][i][0] = gi++;
            transactions[lastIndex][i][1] = gi++;
        }
    }
}
