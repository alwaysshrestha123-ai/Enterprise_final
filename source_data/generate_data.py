"""
Generates a small, simple sample CRM dataset for the ICT934 warehouse project.
Not a real/official dataset -- just simple sample data with a few deliberate
data-quality issues so the ETL cleaning logic has something genuine to do.

Output:
  accounts.xml        (XML, deliberately nests a leaf field <account> inside
                        the record element which is ALSO called <account>)
  sales_teams.xml      (XML)
  products.csv         (CSV)
  sales_pipeline.csv   (CSV)
  data_dictionary.csv  (CSV, documentation only, never loaded)
"""
import random
import csv
from datetime import date, timedelta
from xml.sax.saxutils import escape

random.seed(42)

# ---------- 1. Products (7 products) ----------
products = [
    ("GTX Basic", "GTX", 550),
    ("GTX Pro", "GTX", 4821),
    ("GTX Plus Basic", "GTX", 1096),
    ("GTX Plus Pro", "GTX", 6674),
    ("GTK 500", "GTK", 2149),
    ("MG Advanced", "MG", 3393),
    ("MG Special", "MG", 55),
]
with open("products.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["product", "series", "sales_price"])
    w.writerows(products)

# ---------- 2. Sales teams (10 agents, 3 managers) -> XML ----------
managers = ["Dustin Brinkmann", "Rocco Neubert", "Melvin Marxen"]
offices = ["Central", "East", "West"]
agent_first = ["Cassey", "Kary", "Wilburn", "Vernita", "Marty", "Boris",
               "Kami", "Anna", "Elease", "Niesha"]
agent_last = ["Cassidy", "Hendrixson", "Weyer", "Chega", "Freudenburg",
              "Tarwater", "Berrey", "Boston", "Farler", "Cyrier"]
sales_agents = [f"{f} {l}" for f, l in zip(agent_first, agent_last)]

with open("sales_teams.xml", "w") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<sales_teams>\n')
    for i, agent in enumerate(sales_agents):
        manager = managers[i % len(managers)]
        office = offices[i % len(offices)]
        f.write("  <sales_team>\n")
        f.write(f"    <sales_agent>{escape(agent)}</sales_agent>\n")
        f.write(f"    <manager>{escape(manager)}</manager>\n")
        f.write(f"    <regional_office>{escape(office)}</regional_office>\n")
        f.write("  </sales_team>\n")
    f.write("</sales_teams>\n")

# ---------- 3. Accounts (20 accounts, with data-quality issues) -> XML ----------
sectors_clean = ["Retail", "Technology", "Finance", "Medical", "Marketing",
                  "Software", "Employment", "Telecommunications", "Services", "Entertainment"]
sector_variants = {
    "Retail": ["retail", "RETAIL", "Retail"],
    "Technology": ["technolgy", "Technology", "TECHNOLOGY"],  # deliberate misspelling
    "Finance": ["finance", "Finance"],
    "Medical": ["medical", "Medical"],
    "Marketing": ["marketing", "Marketing"],
    "Software": ["software", "Software"],
    "Employment": ["employment", "Employment"],
    "Telecommunications": ["telecommunications", "Telecommunications"],
    "Services": ["services", "Services"],
    "Entertainment": ["entertainment", "Entertainment"],
}

company_names = [
    "Acme Hardware", "Northwind Traders", "Bluefin Systems", "Cedarline Corp",
    "Marlow Group", "Farview Ltd", "Redgate Solutions", "Silverpeak Inc",
    "Oakridge Networks", "Bellcrest Media", "Ironbridge Retail", "Sunwell Finance",
    "Granite Medical", "Vantage Telecom", "Crestview Services", "Halcyon Software",
    "Westfield Employment", "Trueline Entertainment", "Pinehill Marketing", "Oceanic Systems"
]

offices_loc = ["USA", "Germany", "Brazil", "Japan", "Philippines", "Philipines",  # deliberate typo
               "Kenya", "France", "United Kingdom"]

accounts = []
for i, name in enumerate(company_names):
    sector = sectors_clean[i % len(sectors_clean)]
    raw_sector = random.choice(sector_variants[sector])
    year_established = random.randint(1980, 2015)
    revenue = round(random.uniform(1.0, 500.0), 1)   # in millions
    employees = random.randint(20, 5000)
    office_location = random.choice(offices_loc)
    accounts.append({
        "account": name,
        "sector": raw_sector,
        "year_established": year_established,
        "revenue": revenue,
        "employees": employees,
        "office_location": office_location,
        "subsidiary_of": None,
    })

for child_idx, parent_idx in [(2, 0), (5, 1), (9, 0), (14, 6)]:
    accounts[child_idx]["subsidiary_of"] = accounts[parent_idx]["account"]

# Deliberate structural quirk: the record wrapper is <account>, and it ALSO
# contains a leaf field named <account> (the company name) -- same tag name
# nested inside itself. A naive "search anywhere for tag <account>" parser
# would double-count records. The correct parser only looks at direct
# children of the document root.
with open("accounts.xml", "w") as f:
    f.write('<?xml version="1.0" encoding="UTF-8"?>\n<accounts>\n')
    for a in accounts:
        f.write("  <account>\n")
        f.write(f"    <account>{escape(a['account'])}</account>\n")
        f.write(f"    <sector>{escape(a['sector'])}</sector>\n")
        f.write(f"    <year_established>{a['year_established']}</year_established>\n")
        f.write(f"    <revenue>{a['revenue']}</revenue>\n")
        f.write(f"    <employees>{a['employees']}</employees>\n")
        f.write(f"    <office_location>{escape(a['office_location'])}</office_location>\n")
        if a["subsidiary_of"]:
            f.write(f"    <subsidiary_of>{escape(a['subsidiary_of'])}</subsidiary_of>\n")
        else:
            f.write("    <subsidiary_of></subsidiary_of>\n")
        f.write("  </account>\n")
    f.write("</accounts>\n")

# ---------- 4. Sales pipeline (300 opportunities, with quality issues) -> CSV ----------
account_names = [a["account"] for a in accounts]
stages = ["Prospecting", "Engaging", "Won", "Lost"]
start_date = date(2023, 1, 1)

rows = []
opp_id = 1
GTXPRO_TYPO_COUNT = 40
typo_budget = GTXPRO_TYPO_COUNT

for _ in range(300):
    agent = random.choice(sales_agents)
    product_name, _, price = random.choice(products)

    if product_name == "GTX Pro" and typo_budget > 0 and random.random() < 0.6:
        product_field = "GTXPro"
        typo_budget -= 1
    else:
        product_field = product_name

    stage = random.choices(stages, weights=[0.15, 0.20, 0.40, 0.25])[0]
    engage_offset = random.randint(0, 300)
    engage_date = start_date + timedelta(days=engage_offset)

    account = random.choice(account_names)
    if stage in ("Prospecting", "Engaging") and random.random() < 0.5:
        account = ""

    if stage == "Prospecting":
        engage_date_str = ""
    else:
        engage_date_str = engage_date.isoformat()

    if stage in ("Won", "Lost"):
        close_offset = engage_offset + random.randint(5, 90)
        close_date = start_date + timedelta(days=close_offset)
        close_date_str = close_date.isoformat()
        close_value = round(price * random.uniform(0.8, 1.2), 2) if stage == "Won" else ""
    else:
        close_date_str = ""
        close_value = ""

    rows.append([
        f"OPP-{opp_id:04d}", agent, product_field, account, stage,
        engage_date_str, close_date_str, close_value
    ])
    opp_id += 1

with open("sales_pipeline.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["opportunity_id", "sales_agent", "product", "account", "deal_stage",
                "engage_date", "close_date", "close_value"])
    w.writerows(rows)

