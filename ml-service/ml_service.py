from flask import Flask, request, jsonify
from flask_cors import CORS
import joblib
import numpy as np
import json
import os

app = Flask(__name__)
CORS(app)

# Fix float32 serialization
import json
class NumpyEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, np.floating):
            return float(obj)
        if isinstance(obj, np.integer):
            return int(obj)
        if isinstance(obj, np.ndarray):
            return obj.tolist()
        return super().default(obj)

app.json_encoder = NumpyEncoder

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# ============================================
# Load models (V3 đã bỏ - xem ghi chú trong ensemble_predict())
# ============================================
model_v4 = joblib.load(os.path.join(BASE_DIR, 'gluco_model_v4.pkl'))      # Pima
model_v5 = joblib.load(os.path.join(BASE_DIR, 'gluco_model_v5.pkl'))      # Diabetes Prediction

print("✅ Models loaded: V4 (Pima) + V5 (Diabetes Prediction)")
print("   (V3/CDC đã bỏ vì cần dữ liệu lifestyle chưa thu thập được - tránh bịa dữ liệu)")

# ============================================
# Khuyến nghị
# ============================================
def generate_advice(risk_level, blood_sugar, systolic, bmi):
    advice = []

    # Lưu ý: hệ thống chưa lưu thông tin "bệnh nhân đã được chẩn đoán tiểu
    # đường hay chưa" -- toàn bộ app này vốn dành cho người đã mắc tiểu
    # đường, nên các câu dưới đây mô tả MỨC ĐỘ KIỂM SOÁT của chỉ số đo
    # được trong ngày, KHÔNG phải chẩn đoán lại từ đầu (tránh gây hiểu lầm
    # kiểu "tiền tiểu đường" nghe như bệnh nhẹ hơn bệnh đã có).
    if blood_sugar >= 11.1:
        advice.append("Đường huyết hôm nay rất cao (≥11.1 mmol/L) - kiểm soát kém. Cần kiểm tra HbA1c và xem xét điều chỉnh phác đồ ngay.")
    elif blood_sugar >= 7.8:
        advice.append("Đường huyết hôm nay cao hơn mục tiêu - kiểm soát chưa tốt. Khuyến nghị nhịn ăn và đo lại sau 2 giờ.")
    elif blood_sugar >= 5.6:
        advice.append("Đường huyết hôm nay ở mức khá - vẫn cần theo dõi chặt chẽ và điều chỉnh chế độ ăn, không chủ quan dù chỉ số đẹp.")

    if systolic >= 140:
        advice.append("Huyết áp cao (≥140 mmHg). Xem xét điều chỉnh thuốc hạ áp.")
    elif systolic >= 130:
        advice.append("Huyết áp tăng nhẹ. Khuyến nghị giảm muối và tăng vận động.")

    if bmi >= 30:
        advice.append("BMI ≥30 (béo phì). Giảm cân là ưu tiên hàng đầu để kiểm soát đường huyết.")
    elif bmi >= 25:
        advice.append("BMI 25-30 (thừa cân). Nên điều chỉnh chế độ ăn và tập thể dục đều đặn.")

    if risk_level == 2:
        advice.append("⚕️ Khuyến nghị bác sĩ: Xem xét phác đồ điều trị, có thể cần dùng Metformin hoặc GLP-1.")
    elif risk_level == 1:
        advice.append("⚕️ Khuyến nghị bác sĩ: Theo dõi sát, tái khám sau 2-4 tuần, chú trọng thay đổi lối sống.")
    else:
        advice.append("✅ Tiếp tục duy trì lối sống lành mạnh và kiểm tra định kỳ 6 tháng/lần.")

    return advice


