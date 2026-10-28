package LAB2;

import LAB1.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAOImp implements TransactionDAO {


    @Override
    public boolean CreateTable() throws SQLException {
        String createTransaction = """
            CREATE TABLE transactions (
                transaction_id   VARCHAR(200) PRIMARY KEY,
                account_id       VARCHAR(50)  NOT NULL,
                reference_id     VARCHAR(100) NOT NULL,
                transaction_type VARCHAR(50)  NOT NULL,
                amount           DECIMAL(20,4) NOT NULL,
                created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (account_id) REFERENCES accounts(account_id)
            )
            """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(createTransaction)) {
            statement.executeUpdate();
            return true;
        }
    }


    @Override
    public List<Transaction> findByAccount(String id) throws SQLException {
        String findTransaction = " select * from transactions where account_id = ? order by created_at ";
        List<Transaction> transaction = new ArrayList<>();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(findTransaction)) {
            statement.setString(1, id);          // ← fills the ? BEFORE running


            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Transaction transaction1 = new Transaction();
                    transaction1.setTransactionId(resultSet.getInt("transaction_id"));
                    transaction1.setTransactionType(resultSet.getString("transaction_type"));
                    transaction1.setReferenceId(resultSet.getString("reference_id"));
                    transaction1.setAmount(resultSet.getDouble("amount"));
                    transaction1.setTimestamp(resultSet.getObject("created_at", java.time.LocalDateTime.class));
                    transaction1.setAccountId(resultSet.getString("account_id"));
                    transaction.add(transaction1);
                }
            }
        }
        return transaction;
    }

    @Override
    public List<Transaction> findAll() throws SQLException {
        return List.of();
    }





    public static void main(String[] args) throws SQLException {

        TransactionDAOImp transaction = new TransactionDAOImp();
        // transaction.CreateTable();
        transaction.findByAccount("1");
    }}