<?php
/**
 * queryEngine.php
 *
 * Middleware between the crm_warehouse MySQL database and the browser.
 * Accepts a report selection via a WHITELISTED ?report= parameter (there is
 * no free-text SQL from the client, which closes off SQL injection through
 * the report selector), opens a mysqli connection using the shared settings
 * in config.php, executes the matching prepared query, and returns the
 * result set as JSON with a title, column list, and row array.
 *
 * Supported ?report= values:
 *   products               - Products Report
 *   opportunities          - Sales Opportunities Report (Won/Lost), paginated
 *   establishment_year     - Establishment Year Revenue Analysis
 *   product_analysis       - Sales Opportunity Analysis (by Product)
 *
 * Pagination (opportunities only): ?page=1 (25 rows per page). KPI cards are
 * computed from the COMPLETE result set, not just the visible page.
 */

require_once __DIR__ . '/config.php';

header('Content-Type: application/json');

$allowedReports = ['products', 'opportunities', 'establishment_year', 'product_analysis'];
$report = $_GET['report'] ?? '';

if (!in_array($report, $allowedReports, true)) {
    http_response_code(400);
    echo json_encode([
        'error' => 'Unknown report key. Allowed values: ' . implode(', ', $allowedReports)
    ]);
    exit;
}

$conn = get_db_connection();

switch ($report) {

    case 'products':
        $result = $conn->query(
            "SELECT product, series, sales_price FROM products ORDER BY product"
        );
        $rows = fetch_all_rows($result);
        echo json_encode([
            'title' => 'Products Report',
            'columns' => ['Product', 'Series', 'Sales Price'],
            'rows' => $rows,
        ]);
        break;

    case 'opportunities':
        $perPage = 25;
        $page = max(1, (int)($_GET['page'] ?? 1));
        $offset = ($page - 1) * $perPage;

        // Complete result set (for KPI cards, not just the visible page)
        $fullResult = $conn->query(
            "SELECT deal_stage, close_value FROM sales_pipeline WHERE deal_stage IN ('Won','Lost')"
        );
        $totalCount = 0;
        $wonCount = 0;
        $lostCount = 0;
        $totalWonValue = 0.0;
        while ($row = $fullResult->fetch_assoc()) {
            $totalCount++;
            if ($row['deal_stage'] === 'Won') {
                $wonCount++;
                $totalWonValue += (float)$row['close_value'];
            } else {
                $lostCount++;
            }
        }
        $winRate = $totalCount > 0 ? round(($wonCount / $totalCount) * 100, 1) : 0;

        // Paginated page of rows for the table
        $stmt = $conn->prepare(
            "SELECT opportunity_id, sales_agent, product, account, deal_stage, engage_date, close_date, close_value
             FROM sales_pipeline
             WHERE deal_stage IN ('Won','Lost')
             ORDER BY close_date DESC
             LIMIT ? OFFSET ?"
        );
        $stmt->bind_param('ii', $perPage, $offset);
        $stmt->execute();
        $pageResult = $stmt->get_result();
        $rows = fetch_all_rows($pageResult);

        echo json_encode([
            'title' => 'Sales Opportunities Report (Won / Lost)',
            'columns' => ['Opportunity ID', 'Sales Agent', 'Product', 'Account', 'Stage', 'Engage Date', 'Close Date', 'Close Value'],
            'rows' => $rows,
            'kpis' => [
                'total_opportunities' => $totalCount,
                'won' => $wonCount,
                'lost' => $lostCount,
                'win_rate_pct' => $winRate,
                'total_won_value' => round($totalWonValue, 2),
            ],
            'pagination' => [
                'page' => $page,
                'per_page' => $perPage,
                'total_rows' => $totalCount,
                'total_pages' => (int)ceil($totalCount / $perPage),
            ],
        ]);
        break;

    case 'establishment_year':
        $result = $conn->query(
            "SELECT year_established, COUNT(*) AS num_accounts, SUM(revenue) AS total_revenue
             FROM accounts
             GROUP BY year_established
             ORDER BY year_established"
        );
        $rows = fetch_all_rows($result);
        echo json_encode([
            'title' => 'Establishment Year Revenue Analysis',
            'columns' => ['Year Established', 'Number of Accounts', 'Total Revenue ($M)'],
            'rows' => $rows,
            'chart' => [
                'labels_index' => 0,
                'values_index' => 2,
            ],
        ]);
        break;

    case 'product_analysis':
        $result = $conn->query(
            "SELECT p.product,
                    COUNT(sp.opportunity_id) AS total_opportunities,
                    SUM(CASE WHEN sp.deal_stage = 'Won' THEN 1 ELSE 0 END) AS won_count,
                    SUM(CASE WHEN sp.deal_stage = 'Lost' THEN 1 ELSE 0 END) AS lost_count,
                    COALESCE(SUM(CASE WHEN sp.deal_stage = 'Won' THEN sp.close_value ELSE 0 END), 0) AS won_revenue
             FROM products p
             LEFT JOIN sales_pipeline sp ON sp.product = p.product
             GROUP BY p.product
             ORDER BY won_revenue DESC"
        );
        $rows = fetch_all_rows($result);
        echo json_encode([
            'title' => 'Sales Opportunity Analysis (by Product)',
            'columns' => ['Product', 'Total Opportunities', 'Won', 'Lost', 'Won Revenue'],
            'rows' => $rows,
            'chart' => [
                'labels_index' => 0,
                'values_index' => 4,
            ],
        ]);
        break;
}

$conn->close();

/** Converts a mysqli_result into a plain array of indexed-value rows for JSON. */
function fetch_all_rows(mysqli_result $result): array {
    $rows = [];
    while ($row = $result->fetch_row()) {
        $rows[] = $row;
    }
    return $rows;
}
