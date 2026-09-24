import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * MySQLLoader (Load layer)
 *
 * Loads parent tables (products, sales_teams, accounts) before the
 * dependent sales_pipeline table, using JDBC batch inserts with
 * PreparedStatement for both performance and protection against SQL
 * injection.
 *
 * Foreign-key checks are briefly disabled only while loading accounts,
 * since its subsidiary_of column can reference another account row that
 * has not been inserted yet within the same batch; checks are re-enabled
 * immediately afterwards.
 *
 * The loader truncates all four tables before every run, making the ETL
 * process idempotent and safe to re-run.
 */
public class MySQLLoader {

    private final String url;
    private final String user;
    private final String password;

    public MySQLLoader(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Truncates all four tables in FK-safe order. Safe to re-run the ETL any number of times. */
    public void truncateAll() throws SQLException {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            st.execute("TRUNCATE TABLE sales_pipeline");
            st.execute("TRUNCATE TABLE accounts");
            st.execute("TRUNCATE TABLE products");
            st.execute("TRUNCATE TABLE sales_teams");
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        System.out.println("All tables truncated - ETL run is idempotent.");
    }

    public int loadProducts(List<Map<String, String>> products) throws SQLException {
        String sql = "INSERT INTO products (product, series, sales_price) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, String> p : products) {
                ps.setString(1, p.get("product"));
                ps.setString(2, p.get("series"));
                ps.setBigDecimal(3, new java.math.BigDecimal(p.get("sales_price")));
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    public int loadSalesTeams(List<Map<String, String>> agents) throws SQLException {
        String sql = "INSERT INTO sales_teams (sales_agent, manager, regional_office) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, String> a : agents) {
                ps.setString(1, a.get("sales_agent"));
                ps.setString(2, a.get("manager"));
                ps.setString(3, a.get("regional_office"));
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    /**
     * Loads accounts with foreign-key checks briefly disabled, since a
     * child row's subsidiary_of may reference a parent account row that
     * appears later in the same batch (accounts.xml is not guaranteed to
     * list parent companies before their subsidiaries).
     */
    public int loadAccounts(List<Map<String, String>> accounts) throws SQLException {
        String sql = "INSERT INTO accounts (account, sector, year_established, revenue, employees, office_location, subsidiary_of) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect()) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
            }
            int count;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map<String, String> a : accounts) {
                    ps.setString(1, a.get("account"));
                    ps.setString(2, a.get("sector"));
                    ps.setInt(3, Integer.parseInt(a.get("year_established")));
                    ps.setBigDecimal(4, new java.math.BigDecimal(a.get("revenue")));
                    ps.setInt(5, Integer.parseInt(a.get("employees")));
                    ps.setString(6, a.get("office_location"));
                    String subsidiary = a.get("subsidiary_of");
                    if (subsidiary == null || subsidiary.isEmpty()) {
                        ps.setNull(7, java.sql.Types.VARCHAR);
                    } else {
                        ps.setString(7, subsidiary);
                    }
                    ps.addBatch();
                }
                int[] results = ps.executeBatch();
                count = results.length;
            }
            try (Statement st = conn.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return count;
        }
    }

    public int loadSalesPipeline(List<Map<String, String>> pipeline) throws SQLException {
        String sql = "INSERT INTO sales_pipeline (opportunity_id, sales_agent, product, account, deal_stage, engage_date, close_date, close_value) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, String> row : pipeline) {
                ps.setString(1, row.get("opportunity_id"));
                ps.setString(2, row.get("sales_agent"));
                ps.setString(3, row.get("product"));

                String account = row.get("account");
                if (account == null || account.isEmpty()) {
                    ps.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(4, account);
                }

                ps.setString(5, row.get("deal_stage"));

                String engageDate = row.get("engage_date");
                if (engageDate == null || engageDate.isEmpty()) {
                    ps.setNull(6, java.sql.Types.DATE);
                } else {
                    ps.setDate(6, java.sql.Date.valueOf(engageDate));
                }

                String closeDate = row.get("close_date");
                if (closeDate == null || closeDate.isEmpty()) {
                    ps.setNull(7, java.sql.Types.DATE);
                } else {
                    ps.setDate(7, java.sql.Date.valueOf(closeDate));
                }

                String closeValue = row.get("close_value");
                if (closeValue == null || closeValue.isEmpty()) {
                    ps.setNull(8, java.sql.Types.DECIMAL);
                } else {
                    ps.setBigDecimal(8, new java.math.BigDecimal(closeValue));
                }

                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }
}
