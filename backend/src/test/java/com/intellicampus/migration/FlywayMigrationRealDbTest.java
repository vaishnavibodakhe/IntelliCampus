package com.intellicampus.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class FlywayMigrationRealDbTest {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3307/intellicampus_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    private static final String DB_USER = "campus_admin";
    private static final String DB_PASS = "SecureCampusPassword2026!";

    @Test
    @DisplayName("Verify Flyway V1..V3 runs cleanly on real MySQL and persisting PENDING student succeeds")
    void testFlywayMigrationsAndPendingStudentPersistence() throws Exception {
        System.out.println("=== 1. Executing Flyway V1..V3 on real MySQL (localhost:3307/intellicampus_db) ===");
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        flyway.repair();
        int migrationsApplied = flyway.migrate().migrationsExecuted;
        System.out.println("Flyway migrations executed: " + migrationsApplied);

        MigrationInfo[] applied = flyway.info().applied();
        for (MigrationInfo info : applied) {
            System.out.println("Applied migration: " + info.getVersion() + " - " + info.getDescription() + " [" + info.getState() + "]");
        }

        assertTrue(applied.length >= 3, "Expected at least V1..V3 migrations to be applied");
        assertEquals("1", applied[0].getVersion().getVersion());
        assertEquals("2", applied[1].getVersion().getVersion());
        assertEquals("3", applied[2].getVersion().getVersion());

        System.out.println("=== 2. Verifying users.status column definition and PENDING persistence ===");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            // Check column definition of users.status
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT " +
                         "FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = 'intellicampus_db' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'status'")) {
                assertTrue(rs.next(), "Column 'status' must exist in table 'users'");
                String dataType = rs.getString("DATA_TYPE");
                int maxLen = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                String defVal = rs.getString("COLUMN_DEFAULT");
                System.out.println("users.status schema: data_type=" + dataType + ", max_length=" + maxLen + ", default=" + defVal);
                assertEquals("varchar", dataType.toLowerCase());
                assertTrue(maxLen >= 10, "users.status VARCHAR length must be at least 10 (fits 'PENDING')");
            }

            // Clean up any test user with user_code 'test.student.pending'
            try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM users WHERE user_code = 'test.student.pending'")) {
                delStmt.executeUpdate();
            }

            // Insert a student user with status 'PENDING'
            String insertSql = "INSERT INTO users (user_code, role, name, email, password_hash, must_change_password, status, failed_attempts) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, "test.student.pending");
                insertStmt.setString(2, "STUDENT");
                insertStmt.setString(3, "Pending Verification Student");
                insertStmt.setString(4, "pending.student@intellicampus.edu");
                insertStmt.setString(5, "$2a$10$hashedPasswordPendingTest2026");
                insertStmt.setBoolean(6, true);
                insertStmt.setString(7, "PENDING"); // Must fit in VARCHAR(10)
                insertStmt.setInt(8, 0);

                int affected = insertStmt.executeUpdate();
                assertEquals(1, affected, "1 user record must be inserted");

                try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    long userId = keys.getLong(1);
                    System.out.println("Inserted PENDING student user with generated id: " + userId);

                    // Read back from real MySQL database
                    try (PreparedStatement selectStmt = conn.prepareStatement("SELECT id, user_code, email, status FROM users WHERE id = ?")) {
                        selectStmt.setLong(1, userId);
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            assertTrue(rs.next(), "User record must be readable from DB");
                            assertEquals("test.student.pending", rs.getString("user_code"));
                            assertEquals("pending.student@intellicampus.edu", rs.getString("email"));
                            assertEquals("PENDING", rs.getString("status"));
                            System.out.println("Successfully verified student persisted in real MySQL with status: " + rs.getString("status"));
                        }
                    }
                }
            }
        }
    }
}
