package org.fisco.bcos.sdk.demo.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class Generator {
    /**
     * 得到需要生成的用户数
     *
     * @return
     */
    public static int getGi() {
        return gi;
    }

    // 需要生成的用户数
    private static int gi = 0;

    /**
     * 生成交易测试用例，包括冲突交易和非冲突交易
     *
     * @param total 交易的总量
     * @param conflictRate 冲突率，取值范围 0 - 10，默认值为 0
     * @param groups 分组数，只针对冲突的测试用例，默认为 1
     */
    public static int[][][] generateTransactionTestCases(
            int total, Integer conflictRate, Integer groups) throws IOException {
        gi = 0;
        conflictRate = conflictRate == null ? 0 : conflictRate;
        groups = groups == null ? 0 : groups;
        // 当冲突率为 0 时，组数必须为 0
        if (conflictRate == 0) {
            groups = 0;
        }
        // 当组数为 0 但冲突率不为 0 时，将组数置为 1，或者冲突的测试用例数不能构成 [groups] 组时将组数置为 1
        if (!ifEnoughForPreGroup(total, conflictRate, groups)) {
            groups = 1;
        }
        // 默认最后一组为非冲突测试用例
        int[][][] transactions = new int[groups + 1][][];
        // 生成冲突交易
        generateConflictTransactionTestCases(total, conflictRate, groups, transactions);
        // 生成非冲突交易
        generateNonConflictTransactionTestCases(total - total * conflictRate / 100, transactions);
        saveToFile(transactions);
        return transactions;
    }

    private static boolean ifEnoughForPreGroup(int total, Integer conflictRate, Integer groups) {
        if (groups == 0 && conflictRate == 0) return true;
        return groups != 0 && (total * conflictRate / 100) > groups;
    }

    /**
     * 将测试集写入到文件
     *
     * @param trans
     * @throws IOException
     */
    public static void saveToFile(int[][][] trans) throws IOException {
        int index = 0;
        File transactions = new File("/home/shijianfeng/fisco/java-sdk-demo/user/transactions");
        File transactionsByGroup =
                new File("/home/shijianfeng/fisco/java-sdk-demo/user/transactionsPerGroup");
        if (transactions.exists()) {
            transactions.delete();
        }
        if (transactionsByGroup.exists()) {
            transactionsByGroup.delete();
        }
        transactions.createNewFile();
        transactionsByGroup.createNewFile();
        try (BufferedWriter t = new BufferedWriter(new FileWriter(transactions));
                BufferedWriter tbg = new BufferedWriter(new FileWriter(transactionsByGroup))) {
            for (int i = 0; i < trans.length; i++) {
                tbg.write(
                        "=========================== group "
                                + (i + 1)
                                + "===========================\n");
                for (int j = 0; j < trans[i].length; j++) {
                    int from = trans[i][j][0];
                    int to = trans[i][j][1];
                    t.write((index++) + "\t" + "[" + from + ", " + to + "]\n");
                    tbg.write((index++) + "\t" + "[" + from + ", " + to + "]\n");
                }
            }
            t.flush();
            tbg.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
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
        if (conflictRate == 0) {
            return;
        }
        int totalOfConflict = total * conflictRate / 100;
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
     * @param totalOfConflictTestCasesPerGroup 该组的冲突交易总量
     * @return 该组的冲突交易数组
     */
    public static int[][] generateConflictTransactionTestCasesPerGroup(
            int totalOfConflictTestCasesPerGroup) {
        // a, b, c 为根据等差数列求和公式构造的一元二次方程参数，设层级为 n，Sn 为到第 n 层的累加和, d 为公差，Sn = n * a1 + n * (n - 1) * d
        // / 2
        // 在这里就为 Sn = n + n * (n - 1) / 2， 这里的 Sn 即为 函数参数 totalOfConflictTestCasesPerGroup，则一元二次方程为
        // n * n + n - 2 * totalOfConflictTestCasesPerGroup = 0
        // 其中 a = 1, b = 1, c = -2 * totalOfConflictTestCasesPerGroup
        int a = 1, b = 1, c = -2 * totalOfConflictTestCasesPerGroup;
        // 根据求根公式求出正数根也就是需要构造的层数，向上取整
        double res = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
        // 根据total构造的倒二叉图不是满倒二叉图的情况下，最上面一层需要除去的交易数，默认为0
        int removedItemsOnFirstLayer = 0;
        // 总层数
        int layers = (int) Math.ceil(res);
        System.out.println(res);
        System.out.println(layers);
        // 求出需要在最上面一层除去的多余的交易数
        if (Math.ceil(res) != res) {
            removedItemsOnFirstLayer =
                    (layers * layers + layers) / 2 - totalOfConflictTestCasesPerGroup;
        }
        // 交易测试用例中间结果的存储数组，第一维代表哪一层，第二维代表该层哪个交易，第三维代表该交易的信息
        int[][] conflictTransactionContainer = new int[layers][2];
        // 交易测试用例结果数组
        int[][] conflictTransactions = new int[totalOfConflictTestCasesPerGroup][2];
        int idx = 0;
        // 生成最上面一层的交易信息
        for (int i = 0; i < layers; i++) {
            conflictTransactionContainer[i][0] = gi++;
            conflictTransactionContainer[i][1] = gi++;
        }
        // 将最后一层的交易信息放入交易测试用例结果数组中，由于可能不是满倒二叉树，所以最后一层要单独处理
        for (int i = 0; i < layers - removedItemsOnFirstLayer; i++) {
            conflictTransactions[idx][0] = conflictTransactionContainer[i][0];
            conflictTransactions[idx][1] = conflictTransactionContainer[i][1];
            idx++;
        }
        // 生成剩余层级的交易信息
        for (int i = layers - 1; i >= 1; i--) {
            for (int j = 0; j < i; j++) {
                conflictTransactionContainer[j][1] = conflictTransactionContainer[j + 1][1];
                conflictTransactions[idx][0] = conflictTransactionContainer[j][0];
                conflictTransactions[idx][1] = conflictTransactionContainer[j][1];
                idx++;
            }
        }
        for (int i = 0; i < totalOfConflictTestCasesPerGroup; i++)
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
        // 非冲突测试用例作为最后一组
        int lastGroup = transactions.length - 1;
        transactions[lastGroup] = new int[totalOfNonConflictTransactions][2];
        for (int i = 0; i < totalOfNonConflictTransactions; i++) {
            transactions[lastGroup][i][0] = gi++;
            transactions[lastGroup][i][1] = gi++;
        }
    }
}
