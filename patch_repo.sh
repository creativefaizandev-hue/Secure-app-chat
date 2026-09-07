cat << 'PY' > patch.py
with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "r") as f:
    text = f.read()

# Make sure we have firebase storage instance
if "private val storage =" not in text:
    text = text.replace("private val firestore = FirebaseFirestore.getInstance()", "private val firestore = FirebaseFirestore.getInstance()\n  private val storage = FirebaseStorage.getInstance()")

with open("app/src/main/java/com/example/data/repository/SecureRepository.kt", "w") as f:
    f.write(text)
PY
python3 patch.py
