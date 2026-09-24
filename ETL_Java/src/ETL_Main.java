import java.util.List;
import java.util.Map;

/**
 * ETL_Main
 *
 * Orchestrates the Extract -> Transform -> Load pipeline:
 *   1. Extract:  XMLParser reads accounts.xml, sales_teams.xml
 *                CSVParser reads products.csv, sales_pipeline.csv
 *   2. Transform: DataTransformer cleans/standardises the extracted records
 *   3. Load:      MySQLLoader truncates the warehouse tables and loads
 *                 parent tables first, then the dependent sales_pipeline
 *                 table.
 *
 * Usage:
 *   java -cp .:lib/mariadb-java-client.jar ETL_Main <source_data_dir> <jdbc_url> <user> <password>
 *
 * Example:
 *   java -cp .:lib/mariadb-java-client.jar ETL_Main ../source_data \
 *        jdbc:mariadb://localhost:3306/crm_warehouse root rootroot
 */
public class ETL_Main {

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java ETL_Main <source_data_dir> <jdbc_url> <user> <password>");
            System.exit(1);
        }

        String sourceDir = args[0];
        String jdbcUrl = args[1];
        String user = args[2];
        String password = args[3];

        long start = System.currentTimeMillis();
        System.out.println("=== ICT934 CRM Data Warehouse ETL ===");

        try {
            // ---------------- EXTRACT ----------------
            System.out.println("\n[Extract] Reading source files from: " + sourceDir);
            XMLParser xmlParser = new XMLParser();
            CSVParser csvParser = new CSVParser();

            List<Map<String, String>> accounts = xmlParser.parseAccounts(sourceDir + "/accounts.xml");
            List<Map<String, String>> salesTeams = xmlParser.parseSalesTeams(sourceDir + "/sales_teams.xml");
            List<Map<String, String>> products = csvParser.parse(sourceDir + "/products.csv");
            List<Map<String, String>> pipeline = csvParser.parse(sourceDir + "/sales_pipeline.csv");

            System.out.println("  accounts.xml       -> " + accounts.size() + " records");
            System.out.println("  sales_teams.xml    -> " + salesTeams.size() + " records");
            System.out.println("  products.csv       -> " + products.size() + " records");
            System.out.println("  sales_pipeline.csv -> " + pipeline.size() + " records");
            System.out.println("  (data_dictionary.csv is documentation only and is never loaded)");

            // ---------------- TRANSFORM ----------------
            System.out.println("\n[Transform] Cleaning and standardising records...");
            DataTransformer transformer = new DataTransformer();
            accounts = transformer.transformAccounts(accounts);
            pipeline = transformer.transformSalesPipeline(pipeline);
            transformer.printSummary();

            // ---------------- LOAD ----------------
            System.out.println("\n[Load] Loading into MySQL warehouse...");
            MySQLLoader loader = new MySQLLoader(jdbcUrl, user, password);
            loader.truncateAll();

            int productsLoaded = loader.loadProducts(products);
            int teamsLoaded = loader.loadSalesTeams(salesTeams);
            int accountsLoaded = loader.loadAccounts(accounts);
            int pipelineLoaded = loader.loadSalesPipeline(pipeline);

            System.out.println("  products loaded:       " + productsLoaded);
            System.out.println("  sales_teams loaded:    " + teamsLoaded);
            System.out.println("  accounts loaded:       " + accountsLoaded);
            System.out.println("  sales_pipeline loaded: " + pipelineLoaded);

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("\n=== ETL run completed successfully in " + elapsed + " ms, zero errors ===");

        } catch (Exception e) {
            System.err.println("\n=== ETL run FAILED ===");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
