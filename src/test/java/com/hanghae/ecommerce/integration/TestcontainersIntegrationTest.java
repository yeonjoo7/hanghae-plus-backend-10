package com.hanghae.ecommerce.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.DockerClientFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testcontainers 기반 통합 테스트
 * 
 * MySQL 컨테이너를 사용하여 실제 데이터베이스 연결 및 기본 쿼리 실행을 검증합니다.
 * Docker가 실행 중일 때만 테스트가 실행됩니다.
 */
@Testcontainers
@DisplayName("Testcontainers 통합 테스트")
@Disabled
class TestcontainersIntegrationTest {

    @Container
    private static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    private static DataSource dataSource;

    @BeforeAll
    static void checkDockerAvailability() {
        // Docker 데몬이 실행 중인지 확인
        try {
            DockerClientFactory.instance().client();
            System.out.println("Docker is available and running");

            // MySQL 컨테이너 시작
            mysql.start();

            // DataSource 생성
            dataSource = DataSourceBuilder.create()
                    .url(mysql.getJdbcUrl())
                    .username(mysql.getUsername())
                    .password(mysql.getPassword())
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .build();

        } catch (Exception e) {
            assumeTrue(false, "Docker is not available or not running: " + e.getMessage());
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
            try (ResultSet resultSet = statement
                    .executeQuery("SELECT * FROM test_user WHERE email = 'test@example.com'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("email")).isEqualTo("test@example.com");
                assertThat(resultSet.getString("name")).isEqualTo("테스트 사용자");
            }
        }
    }
}
