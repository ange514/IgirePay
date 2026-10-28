package LAB2;

import LAB1.Transaction;


import java.sql.SQLException;
import java.util.List;

public interface TransactionDAO {


    boolean CreateTable()throws SQLException;
    //Transaction registerTransaction(Transaction transaction) throws SQLException;
    List<Transaction> findByAccount(String id) throws SQLException;
    List<Transaction> findAll() throws SQLException;
    //boolean updateTransaction(Account account) throws SQLException;
    //boolean deleteTransaction(String id) throws SQLException;
}