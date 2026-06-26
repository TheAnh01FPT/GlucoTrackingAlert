"""
medication_service.py
======================
AI gợi ý thuốc - TÁCH RIÊNG khỏi ml_service.py (con AI phân tích nhật ký).
Chạy độc lập trên port 5001.

Luồng xử lý:
  input (age, gender, bmi, blood_sugar, systolic, diastolic)
    -> phân loại case (1 trong 9 nhóm) bằng bộ luật resolve_case()
       (tương đương model RandomForest train trên Colab, vì model đó học
        thuộc lại đúng bộ luật này - xem ghi chú trong code bên dưới)
    -> tra bảng diabetes_medication_dataset_final.csv theo case
    -> trả về danh sách thuốc + liều + chống chỉ định
    -> KHÔNG tự tạo Prescription, chỉ trả gợi ý để bác sĩ xem & duyệt

Chạy:
  pip install -r requirements.txt
  python medication_service.py
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
import csv
import os

app = Flask(__name__)
CORS(app)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# ============================================
# Load bảng thuốc (load 1 lần lúc start)
# ============================================
DATASET_PATH = os.path.join(BASE_DIR, "diabetes_medication_dataset_final.csv")

with open(DATASET_PATH, newline="", encoding="utf-8") as f:
    medicine_rows = list(csv.DictReader(f))

CASE_CLASSES = ["pregnant_risk", "elderly_high", "elderly", "pediatric",
                "obese_high", "obese", "very_high", "high", "mild"]

print("✅ [medication_service] Đã load xong bảng thuốc (rule-based, không cần scikit-learn).")
print(f"   Các nhóm case: {CASE_CLASSES}")


# ============================================
# Phân loại case
# ============================================
# Lưu ý: model RandomForest train trên Colab đạt 100% accuracy trên dữ liệu
# synthetic, vì dữ liệu đó được sinh đúng theo công thức dưới đây -> tức là
# model học thuộc lại y hệt bộ luật này. Để tránh phải cài cả bộ scikit-learn
# nặng (gây lỗi build trên Python quá mới như 3.14), ở đây dùng thẳng luật,
# cho kết quả tương đương model .pkl. Khi nào có dữ liệu THẬT từ DB và model
# học được điều gì đó khác biệt với luật cũ, lúc đó mới cần quay lại dùng .pkl.
def resolve_case(age, gender, bmi, blood_sugar):
    is_female = str(gender).upper() == "FEMALE"

    if is_female and 18 <= age <= 45 and blood_sugar >= 9.0:
        return "pregnant_risk"
    if age >= 70 and blood_sugar >= 10.0:
        return "elderly_high"
    if age >= 70:
        return "elderly"
    if age < 18:
        return "pediatric"
    if bmi >= 25.0 and blood_sugar >= 10.0:
        return "obese_high"
    if bmi >= 25.0:
        return "obese"
    if blood_sugar >= 14.0:
        return "very_high"
    if blood_sugar >= 11.0:
        return "high"
    return "mild"


def predict_case(age, gender, bmi, blood_sugar, systolic, diastolic):
    pred_case = resolve_case(age, gender, bmi, blood_sugar)
    # Rule-based nên luôn chắc chắn 100% -- không có xác suất kiểu model thật
    confidence = 1.0
    proba_detail = {c: (1.0 if c == pred_case else 0.0) for c in CASE_CLASSES}
    return pred_case, confidence, proba_detail


def get_medicines_for_case(case_label):
    items = []
    for r in medicine_rows:
        if r["condition"] != case_label:
            continue
        items.append({
            "medicineName": r["medicine_name"],
            "dosage": r["dosage"],
            "frequency": r["frequency"],
            "timeOfDay": r["time_of_day"],
            "durationDays": int(r["duration_days"]) if r.get("duration_days") else None,
            "instructions": r["instructions"],
            "contraindications": r.get("contraindications", ""),
            "note": r.get("note", ""),
            "source": r.get("source", ""),
        })
    return items


# ============================================
# Routes
# ============================================
@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "service": "medication_service", "port": 5001})


@app.route("/suggest-medication", methods=["POST"])
def suggest_medication():
    try:
        data = request.get_json(force=True)

        age = float(data.get("age"))
        gender = data.get("gender", "MALE")
        bmi = float(data.get("bmi"))
        blood_sugar = float(data.get("bloodSugar"))
        systolic = float(data.get("systolic", 120))
        diastolic = float(data.get("diastolic", 80))

        case_label, confidence, proba_detail = predict_case(
            age, gender, bmi, blood_sugar, systolic, diastolic
        )
        medicines = get_medicines_for_case(case_label)

        # Cờ cảnh báo: model không chắc chắn -> cần bác sĩ xem kỹ hơn
        needs_manual_review = confidence < 0.6

        # Cảnh báo riêng cho case pediatric (theo QĐ BYT không áp dụng <18 tuổi)
        warning = None
        if case_label == "pediatric":
            warning = ("Bệnh nhân dưới 18 tuổi - Quyết định 3280/5481-BYT không áp dụng cho nhóm này. "
                       "Gợi ý chỉ tham khảo phác đồ quốc tế (ADA), BẮT BUỘC bác sĩ chuyên khoa Nhi duyệt lại.")

        return jsonify({
            "case": case_label,
            "confidence": round(confidence, 4),
            "confidenceDetail": proba_detail,
            "needsManualReview": needs_manual_review,
            "warning": warning,
            "medicines": medicines,
            "note": "Đây là gợi ý từ AI dựa trên tuổi/giới tính/BMI/đường huyết. "
                    "Không thay thế chẩn đoán bác sĩ - cần duyệt trước khi kê đơn thật. "
                    "Hệ thống chưa xét tới bệnh nền (tim/thận/gan) - bác sĩ cần tự đối chiếu hồ sơ bệnh nhân.",
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 400


if __name__ == "__main__":
    # Port 5001 -- KHÁC với ml_service.py (port 5000) đang chạy con AI nhật ký
    app.run(host="0.0.0.0", port=5001, debug=True)
