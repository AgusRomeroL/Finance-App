import os
import re

MODEL_DIR = r"g:\My Drive\Apps\Finance-App\app\src\main\java\mx\budget\core\model"

for filename in os.listdir(MODEL_DIR):
    if filename.endswith(".kt") and filename != "Enums.kt":
        filepath = os.path.join(MODEL_DIR, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        new_lines = []
        for line in lines:
            if "= null = null" in line:
                line = line.replace("= null = null", "= null")
            elif '= "" = "POSTED"' in line:
                line = line.replace('= "" = "POSTED"', '= "POSTED"')
            elif '= "" = "' in line: # Fallback for other defaults
                parts = line.split('= "" = "')
                line = parts[0] + '= "' + parts[1]
            new_lines.append(line)
            
        with open(filepath, 'w', encoding='utf-8') as fout:
            fout.writelines(new_lines)

print("Fixed syntax issues")