# ---------- 5. Data dictionary (documentation only, never loaded) ----------
with open("data_dictionary.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["Table", "Field", "Description"])
    dict_rows = [
        ("accounts", "account", "Name of the client company"),
        ("accounts", "sector", "Industry sector of the client"),
        ("accounts", "year_established", "Year the company was founded"),
        ("accounts", "revenue", "Annual revenue in millions USD"),
        ("accounts", "employees", "Number of employees"),
        ("accounts", "office_location", "Country of the company's head office"),
        ("accounts", "subsidiary_of", "Parent company, if any"),
        ("products", "product", "Product name"),
        ("products", "series", "Product series/family"),
        ("products", "sales_price", "List price in USD"),
        ("sales_teams", "sales_agent", "Name of the sales agent"),
        ("sales_teams", "manager", "Name of the agent's manager"),
        ("sales_teams", "regional_office", "Regional office of the agent"),
        ("sales_pipeline", "opportunity_id", "Unique ID of the sales opportunity"),
        ("sales_pipeline", "sales_agent", "Agent working the opportunity"),
        ("sales_pipeline", "product", "Product being sold"),
        ("sales_pipeline", "account", "Client account (may be blank if not yet confirmed)"),
        ("sales_pipeline", "deal_stage", "Prospecting / Engaging / Won / Lost"),
        ("sales_pipeline", "engage_date", "Date the opportunity was engaged"),
        ("sales_pipeline", "close_date", "Date the opportunity was closed"),
        ("sales_pipeline", "close_value", "Value of the deal if won"),
    ]
    w.writerows(dict_rows)

print("Generated: accounts.xml, sales_teams.xml, products.csv, sales_pipeline.csv, data_dictionary.csv")
print(f"Accounts: {len(accounts)}, Products: {len(products)}, Agents: {len(sales_agents)}, Pipeline rows: {len(rows)}")
