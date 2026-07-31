package com.familya.identity.adapter.out.persistence;

import com.familya.identity.application.port.out.RegistrationRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JdbcRegistrationRateLimiter implements RegistrationRateLimiter {

    private final NamedParameterJdbcTemplate jdbc;
    private final int limitPerHour;

    public JdbcRegistrationRateLimiter(NamedParameterJdbcTemplate jdbc,
                                       @Value("${familya.identity.registration-per-ip-per-hour:5}") int limitPerHour) {
        this.jdbc = jdbc;
        this.limitPerHour = limitPerHour;
    }

    @Override
    public boolean tryRegister(String ipAddress, Instant now) {
        Instant windowStart = now.truncatedTo(ChronoUnit.HOURS);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM registration_attempt WHERE ip_address = :ip AND bucket_hour = :b",
                new MapSqlParameterSource().addValue("ip", ipAddress).addValue("b", Timestamp.from(windowStart)),
                Integer.class);
        if (count != null && count >= limitPerHour) {
            return false;
        }
        jdbc.update(
                "INSERT INTO registration_attempt (ip_address, bucket_hour, attempted_at) VALUES (:ip, :b, :n)",
                new MapSqlParameterSource()
                        .addValue("ip", ipAddress)
                        .addValue("b", Timestamp.from(windowStart))
                        .addValue("n", Timestamp.from(now)));
        return true;
    }
}
