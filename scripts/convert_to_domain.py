import os
import re

ENTITY_DIR = r"g:\My Drive\Apps\Finance-App\app\src\main\java\mx\budget\data\local\entity"
MODEL_DIR = r"g:\My Drive\Apps\Finance-App\app\src\main\java\mx\budget\core\model"

if not os.path.exists(MODEL_DIR):
    os.makedirs(MODEL_DIR)

# Annotations to strip
STRIP_REGEX = [
    r"@Entity\([\s\S]*?\)\s*data class",
    r"@PrimaryKey(\(autoGenerate\s*=\s*\w+\))?",
    r"@ColumnInfo\([\s\S]*?\)",
    r"@Index\([\s\S]*?\)",
    r"@ForeignKey\([\s\S]*?\)",
    r"import androidx\.room\..*"
]

def map_type_to_default(type_str):
    if type_str.startswith("String"): return '""'
    if type_str.startswith("Long"): return '0L'
    if type_str.startswith("Int"): return '0'
    if type_str.startswith("Double"): return '0.0'
    if type_str.startswith("Boolean"): return 'false'
    return "null"

for filename in os.listdir(ENTITY_DIR):
    if filename.endswith("Entity.kt"):
        filepath = os.path.join(ENTITY_DIR, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Change package
        content = content.replace("package mx.budget.data.local.entity", "package mx.budget.core.model\n\nimport kotlinx.serialization.Serializable")
        
        # Remove Room imports
        for regex in STRIP_REGEX:
            if "Entity" in regex:
                content = re.sub(regex, "@Serializable\ndata class", content)
            else:
                content = re.sub(regex, "", content)
        
        # Change Name
        class_name = filename.replace("Entity.kt", "")
        content = content.replace(f"data class {class_name}Entity", f"data class {class_name}")
        
        # Add default values
        # Find all val myProp: Type inside the class
        def add_defaults(match):
            prop_dec = match.group(0)
            if "=" not in prop_dec:
                types = re.findall(r":\s*([A-Za-z0-9_?]+)", prop_dec)
                if types:
                    t = types[0]
                    if t.endswith("?"):
                        return prop_dec + " = null"
                    else:
                        return prop_dec + " = " + map_type_to_default(t)
            return prop_dec

        content = re.sub(r"val\s+[A-Za-z0-9_]+\s*:\s*[A-Za-z0-9_?]+", add_defaults, content)

        # Write to core/model
        outpath = os.path.join(MODEL_DIR, f"{class_name}.kt")
        with open(outpath, 'w', encoding='utf-8') as fout:
            fout.write(content)
        
        print(f"Converted {filename} to {class_name}.kt")

