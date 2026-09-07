cat << 'PY' > fix_repo.py
with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    text = f.read()

import re
text = re.sub(r'package com\.example\.data\.repository(import.*)', lambda m: 'package com.example.data.repository\n\n' + m.group(1).replace('import ', '\nimport '), text)

lines = text.split('\n')
imports = set()
new_lines = []
for line in lines:
    if line.startswith('import '):
        if line not in imports:
            imports.add(line)
            new_lines.append(line)
    else:
        new_lines.append(line)

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "w") as f:
    f.write('\n'.join(new_lines))
PY
python3 fix_repo.py
