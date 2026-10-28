package LAB3;

import LAB1.Account;
import LAB1.Customer;
import LAB1.Transaction;
import LAB2.DBConnection;
import LAB2.TransactionDAO;
import LAB2.TransactionDAOImp;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exercise 3.3 - transaction reports:
 *   1. export the transaction history of an account to a CSV file
 *   2. daily summary of a customer's transactions
 *   3. customer statement for a period, with opening, running and closing balances
 */
public class ReportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final TransactionDAO transactionDAO = new TransactionDAOImp();

    // =====================================================================
    //  1. EXPORT TRANSACTION HISTORY TO CSV
    // =====================================================================

    /** Writes every transaction of the account to reports/<account>_history_<date-time>.csv and returns the file. */
    public Path exportHistoryToCsv(Account account) throws SQLException, IOException {
        List<Transaction> history = transactionDAO.findByAccount(account.getAccountId());

        Path folder = Path.of("reports");
        Files.createDirectories(folder);
        Path file = folder.resolve(account.getAccountId() + "_history_"
                + LocalDateTime.now().format(FILE_STAMP) + ".csv");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("transaction_id,date,account_id,type,amount_rwf,reference_id");
            for (Transaction t : history) {
                out.println(t.getTransactionId() + ","
                        + csv(t.getTimestamp().format(DATE_TIME)) + ","
                        + csv(t.getAccountId()) + ","
                        + csv(t.getTransactionType()) + ","
                        + String.format("%.2f", t.getAmount()) + ","
                        + csv(t.getReferenceId()));
            }
        }
        System.out.println("[OK] " + history.size() + " transaction(s) exported.");
        return file.toAbsolutePath();
    }

    // =====================================================================
    //  2. DAILY TRANSACTION SUMMARY
    // =====================================================================

    /** Counts and totals of deposits and withdrawals per account, for one day. */
    public void printDailySummary(Customer customer, LocalDate day) throws SQLException {
        String sql = """
                SELECT t.account_id, t.transaction_type, COUNT(*) AS how_many, SUM(t.amount) AS total
                FROM transactions t
                JOIN accounts a ON a.account_id = t.account_id
                WHERE a.customer_id = ?
                  AND t.created_at >= ?
                  AND t.created_at <  ?
                GROUP BY t.account_id, t.transaction_type
                ORDER BY t.account_id, t.transaction_type
                """;

        System.out.println();
        System.out.println("DAILY SUMMARY - " + customer.getCustomerName() + " - " + day);
        System.out.printf("%-10s %6s %18s %6s %18s %18s%n",
                "ACCOUNT", "DEP #", "DEPOSITS", "WD #", "WITHDRAWALS", "NET");

        String currentAccount = null;
        int depCount = 0, wdCount = 0, totalDepCount = 0, totalWdCount = 0;
        double depTotal = 0, wdTotal = 0, allDep = 0, allWd = 0;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customer.getCustomerId());
            statement.setObject(2, day.atStartOfDay());              // 00:00 of that day
            statement.setObject(3, day.plusDays(1).atStartOfDay());  // 00:00 of the next day

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String accountId = rs.getString("account_id");
                    if (currentAccount != null && !currentAccount.equals(accountId)) {
                        printSummaryRow(currentAccount, depCount, depTotal, wdCount, wdTotal);
                        depCount = wdCount = 0;
                        depTotal = wdTotal = 0;
                    }
                    currentAccount = accountId;

                    int howMany = rs.getInt("how_many");
                    double total = rs.getDouble("total");
                    if (rs.getString("transaction_type").equals("DEPOSIT")) {
                        depCount += howMany; depTotal += total;
                        totalDepCount += howMany; allDep += total;
                    } else {
                        wdCount += howMany; wdTotal += total;
                        totalWdCount += howMany; allWd += total;
                    }
                }
            }
        }

        if (currentAccount == null) {
            System.out.println("   No transactions on " + day + ".");
            return;
        }
        printSummaryRow(currentAccount, depCount, depTotal, wdCount, wdTotal);
        System.out.println("-".repeat(81));
        printSummaryRow("TOTAL", totalDepCount, allDep, totalWdCount, allWd);
    }

    private void printSummaryRow(String label, int depCount, double depTotal, int wdCount, double wdTotal) {
        System.out.printf("%-10s %6d %18s %6d %18s %18s%n",
                label, depCount, money(depTotal), wdCount, money(wdTotal), signed(depTotal - wdTotal));
    }

    // =====================================================================
    //  3. CUSTOMER TRANSACTION STATEMENT
    // =====================================================================

    /** For each account: opening balance, every transaction in the period with a running balance, closing balance. */
    public void printStatement(Customer customer, List<Account> accounts, LocalDate from, LocalDate to)
            throws SQLException {
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("                         IgirePay - CUSTOMER STATEMENT");
        System.out.println("=".repeat(80));
        System.out.println("Customer : " + customer.getCustomerName() + " (" + customer.getCustomerId() + ")");
        System.out.println("Email    : " + customer.getEmail() + "    Phone: " + customer.getPhoneNumber());
        System.out.println("Period   : " + from + "  to  " + to);
        System.out.println("Printed  : " + LocalDateTime.now().format(DATE_TIME));

        if (accounts.isEmpty()) {
            System.out.println();
            System.out.println("No accounts.");
            return;
        }

        for (Account account : accounts) {
            List<Transaction> all = transactionDAO.findByAccount(account.getAccountId());

            // Work backwards from today's balance:
            //   closing = current balance - everything that happened after the period
            //   opening = closing        - everything that happened inside the period
            double afterPeriod = 0, insidePeriod = 0;
            for (Transaction t : all) {
                LocalDate day = t.getTimestamp().toLocalDate();
                if (day.isAfter(to)) {
                    afterPeriod += signedAmount(t);
                } else if (!day.isBefore(from)) {
                    insidePeriod += signedAmount(t);
                }
            }
            double closing = account.getBalance() - afterPeriod;
            double opening = closing - insidePeriod;

            System.out.println();
            System.out.println("Account " + account.getAccountId() + " (" + account.getAccountType() + ")");
            System.out.printf("%-17s %-14s %-11s %15s %16s%n", "DATE", "REFERENCE", "TYPE", "AMOUNT", "BALANCE");
            System.out.printf("%-17s %-14s %-11s %15s %16s%n", "", "", "OPENING", "", money(opening));

            double running = opening;
            int count = 0;
            double in = 0, out = 0;
            for (Transaction t : all) {
                LocalDate day = t.getTimestamp().toLocalDate();
                if (day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                running += signedAmount(t);
                count++;
                if (t.getTransactionType().equals("DEPOSIT")) in += t.getAmount(); else out += t.getAmount();
                System.out.printf("%-17s %-14s %-11s %15s %16s%n",
                        t.getTimestamp().format(DATE_TIME), t.getReferenceId(), t.getTransactionType(),
                        signed(signedAmount(t)), money(running));
            }
            System.out.printf("%-17s %-14s %-11s %15s %16s%n", "", "", "CLOSING", "", money(closing));
            System.out.println("   " + count + " transaction(s)  |  money in: " + money(in)
                    + "  |  money out: " + money(out));
        }
        System.out.println("=".repeat(80));
    }

    // =====================================================================
    //  helpers
    // =====================================================================

    /** + for deposits, - for withdrawals. */
    private double signedAmount(Transaction t) {
        return t.getTransactionType().equals("DEPOSIT") ? t.getAmount() : -t.getAmount();
    }

    private String money(double amount) {
        return String.format("%,.2f", amount);
    }

    private String signed(double amount) {
        return (amount >= 0 ? "+" : "-") + money(Math.abs(amount));
    }

    /** Puts a CSV value in quotes if it contains a comma, a quote or a line break. */
    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}