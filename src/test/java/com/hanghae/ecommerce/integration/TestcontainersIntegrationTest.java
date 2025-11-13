package com.hanghae.ecommerce.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 기반 통합 테스트
 * 
 * MySQL 컨테이너를 사용하여 실제 데이터베이스 연결 및 기본 쿼리 실행을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Testcontainers 통합 테스트")
class TestcontainersIntegrationTest {

    @Container
    private static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private DataSource dataSource;
    
    @BeforeEach
    void setUp() throws Exception {
        // 각 테스트 실행 전에 테스트용 테이블들이 없는지 확인하고 삭제
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS test_table");
            statement.execute("DROP TABLE IF EXISTS test_user");
        }
    }
    
    @AfterEach
    void tearDown() throws Exception {
        // 각 테스트 실행 후 생성된 테스트용 테이블들을 삭제
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS test_table");
            statement.execute("DROP TABLE IF EXISTS test_user");
        }
    }

    @Test
    @DisplayName("MySQL 컨테이너 연결 성공")
    void testDatabaseConnection() throws java.sql.SQLException {
        // when
        try (Connection connection = dataSource.getConnection()) {
            // then
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    @DisplayName("MySQL 버전 확인")
    void testMySQLVersion() throws Exception {
        // when
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            
            // then
            assertThat(resultSet.next()).isTrue();
            String version = resultSet.getString(1);
            assertThat(version).isNotNull();
            assertThat(version).startsWith("8.0");
        }
    }

    @Test
    @DisplayName("데이터베이스 생성 및 사용 가능")
    void testDatabaseCreation() throws Exception {
        // when
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            
            // then
            assertThat(resultSet.next()).isTrue();
            String databaseName = resultSet.getString(1);
            assertThat(databaseName).isEqualTo("testdb");
        }
    }

    @Test
    @DisplayName("테이블 생성 및 조회 가능")
    void testTableCreation() throws Exception {
        // given
        String createTableSql = """
            CREATE TABLE test_table (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """;

        // when
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(createTableSql);
            
            // 테이블 존재 확인
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'testdb' AND table_name = 'test_table'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("INSERT 및 SELECT 쿼리 실행 가능")
    void testInsertAndSelect() throws Exception {
        // given
        String createTableSql = """
            CREATE TABLE test_user (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(255) NOT NULL,
                name VARCHAR(100) NOT NULL
            )
            """;

        // when
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(createTableSql);
            statement.execute("INSERT INTO test_user (email, name) VALUES ('test@example.com', '테스트 사용자')");
            
            // then
            try (ResultSet resultSet = statement.executeQuery("SELECT * FROM test_user WHERE email = 'test@example.com'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("email")).isEqualTo("test@example.com");
                assertThat(resultSet.getString("name")).isEqualTo("테스트 사용자");
            }
        }
    }
}

