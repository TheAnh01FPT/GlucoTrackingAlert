CÁCH CHẠY medication_service.py (bản rule-based, KHÔNG cần sklearn)
=====================================================================

Lý do đổi: máy bạn dùng Python 3.14 (quá mới), scikit-learn chưa có sẵn bản
build cho version này nên cài bị lỗi. Vì model train trên Colab đạt 100%
accuracy do học thuộc lại đúng bộ luật resolve_case() có sẵn, nên ở đây
dùng thẳng bộ luật đó trong code Python -- cho kết quả tương đương, mà
không cần cài sklearn/pandas/numpy/joblib (file case_classifier.pkl không
dùng tới nữa, có thể bỏ qua không cần copy).

1. Copy thư mục medication-ai/ này vào:
   GlucoTrackingAlert/ml-service/medication-ai/

2. Cài thư viện (chỉ cần Flask, rất nhẹ, không lỗi build):
   pip install -r requirements.txt --break-system-packages

3. Chạy service (port 5001, KHÁC port 5000 của ml_service.py):
   python medication_service.py

4. Test thử bằng curl:
   curl -X POST http://localhost:5001/suggest-medication \
     -H "Content-Type: application/json" \
     -d "{\"age\":32,\"gender\":\"FEMALE\",\"bmi\":24.5,\"bloodSugar\":9.5,\"systolic\":120,\"diastolic\":80}"

5. Bên Java:
   - Copy MedicationMlService.java vào src/main/java/.../service/
   - Sửa AiSuggestController.java: gọi medicationMlService.suggestMedication(...)
     thay vì gọi Claude API
   - Chạy song song 2 service: ml_service.py (5000) + medication_service.py (5001)
     + Spring Boot app khi demo/báo cáo

LƯU Ý QUAN TRỌNG:
- Đây vẫn chỉ là GỢI Ý, không tự tạo đơn thuốc thật - bác sĩ phải duyệt.
- Case "pediatric" sẽ luôn có cảnh báo riêng (theo QĐ 3280/5481-BYT không
  áp dụng cho người dưới 18 tuổi).
- Nếu sau này có dữ liệu đơn thuốc THẬT từ database (đã bác sĩ duyệt), nên
  quay lại train model thật (.pkl) bằng dữ liệu đó - lúc đó model mới học
  được điều gì khác biệt so với luật cũ, mới đáng dùng thay vì rule thuần.