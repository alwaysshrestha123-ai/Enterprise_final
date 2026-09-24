<?php
// Shared database connection settings for the CRM warehouse.
// As specified in the assessment brief, the placeholder root password is
// "rootroot". Change this before deploying anywhere beyond local development.

define('DB_HOST', '127.0.0.1');
define('DB_PORT', 3306);
define('DB_USER', 'root');
define('DB_PASSWORD', 'root');
define('DB_NAME', 'crm_warehouse');

function get_db_connection(): mysqli {
    mysqli_report(MYSQLI_REPORT_OFF); // we handle errors ourselves below
    $conn = new mysqli(DB_HOST, DB_USER, DB_PASSWORD, DB_NAME, DB_PORT);
    if ($conn->connect_error) {
        http_response_code(500);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'Database connection failed: ' . $conn->connect_error]);
        exit;
    }
    return $conn;
}
