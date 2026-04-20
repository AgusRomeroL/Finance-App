import sqlite3
conn = sqlite3.connect(r'G:\My Drive\Apps\Finance-App\app\src\main\assets\budget_database.db')
c = conn.cursor()

# Mark current quincena as ACTIVE (Q2 Abril 2026 = Apr 16-30)
c.execute("UPDATE quincena SET status = 'ACTIVE' WHERE start_date <= '2026-04-19' AND end_date >= '2026-04-19'")
print(f"Updated {c.rowcount} quincena(s) to ACTIVE")

# Verify
c.execute("SELECT label, status, start_date, end_date FROM quincena WHERE status = 'ACTIVE'")
rows = c.fetchall()
for r in rows:
    print(f"  ACTIVE: {r[0]} ({r[2]} to {r[3]})")

if not rows:
    # Fallback: activate the most recent one
    c.execute("UPDATE quincena SET status = 'ACTIVE' WHERE id = (SELECT id FROM quincena ORDER BY start_date DESC LIMIT 1)")
    print(f"Fallback: activated most recent quincena ({c.rowcount} row)")
    c.execute("SELECT label, status FROM quincena WHERE status = 'ACTIVE'")
    print(f"  ACTIVE: {c.fetchone()}")

conn.commit()
conn.close()
