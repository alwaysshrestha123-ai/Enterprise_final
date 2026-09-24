import java.util.List;
import java.util.Map;

/**
 * DataTransformer (Transform layer)
 *
 * Applies the data-cleaning rules identified by inspecting the source data:
 *
 *  1. Sector standardisation (accounts.sector): mixed casing and a
 *     "technolgy" misspelling are normalised to a single, consistently
 *     Title-Cased, correctly spelled value (e.g. "retail" -> "Retail",
 *     "technolgy" -> "Technology").
 *
 *  2. Product name standardisation (sales_pipeline.product): rows recording
 *     "GTXPro" (no space) are standardised to "GTX Pro" so they match the
 *     products table and don't fail the foreign-key constraint on load.
 *
 *  3. Office location typo (accounts.office_location): "Philipines" is
 *     corrected to "Philippines".
 *
 * Genuine missing values (blank account / engage_date / close_date /
 * close_value in sales_pipeline) are NOT fabricated -- they represent real
 * business states of an open pipeline (an opportunity not yet tied to a
 * confirmed account, or a deal that hasn't engaged/closed yet) and are
 * deliberately passed through as SQL NULL. This class logs how many blanks
 * of each kind it saw, for auditability, but does not invent values for them.
 */
public class DataTransformer {

    private int sectorFixCount = 0;
    private int productTypoFixCount = 0;
    private int officeTypoFixCount = 0;
    private int missingAccountCount = 0;
    private int missingEngageDateCount = 0;
    private int missingCloseCount = 0;

    /** Cleans a list of account records in place (returns a new list). */
    public List<Map<String, String>> transformAccounts(List<Map<String, String>> accounts) {
        for (Map<String, String> account : accounts) {
            String rawSector = account.get("sector");
            String cleanSector = standardiseSector(rawSector);
            if (!cleanSector.equals(rawSector)) sectorFixCount++;
            account.put("sector", cleanSector);

            String rawOffice = account.get("office_location");
            if ("Philipines".equalsIgnoreCase(rawOffice)) {
                account.put("office_location", "Philippines");
                officeTypoFixCount++;
            }

            String subsidiary = account.get("subsidiary_of");
            if (subsidiary == null || subsidiary.isEmpty()) {
                account.put("subsidiary_of", null);
            }
        }
        return accounts;
    }

    /** Fixes the misspelling and normalises casing to Title Case. */
    private String standardiseSector(String raw) {
        if (raw == null) return "";
        String fixed = raw.trim();
        if (fixed.equalsIgnoreCase("technolgy")) {
            fixed = "Technology";
        }
        return toTitleCase(fixed);
    }

    private String toTitleCase(String s) {
        if (s.isEmpty()) return s;
        StringBuilder result = new StringBuilder();
        for (String word : s.toLowerCase().split(" ")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    /** Cleans a list of sales_pipeline records in place (returns a new list). */
    public List<Map<String, String>> transformSalesPipeline(List<Map<String, String>> pipeline) {
        for (Map<String, String> row : pipeline) {
            String product = row.get("product");
            if ("GTXPro".equalsIgnoreCase(product)) {
                row.put("product", "GTX Pro");
                productTypoFixCount++;
            }

            String account = row.get("account");
            if (account == null || account.isEmpty()) {
                row.put("account", null);
                missingAccountCount++;
            }

            String engageDate = row.get("engage_date");
            if (engageDate == null || engageDate.isEmpty()) {
                row.put("engage_date", null);
                missingEngageDateCount++;
            }

            String closeDate = row.get("close_date");
            String closeValue = row.get("close_value");
            boolean closeDateMissing = closeDate == null || closeDate.isEmpty();
            boolean closeValueMissing = closeValue == null || closeValue.isEmpty();
            if (closeDateMissing) row.put("close_date", null);
            if (closeValueMissing) row.put("close_value", null);
            if (closeDateMissing && closeValueMissing) missingCloseCount++;
        }
        return pipeline;
    }

    public void printSummary() {
        System.out.println("---- DataTransformer summary ----");
        System.out.println("Sector values standardised (casing/spelling): " + sectorFixCount);
        System.out.println("Office location typos fixed (Philipines -> Philippines): " + officeTypoFixCount);
        System.out.println("Product name typos fixed (GTXPro -> GTX Pro): " + productTypoFixCount);
        System.out.println("Pipeline rows with no confirmed account (kept as NULL): " + missingAccountCount);
        System.out.println("Pipeline rows with no engage_date (still Prospecting, kept as NULL): " + missingEngageDateCount);
        System.out.println("Pipeline rows with no close_date/close_value (still open, kept as NULL): " + missingCloseCount);
    }
}
