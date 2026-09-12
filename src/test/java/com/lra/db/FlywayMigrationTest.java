package com.lra.db;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationCreatesAllTablesAndView() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String[] tables = {
            "agent_info", "process_config", "agent_status", "commands",
            "model_parameters", "execution_log"
        };
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE UPPER(table_name) = UPPER(?)", Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        Integer views = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.views "
                + "WHERE UPPER(table_name) = 'MODEL_STATUS'", Integer.class);
        assertThat(views).as("view model_status exists").isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM model_status", Integer.class))
            .isZero();
    }
}
