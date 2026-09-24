import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XMLParser (Extract layer)
 *
 * Reads accounts.xml and sales_teams.xml using the JDK's built-in DOM parser.
 *
 * IMPORTANT STRUCTURAL NOTE:
 * accounts.xml nests a leaf field literally named <account> (the company
 * name) inside each record element, which is ALSO named <account>. A naive
 * recursive search for the tag name (getElementsByTagName("account")) would
 * therefore match both the record wrapper AND the inner field, silently
 * doubling the record count.
 *
 * The fix: only iterate the DIRECT CHILDREN of the document root, then for
 * each record read its own direct children (not descendants) as fields.
 * This resolves the collision correctly.
 */
public class XMLParser {

    /**
     * Parses an XML file where the root element wraps a flat list of record
     * elements, and each record element's own direct-child elements are its
     * fields (even if a field happens to share a tag name with the record
     * wrapper itself, e.g. accounts.xml).
     *
     * @param filePath   path to the XML file
     * @param recordTag  the tag name of each record element (e.g. "account")
     * @return a list of records, each represented as an ordered field-name -> value map
     */
    public List<Map<String, String>> parseRecords(String filePath, String recordTag) throws Exception {
        List<Map<String, String>> records = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Harden against XXE (external entity) attacks
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();

        // Only iterate DIRECT CHILDREN of the root element that match recordTag.
        // Do NOT use getElementsByTagName(recordTag) here, since that searches
        // ALL descendants and would double-count in accounts.xml.
        NodeList rootChildren = root.getChildNodes();
        for (int i = 0; i < rootChildren.getLength(); i++) {
            Node node = rootChildren.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element recordElement = (Element) node;
            if (!recordElement.getTagName().equals(recordTag)) continue;

            Map<String, String> fields = new LinkedHashMap<>();
            NodeList fieldNodes = recordElement.getChildNodes();
            for (int j = 0; j < fieldNodes.getLength(); j++) {
                Node fieldNode = fieldNodes.item(j);
                if (fieldNode.getNodeType() != Node.ELEMENT_NODE) continue;
                Element fieldElement = (Element) fieldNode;
                String tag = fieldElement.getTagName();
                String value = fieldElement.getTextContent();
                fields.put(tag, value == null ? "" : value.trim());
            }
            records.add(fields);
        }

        return records;
    }

    /** Convenience: parse accounts.xml (record element tag is "account"). */
    public List<Map<String, String>> parseAccounts(String filePath) throws Exception {
        return parseRecords(filePath, "account");
    }

    /** Convenience: parse sales_teams.xml (record element tag is "sales_team"). */
    public List<Map<String, String>> parseSalesTeams(String filePath) throws Exception {
        return parseRecords(filePath, "sales_team");
    }
}
