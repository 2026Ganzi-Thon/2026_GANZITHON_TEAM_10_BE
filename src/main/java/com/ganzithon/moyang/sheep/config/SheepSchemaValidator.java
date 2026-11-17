package com.ganzithon.moyang.sheep.config;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Refuse unsafe MySQL-generated case-insensitive idempotency keys; this performs no DDL. */
@Component
public class SheepSchemaValidator implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    public SheepSchemaValidator(DataSource dataSource,JdbcTemplate jdbc){this.dataSource=dataSource;this.jdbc=jdbc;}
    @Override public void run(ApplicationArguments args) throws Exception {
        try(var connection=dataSource.getConnection()){
            if(!"MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))return;
        }
        String collation=jdbc.queryForObject("select collation_name from information_schema.columns where table_schema=database() and table_name='sheep_transactions' and column_name='reference_id'",String.class);
        if(collation==null || !collation.toLowerCase(Locale.ROOT).endsWith("_bin"))
            throw new IllegalStateException("sheep_transactions.reference_id requires binary collation. Apply reviewed db/sheep-mysql.sql or a migration; automatic schema update is insufficient.");
    }
}
