import sqlite3
import json
import os
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

DB_PATH = r"g:\My Drive\Apps\Finance-App\app\src\main\assets\budget_database.db"
KEY_PATH = r"g:\My Drive\Apps\Finance-App\service-account.json"

if not os.path.exists(KEY_PATH):
    print("Error: service-account.json not found!")
    exit(1)

# Initialize Firebase
cred = credentials.Certificate(KEY_PATH)
firebase_admin.initialize_app(cred)
db = firestore.client()

print("Connected to Firestore. Reading local DB...")
conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get Household
cursor.execute("SELECT * FROM household LIMIT 1")
household_row = cursor.fetchone()
if not household_row:
    print("No household found. Local DB is empty?")
    exit(1)

household = dict(household_row)
household_id = household["id"]

h_ref = db.collection("households").document(household_id)
h_ref.set(household)
print(f"Household {household_id} uploaded.")

# Upload members
cursor.execute("SELECT * FROM member WHERE household_id = ?", (household_id,))
members = cursor.fetchall()
for m in members:
    m_dict = dict(m)
    h_ref.collection("members").document(m_dict["id"]).set(m_dict)
print(f"Uploaded {len(members)} members.")

# Upload categories
cursor.execute("SELECT * FROM category")
categories = cursor.fetchall()
for c in categories:
    c_dict = dict(c)
    h_ref.collection("categories").document(c_dict["id"]).set(c_dict)
print(f"Uploaded {len(categories)} categories.")

# Upload payment methods as wallets
cursor.execute("SELECT * FROM payment_method")
wallets = cursor.fetchall()
for w in wallets:
    w_dict = dict(w)
    h_ref.collection("wallets").document(w_dict["id"]).set(w_dict)
print(f"Uploaded {len(wallets)} wallets.")

# Upload quincenas
cursor.execute("SELECT * FROM quincena")
quincenas = cursor.fetchall()
for q in quincenas:
    q_dict = dict(q)
    h_ref.collection("quincenas").document(q_dict["id"]).set(q_dict)
print(f"Uploaded {len(quincenas)} quincenas.")

# Pre-load attributions into memory to embed them
cursor.execute("SELECT * FROM expense_attribution")
attributions = cursor.fetchall()
attrs_by_expense = {}
for a in attributions:
    a_dict = dict(a)
    exp_id = a_dict["expense_id"]
    if exp_id not in attrs_by_expense:
        attrs_by_expense[exp_id] = []
    attrs_by_expense[exp_id].append(a_dict)

# Upload expenses
cursor.execute("SELECT * FROM expense")
expenses = cursor.fetchall()
for e in expenses:
    e_dict = dict(e)
    # Embed attributions
    e_id = e_dict["id"]
    e_dict["attributions"] = attrs_by_expense.get(e_id, [])
    
    h_ref.collection("expenses").document(e_id).set(e_dict)
print(f"Uploaded {len(expenses)} expenses with mapped attributions.")

print("Data Seeding Complete!")
