from flask import Flask, request, jsonify
import joblib
import numpy as np

app = Flask(__name__)

# 1. Nạp mô hình AI và bộ chuẩn hóa đã train từ Colab vào bộ nhớ
model = joblib.load('cardio_xgb_model.pkl')
scaler = joblib.load('cardio_scaler.pkl')

@app.route('/predict-cardio', methods=['POST'])
def predict_cardio():
    try:
        # Lấy dữ liệu JSON từ Spring Boot gửi sang
        data = request.json
        
        # Sắp xếp các trường dữ liệu đầu vào theo đúng thứ tự lúc huấn luyện mô hình
        # Đã map lại key theo dữ liệu thực tế từ hồ sơ nền + nhật ký sức khỏe mới nhất
        features = np.array([[
            float(data['age_days']),
            float(data['gender']),
            float(data['height']),
            float(data['weight']),
            float(data['systolic']),      # Thay cho ap_hi cũ
            float(data['diastolic']),     # Thay cho ap_lo cũ
            float(data['cholesterol']),
            float(data['blood_sugar']),   # Thay cho gluc cũ
            float(data['smoke']),         # Nhận giá trị 0 hoặc 1 map tự động từ Java
            float(data['alco']),
            float(data['active'])
        ]])
        
        # 2. Thực hiện chuẩn hóa dữ liệu đầu vào
        features_scaled = scaler.transform(features)
        
        # 3. Dự đoán xác suất nguy cơ mắc bệnh (%)
        prediction_prob = model.predict_proba(features_scaled)[0][1]
        risk_percentage = float(round(prediction_prob * 100, 2))
        
        # 4. Tự động phân cấp nguy cơ (riskLevel) và sinh lời khuyên (advice) phù hợp
        if risk_percentage < 20.0:
            risk_level = "LOW"
            summary = "Nguy cơ tim mạch thấp"
            advice = "Duy trì lối sống lành mạnh, hạn chế đồ dầu mỡ và tập thể dục đều đặn."
        elif risk_percentage < 50.0:
            risk_level = "MEDIUM"
            summary = "Nguy cơ tim mạch trung bình"
            advice = "Chú ý theo dõi huyết áp thường xuyên, giảm ăn mặn và hạn chế tối đa căng thẳng."
        else:
            risk_level = "HIGH"
            summary = "Nguy cơ tim mạch CAO"
            advice = "Cảnh báo! Hãy tham khảo ý kiến bác sĩ chuyên khoa sớm để kiểm tra chi tiết hệ tim mạch."

        # Trả đầy đủ dữ liệu về cho Spring Boot để hiển thị lên giao diện
        return jsonify({
            'status': 'success',
            'cardio_risk_percentage': risk_percentage,
            'riskLevel': risk_level,
            'riskPercentage': risk_percentage,
            'summary': summary,
            'advice': advice
        })
        
    except Exception as e:
        # Trả về chi tiết lỗi giúp bạn dễ Debug ở console của Spring Boot
        return jsonify({'status': 'error', 'message': f"Lỗi tiền xử lý dữ liệu AI: {str(e)}"}), 400

if __name__ == '__main__':
    # Chạy API Server ở cổng 5000
    app.run(host='0.0.0.0', port=5000)