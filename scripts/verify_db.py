import sqlite3

DB = r'G:\My Drive\Apps\Finance-App\app\src\main\assets\budget_database.db'
OLD_ID = 'b9de10a8-9620-5d94-92bd-5ab4735ebd3b'
NEW_ID = 'default_household'

conn = sqlite3.connect(DB)
c = conn.cursor()

# Disable FK checks during migration
c.execute("PRAGMA foreign_keys = OFF")

tables_with_household_id = [
    'household',        # id
    'member',           # household_id
    'category',         # household_id
    'payment_method',   # household_id
    'quincena',         # household_id
    'expense',          # household_id
    'income_source',    # household_id
    'recurrence_template',  # household_id
    'installment_plan', # household_id
    'loan',             # household_id
    'savings_goal',     # household_id
]

# Update household table (PK)
c.execute(f"UPDATE household SET id = ? WHERE id = ?", (NEW_ID, OLD_ID))
print(f"household: {c.rowcount} rows updated")

# Update all FK references
for table in tables_with_household_id[1:]:
    c.execute(f"UPDATE {table} SET household_id = ? WHERE household_id = ?", (NEW_ID, OLD_ID))
    print(f"{table}: {c.rowcount} rows updated")

# Re-enable FK checks and verify
c.execute("PRAGMA foreign_keys = ON")
c.execute("PRAGMA foreign_key_check")
fk_errors = c.fetchall()
if fk_errors:
    print(f"\nFK ERRORS: {fk_errors}")
else:
    print("\nAll FK references OK")

conn.commit()

# Verify
c.execute("SELECT id FROM household")
print(f"\nHousehold ID is now: '{c.fetchone()[0]}'")
c.execute("SELECT COUNT(*) FROM member WHERE household_id = ?", (NEW_ID,))
print(f"Members with correct FK: {c.fetchone()[0]}")
c.execute("SELECT COUNT(*) FROM quincena WHERE household_id = ?", (NEW_ID,))
print(f"Quincenas with correct FK: {c.fetchone()[0]}")
c.execute("SELECT COUNT(*) FROM expense WHERE household_id = ?", (NEW_ID,))
print(f"Expenses with correct FK: {c.fetchone()[0]}")

conn.close()
