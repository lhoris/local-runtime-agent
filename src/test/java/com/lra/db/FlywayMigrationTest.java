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
            "TB_M26_AGENT", "TB_M26_PROCESS_CONFIG", "TB_M26_MODEL_PROCESS", "TB_M26_COMMAND",
            "TB_M26_MODEL_PARAMETER", "TB_M26_EXECUTION_LOG"
        };
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE UPPER(table_name) = UPPER(?)", Integer.class, table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }

        Integer views = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.views "
                + "WHERE UPPER(table_name) = 'V_M26_MODEL_STATUS'", Integer.class);
        assertThat(views).as("view V_M26_MODEL_STATUS exists").isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM V_M26_MODEL_STATUS", Integer.class))
            .isZero();
    }
}
