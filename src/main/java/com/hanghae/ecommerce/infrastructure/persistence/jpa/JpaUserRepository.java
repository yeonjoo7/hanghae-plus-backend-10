package com.hanghae.ecommerce.infrastructure.persistence.jpa;

import com.hanghae.ecommerce.domain.user.User;
import com.hanghae.ecommerce.domain.user.UserState;
import com.hanghae.ecommerce.domain.user.UserType;
import com.hanghae.ecommerce.domain.user.Point;
import com.hanghae.ecommerce.domain.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper userRowMapper = new UserRowMapper();
    
    public JpaUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    @Transactional
    public User save(User user) {
        String sql = """
            INSERT INTO users (id, email, status, type, name, phone, available_point, used_point, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                email = VALUES(email),
                status = VALUES(status),
                type = VALUES(type),
                name = VALUES(name),
                phone = VALUES(phone),
                available_point = VALUES(available_point),
                used_point = VALUES(used_point),
                updated_at = NOW()
            """;
        
        jdbcTemplate.update(sql,
            user.getId(),
            user.getEmail(),
            user.getState().name(),
            user.getType().name(),
            user.getName(),
            user.getPhone(),
            user.getAvailablePoint().getValue(),
            user.getUsedPoint().getValue()
        );
        
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, id);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, email);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        String sql = "SELECT * FROM users WHERE phone = ?";
        List<User> users = jdbcTemplate.query(sql, userRowMapper, phone);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    @Override
    public List<User> findByState(UserState state) {
        String sql = "SELECT * FROM users WHERE status = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper, state.name());
    }

    @Override
    public List<User> findActiveUsers() {
        return findByState(UserState.NORMAL);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM users WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByPhone(String phone) {
        String sql = "SELECT COUNT(*) FROM users WHERE phone = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, phone);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countByState(UserState state) {
        String sql = "SELECT COUNT(*) FROM users WHERE status = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, state.name());
        return count != null ? count : 0;
    }

    
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            return User.restore(
                rs.getLong("id"),
                rs.getString("email"),
                UserState.valueOf(rs.getString("status")),
                UserType.valueOf(rs.getString("type")),
                rs.getString("name"),
                rs.getString("phone"),
                Point.of(rs.getInt("available_point")),
                Point.of(rs.getInt("used_point")),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime()
            );
        }
    }
}