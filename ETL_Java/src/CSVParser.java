import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSVParser (Extract layer)
 *
 * Reads products.csv and sales_pipeline.csv with a straightforward
 * comma-split, which is sufficient because neither file contains quoted
 * or comma-embedded values.
 */
public class CSVParser {

    /**
     * Parses a simple CSV file (no embedded commas/quotes) into a list of
     * field-name -> value maps, keyed by the header row.
     */
    public List<Map<String, String>> parse(String filePath) throws Exception {
        List<Map<String, String>> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return records;
            String[] headers = headerLine.split(",", -1);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] values = line.split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = i < values.length ? values[i].trim() : "";
                    // Strip a trailing carriage return in case of Windows line endings
                    if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
                    row.put(headers[i].trim(), value);
                }
                records.add(row);
            }
        }
        return records;
    }
}
