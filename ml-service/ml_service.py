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
# Load 3 models
# ============================================
model_v3 = joblib.load(os.path.join(BASE_DIR, 'gluco_model_final.pkl'))   # CDC BRFSS
model_v4 = joblib.load(os.path.join(BASE_DIR, 'gluco_model_v4.pkl'))      # Pima
model_v5 = joblib.load(os.path.join(BASE_DIR, 'gluco_model_v5.pkl'))      # Diabetes Prediction

print("✅ Models loaded: V3 (CDC) + V4 (Pima) + V5 (Diabetes Prediction)")

# ============================================
# Khuyến nghị
# ============================================
def generate_advice(risk_level, blood_sugar, systolic, bmi):
    advice = []

    if blood_sugar >= 11.1:
        advice.append("Đường huyết vượt ngưỡng chẩn đoán tiểu đường (ADA: ≥11.1 mmol/L). Cần kiểm tra HbA1c ngay.")
    elif blood_sugar >= 7.8:
        advice.append("Đường huyết trong vùng nghi ngờ tiểu đường. Khuyến nghị nhịn ăn và đo lại sau 2 giờ.")
    elif blood_sugar >= 5.6:
        advice.append("Đường huyết ở mức tiền tiểu đường. Theo dõi chặt chẽ và điều chỉnh chế độ ăn.")

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
    blood_sugar = float(data.get('bloodSugar', 5.5))
    systolic    = int(data.get('systolic', 120))
    diastolic   = int(data.get('diastolic', 80))
    bmi         = float(data.get('bmi', 22.0))
    age         = int(data.get('age', 40))
    gender      = data.get('gender', 'MALE')
    is_pregnant = bool(data.get('isPregnant', False))
    smoker      = bool(data.get('smoker', False))
    phys_active = bool(data.get('physActivity', True))
    gen_health  = int(data.get('genHealth', 3))
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

        # Model V3 — CDC BRFSS (lifestyle)
        x_v3 = np.array([[
            high_bp, 0, bmi,
            1 if smoker else 0, 0, 0,
            1 if phys_active else 0,
            1, 1, 0, gen_health, 0, 0, 0,
            0 if gender == 'MALE' else 1,
            min(13, max(1, age // 5)), 3
        ]])
        proba_v3 = float(model_v3.predict_proba(x_v3)[0][2])  # prob tiểu đường

        # Model V4 — Pima (glucose trực tiếp)
        x_v4 = np.array([[
            1 if is_pregnant else 0,
            glucose_mgdl, systolic,
            25.0, bmi, 0.5, age
        ]])
        proba_v4 = float(model_v4.predict_proba(x_v4)[0][1])

        # Model V5 — Diabetes Prediction (blood_glucose + hypertension)
        smoke_enc = 2 if smoker else 0
        gender_enc = 1 if gender == 'FEMALE' else 0
        x_v5 = np.array([[
            glucose_mgdl, bmi, age,
            hypertension, 0,
            smoke_enc, gender_enc
        ]])
        proba_v5 = float(model_v5.predict_proba(x_v5)[0][1])

        # Weighted ensemble
        # V5 có recall tốt nhất (87%) → weight cao nhất
        ensemble_score = (proba_v3 * 0.20) + (proba_v4 * 0.35) + (proba_v5 * 0.45)

        if ensemble_score >= 0.55:
            risk = 2
        elif ensemble_score >= 0.40:
            risk = 1
        else:
            risk = 0

    risk_labels = {0: 'Nguy cơ THẤP', 1: 'Nguy cơ TRUNG BÌNH', 2: 'Nguy cơ CAO'}
    risk_colors = {0: 'GREEN', 1: 'YELLOW', 2: 'RED'}

    advice = generate_advice(risk, blood_sugar, systolic, bmi)

    result = {
        'riskLevel': risk,
        'riskLabel': risk_labels[risk],
        'riskColor': risk_colors[risk],
        'ruleApplied': rule_applied,
        'advice': advice,
        'summary': f"Phân tích AI ({'Rule ADA' if rule_applied else 'Ensemble V3+V4+V5'}): {risk_labels[risk]}"
    }

    if not rule_applied:
        result['scores'] = {
            'v3_lifestyle': round(proba_v3, 3),
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
        'models': ['V3_CDC', 'V4_Pima', 'V5_DiabetesPrediction'],
        'version': '2.0',
        'ensemble_weights': {'v3': 0.20, 'v4': 0.35, 'v5': 0.45}
    })


@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        return jsonify(ensemble_predict(data))
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