# ============================================
# Ensemble 3 models
# ============================================
def ensemble_predict(data: dict) -> dict:
    # Các field bắt buộc -- Java (MlAnalysisService) đã validate trước khi gửi sang,
    # nhưng vẫn check lại ở đây để service Python không tự bịa số khi bị gọi trực tiếp
    # (trước đây dùng data.get(key, default) khiến luôn có giá trị giả khi field thiếu)
    required = ['bloodSugar', 'systolic', 'diastolic', 'bmi', 'age', 'gender']
    missing = [k for k in required if data.get(k) is None]
    if missing:
        raise ValueError(f"Thiếu dữ liệu bắt buộc: {', '.join(missing)}")

    blood_sugar = float(data['bloodSugar'])
    systolic    = int(data['systolic'])
    diastolic   = int(data['diastolic'])
    bmi         = float(data['bmi'])
    age         = int(data['age'])
    gender      = data['gender']
    is_pregnant = bool(data.get('isPregnant', False))
    hypertension = 1 if systolic >= 140 else 0

    # ---- Rule ADA cứng ----
    rule_applied = False
    if blood_sugar >= 11.1:
        risk = 2
        rule_applied = True
    elif blood_sugar < 5.6 and systolic < 120 and bmi < 25:
        risk = 0
        rule_applied = True
    else:
        glucose_mgdl = blood_sugar * 18.0
        high_bp = 1 if systolic >= 130 else 0

        # Model V4 — Pima (glucose trực tiếp)
        x_v4 = np.array([[
            1 if is_pregnant else 0,
            glucose_mgdl, systolic,
            25.0, bmi, 0.5, age
        ]])
        proba_v4 = float(model_v4.predict_proba(x_v4)[0][1])

        # Model V5 — Diabetes Prediction (blood_glucose + hypertension)
        # smokingStatus lấy từ Patient.smokingStatus (cột đã có sẵn trong DB),
        # mapping y hệt logic DailyHealthLogServiceImpl.java đang dùng cho model khác:
        # "never smoked"=0, "formerly smoked"=1, "smokes"=2, không rõ/khác -> trung tính=1
        smoking_status = str(data.get('smokingStatus') or '').strip().lower()
        if smoking_status == 'never smoked':
            smoke_enc = 0
        elif smoking_status == 'formerly smoked':
            smoke_enc = 1
        elif smoking_status == 'smokes':
            smoke_enc = 2
        else:
            smoke_enc = 1  # chưa rõ thông tin -> dùng giá trị trung tính, không suy diễn "không hút"
        gender_enc = 1 if gender == 'FEMALE' else 0
        x_v5 = np.array([[
            glucose_mgdl, bmi, age,
            hypertension, 0,
            smoke_enc, gender_enc
        ]])
        proba_v5 = float(model_v5.predict_proba(x_v5)[0][1])

        # Weighted ensemble (chỉ còn V4 + V5 -- đã bỏ V3 vì V3 cần 3 input
        # (physActivity, genHealth) mà hệ thống chưa thu thập được, trước đây
        # bị hardcode giả -> bỏ hẳn để tránh sai lệch)
        # Giữ nguyên tỉ lệ tương đối cũ giữa V4:V5 (0.35:0.45 -> chia lại tổng=1)
        ensemble_score = (proba_v4 * 0.4375) + (proba_v5 * 0.5625)

        if ensemble_score >= 0.55:
            risk = 2
        elif ensemble_score >= 0.40:
            risk = 1
        else:
            risk = 0

    risk_labels = {0: 'Kiểm soát TỐT', 1: 'Kiểm soát TRUNG BÌNH', 2: 'Kiểm soát KÉM'}
    risk_colors = {0: 'GREEN', 1: 'YELLOW', 2: 'RED'}

    advice = generate_advice(risk, blood_sugar, systolic, bmi)

    result = {
        'riskLevel': risk,
        'riskLabel': risk_labels[risk],
        'riskColor': risk_colors[risk],
        'ruleApplied': rule_applied,
        'advice': advice,
        'summary': f"Phân tích AI ({'Rule ADA' if rule_applied else 'Ensemble V4+V5'}): {risk_labels[risk]}"
    }

    if not rule_applied:
        result['scores'] = {
            'v4_glucose':   round(proba_v4, 3),
            'v5_clinical':  round(proba_v5, 3),
            'ensemble':     round(ensemble_score, 3)
        }

    return result


# ============================================
# API Endpoints
# ============================================
@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'models': ['V4_Pima', 'V5_DiabetesPrediction'],
        'version': '2.1',
        'ensemble_weights': {'v4': 0.4375, 'v5': 0.5625}
    })


@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        return jsonify(ensemble_predict(data))
    except ValueError as e:
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/predict/batch', methods=['POST'])
def predict_batch():
    try:
        patients = request.get_json()
        if not isinstance(patients, list):
            return jsonify({'error': 'Expected a list'}), 400
        results = []
        for p in patients:
            r = ensemble_predict(p)
            r['patientId'] = p.get('patientId')
            results.append(r)
        return jsonify(results)
    except Exception as e:
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    print("🚀 GlucoTracking ML Service v2.0")
    print("📍 http://localhost:5000")
    print("   GET  /health")
    print("   POST /predict")
    print("   POST /predict/batch")
    app.run(host='0.0.0.0', port=5000, debug=False)
