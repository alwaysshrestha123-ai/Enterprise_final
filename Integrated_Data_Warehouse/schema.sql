-- =========================================================
-- crm_warehouse schema
-- ICT934 Assessment 3 - Web-Based Data Warehouse for CRM Sales Opportunities
-- =========================================================

DROP DATABASE IF EXISTS crm_warehouse;
CREATE DATABASE crm_warehouse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE crm_warehouse;

-- ---------------------------------------------------------
-- accounts: self-referencing via subsidiary_of
-- ---------------------------------------------------------
CREATE TABLE accounts (
    account            VARCHAR(100) NOT NULL PRIMARY KEY,
    sector             VARCHAR(50)  NOT NULL,
    year_established   INT          NOT NULL,
    revenue            DECIMAL(10,1) NOT NULL,
    employees          INT          NOT NULL,
    office_location    VARCHAR(100) NOT NULL,
    subsidiary_of      VARCHAR(100) NULL,
    CONSTRAINT fk_accounts_parent
        FOREIGN KEY (subsidiary_of) REFERENCES accounts(account)
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- products
-- ---------------------------------------------------------
CREATE TABLE products (
    product      VARCHAR(50) NOT NULL PRIMARY KEY,
    series       VARCHAR(20) NOT NULL,
    sales_price  DECIMAL(10,2) NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- sales_teams
-- ---------------------------------------------------------
CREATE TABLE sales_teams (
    sales_agent      VARCHAR(100) NOT NULL PRIMARY KEY,
    manager          VARCHAR(100) NOT NULL,
    regional_office  VARCHAR(50)  NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- sales_pipeline: fact-like table at the centre of the schema
-- ---------------------------------------------------------
CREATE TABLE sales_pipeline (
    opportunity_id  VARCHAR(20)  NOT NULL PRIMARY KEY,
    sales_agent     VARCHAR(100) NOT NULL,
    product         VARCHAR(50)  NOT NULL,
    account         VARCHAR(100) NULL,      -- NULL = opportunity not yet tied to a confirmed account
    deal_stage      VARCHAR(20)  NOT NULL,  -- Prospecting / Engaging / Won / Lost
    engage_date     DATE         NULL,      -- NULL = still Prospecting, not yet engaged
    close_date      DATE         NULL,      -- NULL = still open
    close_value     DECIMAL(10,2) NULL,     -- NULL = still open

    CONSTRAINT fk_pipeline_agent
        FOREIGN KEY (sales_agent) REFERENCES sales_teams(sales_agent),
    CONSTRAINT fk_pipeline_product
        FOREIGN KEY (product) REFERENCES products(product),
    CONSTRAINT fk_pipeline_account
        FOREIGN KEY (account) REFERENCES accounts(account)
        ON DELETE SET NULL,

    INDEX idx_pipeline_stage (deal_stage),
    INDEX idx_pipeline_product (product)
) ENGINE=InnoDB;
