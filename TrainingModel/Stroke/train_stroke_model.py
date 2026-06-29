import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score, f1_score
from imblearn.over_sampling import SMOTE
import joblib

print("--- BƯỚC 1: ĐỌC VÀ LÀM SẠCH DỮ LIỆU ---")
# 1. Đọc dữ liệu từ file csv
df = pd.read_csv("dataset.csv")

# 2. Xử lý giá trị BMI bị khuyết thiếu (ở đây ta dùng Median để đơn giản và hiệu quả)
# Thay thế chuỗi 'N/A' hoặc NaN thành giá trị trống thực sự, sau đó điền bằng trung vị
df['bmi'] = pd.to_numeric(df['bmi'], errors='coerce')
bmi_median = df['bmi'].median()
df['bmi'] = df['bmi'].fillna(bmi_median)
print(f"-> Đã xử lý giá trị khuyết của BMI bằng trung vị: {bmi_median}")

# 3. Loại bỏ dòng có giới tính không xác định 'Other' (nếu có)
df = df[df['gender'] != 'Other']


print("\n--- BƯỚC 2: TIỀN XỬ LÝ DỮ LIỆU & ENCODING ---")
# 1. Loại bỏ cột 'id' không có giá trị dự đoán
df.drop(columns=['id'], inplace=True, errors='ignore')

# 2. Mã hóa các biến phân loại (Categorical columns) sang dạng số
# Ever Married
df['ever_married'] = df['ever_married'].map({'Yes': 1, 'No': 0})

# Gender
df['gender'] = df['gender'].map({'Male': 0, 'Female': 1})

# Residence Type
df['Residence_type'] = df['Residence_type'].map({'Rural': 0, 'Urban': 1})

# Work Type
df['work_type'] = df['work_type'].map({
    'Private': 0, 
    'Self-employed': 1, 
    'Govt_job': 2, 
    'children': -1, 
    'Never_worked': -2
})

# Smoking Status
df['smoking_status'] = df['smoking_status'].map({
    'never smoked': 0,
    'formerly smoked': 1,
    'smokes': 2,
    'Unknown': -1
})

# 3. Tách thuộc tính đặc trưng (X) và nhãn mục tiêu (y)
# Các cột dùng để dự đoán
feature_cols = ['gender', 'age', 'hypertension', 'heart_disease', 'work_type', 'Residence_type', 'avg_glucose_level', 'bmi', 'smoking_status']
X = df[feature_cols]
y = df['stroke']

# 4. Chia tập dữ liệu thành tập huấn luyện (Train) và tập kiểm thử (Test) tỉ lệ 70/30
X_train, X_test, y_train, y_test = train_test_split(X, y, train_size=0.7, random_state=42, stratify=y)
print(f"-> Tập huấn luyện: {X_train.shape[0]} mẫu, Tập kiểm thử: {X_test.shape[0]} mẫu")


print("\n--- BƯỚC 3: CÂN BẰNG LỚP DỮ LIỆU (SMOTE) ---")
# Sử dụng SMOTE để tạo thêm mẫu giả lập cho lớp đột quỵ (stroke = 1) để cân bằng dữ liệu train
oversample = SMOTE(random_state=42)
X_train_resh, y_train_resh = oversample.fit_resample(X_train, y_train)
print(f"-> Sau khi chạy SMOTE: Số lượng mẫu class 0: {sum(y_train_resh == 0)}, class 1: {sum(y_train_resh == 1)}")


print("\n--- BƯỚC 4: CHUẨN HÓA DỮ LIỆU & HUẤN LUYỆN MÔ HÌNH ---")
# 1. Khởi tạo bộ chuẩn hóa Standard Scaler
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train_resh)
X_test_scaled = scaler.transform(X_test)

# 2. Khởi tạo mô hình Logistic Regression (Sử dụng cấu hình tối ưu C=0.1)
model = LogisticRegression(C=0.1, penalty='l2', random_state=42)
model.fit(X_train_scaled, y_train_resh)
print("-> Đã huấn luyện xong mô hình Logistic Regression.")


print("\n--- BƯỚC 5: ĐÁNH GIÁ MÔ HÌNH ---")
# 1. Dự đoán nhãn nhị phân (0 hoặc 1)
y_pred_binary = model.predict(X_test_scaled)

# 2. Dự đoán dạng xác suất % (Lấy cột index 1 đại diện cho xác suất bị đột quỵ)
y_pred_proba = model.predict_proba(X_test_scaled)[:, 1]

# 3. In báo cáo phân loại (Classification Report)
print("\n--- Báo cáo kết quả dự đoán nhị phân (Ngưỡng mặc định 50%): ---")
print(classification_report(y_test, y_pred_binary))
print("Độ chính xác (Accuracy):", accuracy_score(y_test, y_pred_binary))
print("F1-Score:", f1_score(y_test, y_pred_binary))

# 4. Hiển thị thử nghiệm dự đoán phần trăm (%) cho 5 mẫu đầu tiên trong tập test
print("\n--- Ví dụ dự đoán dạng % nguy cơ đột quỵ cho 5 bệnh nhân đầu tiên: ---")
for i in range(5):
    prob_percent = y_pred_proba[i] * 100
    actual = y_test.iloc[i]
    pred_label = y_pred_binary[i]
    print(f"Bệnh nhân {i+1}: Nguy cơ đột quỵ dự đoán: {prob_percent:.2f}% | Kết quả nhị phân: {pred_label} | Thực tế: {actual}")


import os
from datetime import datetime

# Đảm bảo thư mục models tồn tại
os.makedirs("models", exist_ok=True)

print("\n--- BƯỚC 6: LƯU MÔ HÌNH & BỘ CHUẨN HÓA ---")
# Lưu lại mô hình và bộ chuẩn hóa scaler với timestamp để tránh ghi đè
timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
model_filename = f'models/logistic_regression_stroke_model_{timestamp}.pkl'
scaler_filename = f'models/scaler_stroke_{timestamp}.pkl'

joblib.dump(model, model_filename)
joblib.dump(scaler, scaler_filename)
print(f"-> Đã lưu file model '{model_filename}' và scaler '{scaler_filename}' thành công!")

print("\n--- BƯỚC 7: XUẤT KẾT QUẢ DỰ ĐOÁN TẬP TEST RA FILE CSV ---")
# Đảm bảo thư mục predictions tồn tại
os.makedirs("predictions", exist_ok=True)

# Tạo một bảng chứa: Kết quả thực tế, Kết quả dự đoán của mô hình, và Xác suất % nguy cơ
test_results = pd.DataFrame({
    'Actual_Stroke': y_test,
    'Predicted_Stroke': y_pred_binary,
    'Stroke_Probability_Percent': y_pred_proba * 100
})

prediction_file = f'predictions/test_predictions_lr_{timestamp}.csv'
test_results.to_csv(prediction_file, index=False)
print(f"-> Đã xuất file kết quả dự đoán chi tiết: '{prediction_file}' thành công!")



