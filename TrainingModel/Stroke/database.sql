-- SQL Script generated from DBML Schema
-- Target database: MySQL

CREATE DATABASE IF NOT EXISTS healthcare_db;
USE healthcare_db;

-- Table: roles
CREATE TABLE roles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu danh sách vai trò trong hệ thống như Admin, Patient, Doctor. Dùng cho phân quyền đăng nhập và quản lý người dùng.';

-- Table: users
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  role_id INT NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(150),
  phone VARCHAR(20),
  status VARCHAR(30) NOT NULL DEFAULT 'pending_verification',
  email_verified BOOLEAN DEFAULT FALSE,
  last_login_at DATETIME,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu tài khoản đăng nhập chung cho Admin, Patient, Doctor. Guest sau khi đăng ký sẽ tạo bản ghi tại đây và cần xác thực email.';

-- Table: email_verifications
CREATE TABLE email_verifications (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  verification_token VARCHAR(255) NOT NULL UNIQUE,
  expired_at DATETIME NOT NULL,
  verified_at DATETIME,
  status VARCHAR(30) DEFAULT 'pending',
  created_at DATETIME
) COMMENT = 'Lưu token xác thực email khi Guest đăng ký tài khoản. Dùng để kiểm tra link xác thực còn hạn hay không.';

-- Table: password_reset_tokens
CREATE TABLE password_reset_tokens (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  reset_token VARCHAR(255) NOT NULL UNIQUE,
  expired_at DATETIME NOT NULL,
  used_at DATETIME,
  status VARCHAR(30) DEFAULT 'pending',
  created_at DATETIME
) COMMENT = 'Lưu token đặt lại mật khẩu khi người dùng quên mật khẩu.';

-- Table: patients
CREATE TABLE patients (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL UNIQUE,
  full_name VARCHAR(150) NOT NULL,
  date_of_birth DATE,
  age INT,
  gender VARCHAR(20),
  phone VARCHAR(20),
  address VARCHAR(255),
  height_cm DECIMAL(5,2),
  weight_kg DECIMAL(5,2),
  bmi DECIMAL(5,2),
  status VARCHAR(30) DEFAULT 'active',
  identity_card VARCHAR(20) NULL COMMENT 'Căn cước công dân (CCCD)',
  insurance_number VARCHAR(50) NULL COMMENT 'Mã bảo hiểm y tế (BHYT)',
  is_pregnant BOOLEAN DEFAULT FALSE COMMENT 'Trạng thái có thai (chỉ áp dụng cho Nữ)',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu hồ sơ bệnh nhân. Dùng cho chức năng nhập profile cá nhân, tính BMI, theo dõi chỉ số và hiển thị cho bác sĩ.';

-- Table: doctors
CREATE TABLE doctors (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL UNIQUE,
  full_name VARCHAR(150) NOT NULL,
  specialization VARCHAR(150),
  degree VARCHAR(100),
  experience_years INT,
  workplace VARCHAR(150),
  phone VARCHAR(20),
  introduction TEXT,
  avatar_url VARCHAR(255),
  status VARCHAR(30) DEFAULT 'active',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu hồ sơ bác sĩ trong hệ thống. Doctor dùng để xem bệnh nhân được phân công và đưa khuyến nghị theo dõi, không dùng để chẩn đoán chính thức.';

-- Table: relatives
CREATE TABLE relatives (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  relationship VARCHAR(50),
  age INT,
  phone VARCHAR(20),
  email VARCHAR(150) NOT NULL,
  notify_enabled BOOLEAN DEFAULT TRUE,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu thông tin người thân của bệnh nhân. Người thân nhận email cảnh báo khi chỉ số bệnh nhân vượt ngưỡng an toàn.';

-- Table: doctor_patient_assignments
CREATE TABLE doctor_patient_assignments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  doctor_id INT NOT NULL,
  patient_id INT NOT NULL,
  assigned_at DATETIME,
  status VARCHAR(30) DEFAULT 'active',
  note TEXT,
  UNIQUE KEY idx_doctor_patient (doctor_id, patient_id)
) COMMENT = 'Bảng trung gian thể hiện quan hệ nhiều-nhiều giữa bác sĩ và bệnh nhân. Dùng để giới hạn bác sĩ chỉ xem bệnh nhân được phân công.';

-- Table: daily_health_logs
CREATE TABLE daily_health_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  log_date DATE NOT NULL,
  blood_sugar DECIMAL(6,2),
  systolic INT,
  diastolic INT,
  sleep_hours DECIMAL(4,2),
  water_ml INT,
  sugar_consumption_level VARCHAR(20),
  symptoms TEXT,
  note TEXT,
  physical_activity INT DEFAULT 0,
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY idx_patient_log_date (patient_id, log_date)
) COMMENT = 'Lưu chỉ số sức khỏe bệnh nhân nhập theo ngày như đường huyết, huyết áp, giấc ngủ, lượng nước, mức tiêu thụ đường và triệu chứng. Đây là dữ liệu gốc để AI phân tích, tạo cảnh báo và báo cáo tuần.';

-- Table: meal_logs
CREATE TABLE meal_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  daily_health_log_id INT,
  meal_date DATE NOT NULL,
  meal_time VARCHAR(30),
  food_description TEXT,
  sugar_level VARCHAR(20),
  carb_estimation DECIMAL(6,2),
  note TEXT,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu thông tin bữa ăn nếu hệ thống muốn theo dõi chi tiết chế độ ăn. Có thể liên kết với daily_health_logs để tổng hợp mức tiêu thụ đường trong ngày.';

-- Table: weekly_health_reports
CREATE TABLE weekly_health_reports (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  baseline_id INT,
  previous_report_id INT,
  week_start DATE NOT NULL,
  week_end DATE NOT NULL,
  average_blood_sugar DECIMAL(6,2),
  average_systolic DECIMAL(6,2),
  average_diastolic DECIMAL(6,2),
  average_sleep_hours DECIMAL(4,2),
  average_water_ml DECIMAL(8,2),
  high_sugar_days INT,
  warning_count INT,
  blood_sugar_change DECIMAL(6,2),
  blood_sugar_change_percent DECIMAL(5,2),
  systolic_change DECIMAL(6,2),
  diastolic_change DECIMAL(6,2),
  sleep_hours_change DECIMAL(4,2),
  trend_status VARCHAR(30),
  health_status VARCHAR(30),
  ai_summary TEXT,
  recommendation TEXT,
  created_at DATETIME,
  UNIQUE KEY idx_patient_week (patient_id, week_start, week_end)
) COMMENT = 'Lưu báo cáo sức khỏe theo tuần. Hệ thống tổng hợp daily_health_logs, so sánh với tuần trước hoặc baseline, rồi lưu xu hướng Improved/Stable/Worse.';

-- Table: health_baselines
CREATE TABLE health_baselines (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  source_health_log_id INT,
  baseline_date DATE NOT NULL,
  baseline_type VARCHAR(50),
  blood_sugar DECIMAL(6,2),
  systolic INT,
  diastolic INT,
  sleep_hours DECIMAL(4,2),
  water_ml INT,
  sugar_consumption_level VARCHAR(20),
  weight_kg DECIMAL(5,2),
  bmi DECIMAL(5,2),
  note TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME
) COMMENT = 'Lưu mốc sức khỏe dùng để so sánh. Mốc có thể lấy từ daily_health_logs ngày đầu, trung bình tuần đầu hoặc do bác sĩ/admin chọn lại.';

-- Table: risk_assessments
CREATE TABLE risk_assessments (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  daily_health_log_id INT,
  weekly_report_id INT,
  assessment_type VARCHAR(50) NOT NULL,
  risk_level VARCHAR(30),
  risk_percentage DECIMAL(5,2),
  ai_summary TEXT,
  recommendation TEXT,
  assessed_at DATETIME
) COMMENT = 'Lưu kết quả đánh giá nguy cơ sau khi AI hoặc hệ thống phân tích dữ liệu. Không lưu dữ liệu gốc bệnh nhân nhập, mà lưu kết quả như Low/Medium/High/Critical, phần trăm nguy cơ, tóm tắt AI và khuyến nghị.';

-- Table: risk_warnings
CREATE TABLE risk_warnings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  risk_assessment_id INT,
  daily_health_log_id INT,
  risk_type VARCHAR(50),
  risk_level VARCHAR(30),
  risk_percentage DECIMAL(5,2),
  message TEXT NOT NULL,
  status VARCHAR(30) DEFAULT 'new',
  notified BOOLEAN DEFAULT FALSE,
  created_at DATETIME,
  acknowledged_at DATETIME,
  resolved_at DATETIME
) COMMENT = 'Lưu cảnh báo cụ thể khi chỉ số vượt ngưỡng, ví dụ đường huyết cao, huyết áp cao, ngủ quá ít. Bảng này dùng để gửi email cho bệnh nhân/người thân và hiển thị warning history.';

-- Table: threshold_rules
CREATE TABLE threshold_rules (
  id INT AUTO_INCREMENT PRIMARY KEY,
  metric_code VARCHAR(50) NOT NULL,
  metric_name VARCHAR(100) NOT NULL,
  operator VARCHAR(20) NOT NULL,
  threshold_value DECIMAL(8,2) NOT NULL,
  risk_level VARCHAR(30) NOT NULL,
  message_template TEXT,
  status VARCHAR(30) DEFAULT 'active',
  created_by INT,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu cấu hình ngưỡng cảnh báo do Admin quản lý, ví dụ blood_sugar > 180 thì High Risk. Giúp hệ thống không hard-code điều kiện cảnh báo.';

-- Table: health_reminders
CREATE TABLE health_reminders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT NOT NULL,
  reminder_type VARCHAR(50) NOT NULL,
  title VARCHAR(150),
  message TEXT NOT NULL,
  reminder_time DATETIME NOT NULL,
  repeat_type VARCHAR(30),
  status VARCHAR(30) DEFAULT 'active',
  is_sent BOOLEAN DEFAULT FALSE,
  sent_at DATETIME,
  google_calendar_event_id VARCHAR(255),
  google_sync_status VARCHAR(30),
  last_sync_at DATETIME,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu nhắc nhở sức khỏe cho bệnh nhân như nhập chỉ số, kiểm tra đường huyết, uống nước, xem báo cáo tuần. Nếu kết nối Google Calendar, bảng này lưu thêm event_id và trạng thái đồng bộ.';

-- Table: google_integrations
CREATE TABLE google_integrations (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  google_email VARCHAR(150),
  access_token TEXT,
  refresh_token TEXT,
  token_expired_at DATETIME,
  calendar_connected BOOLEAN DEFAULT FALSE,
  gmail_connected BOOLEAN DEFAULT FALSE,
  status VARCHAR(30) DEFAULT 'active',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu thông tin kết nối Google của người dùng. Dùng cho chức năng đồng bộ nhắc nhở sang Google Calendar và gửi email qua Gmail nếu hệ thống tích hợp OAuth.';

-- Table: notification_logs
CREATE TABLE notification_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT,
  relative_id INT,
  user_id INT,
  warning_id INT,
  reminder_id INT,
  weekly_report_id INT,
  notification_type VARCHAR(50),
  channel VARCHAR(30) DEFAULT 'email',
  recipient_email VARCHAR(150),
  subject VARCHAR(255),
  message TEXT,
  status VARCHAR(30),
  sent_at DATETIME,
  error_message TEXT,
  created_at DATETIME
) COMMENT = 'Lưu lịch sử gửi thông báo/email như email xác thực, email cảnh báo, email nhắc nhở, email báo cáo tuần. Gmail Gateway chỉ là dịch vụ gửi, còn bảng này lưu log trong hệ thống.';

-- Table: doctor_recommendations
CREATE TABLE doctor_recommendations (
  id INT AUTO_INCREMENT PRIMARY KEY,
  doctor_id INT NOT NULL,
  patient_id INT NOT NULL,
  weekly_report_id INT,
  risk_warning_id INT,
  title VARCHAR(150),
  recommendation TEXT NOT NULL,
  status VARCHAR(30) DEFAULT 'active',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu khuyến nghị theo dõi của bác sĩ dành cho bệnh nhân. Không phải chẩn đoán hoặc điều trị chính thức, chỉ là nhận xét và hướng theo dõi sức khỏe.';

-- Table: ai_analysis_logs
CREATE TABLE ai_analysis_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_id INT,
  daily_health_log_id INT,
  weekly_report_id INT,
  analysis_type VARCHAR(50),
  input_data TEXT,
  output_result TEXT,
  risk_level VARCHAR(30),
  created_at DATETIME
) COMMENT = 'Lưu lịch sử AI phân tích dữ liệu. Dùng để debug, giải thích kết quả AI, audit nhẹ và hỗ trợ bác sĩ xem AI đã phân tích dựa trên dữ liệu nào.';

-- Table: notifications
CREATE TABLE notifications (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  title VARCHAR(150),
  message TEXT,
  notification_type VARCHAR(50),
  is_read BOOLEAN DEFAULT FALSE,
  related_warning_id INT,
  related_report_id INT,
  created_at DATETIME,
  read_at DATETIME
) COMMENT = 'Lưu thông báo hiển thị trong website như notification bell. Khác với notification_logs là bảng này phục vụ hiển thị trong hệ thống, còn notification_logs phục vụ lịch sử gửi email.';

-- Table: banners
CREATE TABLE banners (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(150) NOT NULL,
  subtitle VARCHAR(255),
  image_url VARCHAR(255),
  redirect_url VARCHAR(255),
  display_order INT,
  status VARCHAR(30) DEFAULT 'active',
  created_by INT,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu banner quảng bá trên homepage. Admin có thể tạo, sửa, xóa banner để hiển thị nội dung truyền thông của website.';

-- Table: health_articles
CREATE TABLE health_articles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL UNIQUE,
  summary TEXT,
  content TEXT,
  thumbnail_url VARCHAR(255),
  category VARCHAR(100),
  status VARCHAR(30) DEFAULT 'draft',
  published_at DATETIME,
  created_by INT,
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu bài viết thông tin sức khỏe/tiểu đường trên website. Guest có thể xem danh sách và chi tiết bài viết.';

-- Table: doctor_introductions
CREATE TABLE doctor_introductions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  doctor_id INT,
  display_name VARCHAR(150) NOT NULL,
  title VARCHAR(150),
  introduction TEXT,
  avatar_url VARCHAR(255),
  display_order INT,
  status VARCHAR(30) DEFAULT 'active',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu nội dung giới thiệu bác sĩ ở trang chủ. Có thể lấy từ doctors hoặc nhập riêng phục vụ marketing/public website.';

-- Table: patient_testimonials
CREATE TABLE patient_testimonials (
  id INT AUTO_INCREMENT PRIMARY KEY,
  patient_name VARCHAR(150) NOT NULL,
  avatar_url VARCHAR(255),
  content TEXT NOT NULL,
  rating INT,
  display_order INT,
  status VARCHAR(30) DEFAULT 'active',
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = 'Lưu đánh giá/cảm nhận của bệnh nhân cũ để hiển thị trên homepage. Đây là nội dung quảng bá công khai, không gắn trực tiếp với dữ liệu y tế nhạy cảm.';

-- Table: contact_requests
CREATE TABLE contact_requests (
  id INT AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150),
  phone VARCHAR(20),
  subject VARCHAR(255),
  message TEXT,
  status VARCHAR(30) DEFAULT 'new',
  created_at DATETIME,
  handled_at DATETIME,
  handled_by INT
) COMMENT = 'Lưu yêu cầu liên hệ từ Guest trên website. Admin có thể xem và xử lý các contact request.';

-- Table: export_requests
CREATE TABLE export_requests (
  id INT AUTO_INCREMENT PRIMARY KEY,
  requested_by INT NOT NULL,
  export_type VARCHAR(50) NOT NULL,
  patient_id INT,
  weekly_report_id INT,
  file_url VARCHAR(255),
  status VARCHAR(30) DEFAULT 'pending',
  created_at DATETIME,
  completed_at DATETIME
) COMMENT = 'Lưu yêu cầu xuất báo cáo như patient health report, weekly report, warning report. Dùng cho chức năng Report & Export.';


-- --- FOREIGN KEY CONSTRAINTS (Relationships) ---

-- Ref: users.role_id > roles.id
ALTER TABLE users
  ADD CONSTRAINT fk_users_role_id FOREIGN KEY (role_id) REFERENCES roles(id);

-- Ref: email_verifications.user_id > users.id
ALTER TABLE email_verifications
  ADD CONSTRAINT fk_email_verifications_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: password_reset_tokens.user_id > users.id
ALTER TABLE password_reset_tokens
  ADD CONSTRAINT fk_password_reset_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: patients.user_id - users.id
ALTER TABLE patients
  ADD CONSTRAINT fk_patients_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: doctors.user_id - users.id
ALTER TABLE doctors
  ADD CONSTRAINT fk_doctors_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: relatives.patient_id > patients.id
ALTER TABLE relatives
  ADD CONSTRAINT fk_relatives_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: doctor_patient_assignments.doctor_id > doctors.id
ALTER TABLE doctor_patient_assignments
  ADD CONSTRAINT fk_doctor_patient_assignments_doctor_id FOREIGN KEY (doctor_id) REFERENCES doctors(id);

-- Ref: doctor_patient_assignments.patient_id > patients.id
ALTER TABLE doctor_patient_assignments
  ADD CONSTRAINT fk_doctor_patient_assignments_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: daily_health_logs.patient_id > patients.id
ALTER TABLE daily_health_logs
  ADD CONSTRAINT fk_daily_health_logs_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: meal_logs.patient_id > patients.id
ALTER TABLE meal_logs
  ADD CONSTRAINT fk_meal_logs_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: meal_logs.daily_health_log_id > daily_health_logs.id
ALTER TABLE meal_logs
  ADD CONSTRAINT fk_meal_logs_daily_health_log_id FOREIGN KEY (daily_health_log_id) REFERENCES daily_health_logs(id);

-- Ref: health_baselines.patient_id > patients.id
ALTER TABLE health_baselines
  ADD CONSTRAINT fk_health_baselines_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: health_baselines.source_health_log_id > daily_health_logs.id
ALTER TABLE health_baselines
  ADD CONSTRAINT fk_health_baselines_source_health_log_id FOREIGN KEY (source_health_log_id) REFERENCES daily_health_logs(id);

-- Ref: risk_assessments.patient_id > patients.id
ALTER TABLE risk_assessments
  ADD CONSTRAINT fk_risk_assessments_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: risk_assessments.daily_health_log_id > daily_health_logs.id
ALTER TABLE risk_assessments
  ADD CONSTRAINT fk_risk_assessments_daily_health_log_id FOREIGN KEY (daily_health_log_id) REFERENCES daily_health_logs(id);

-- Ref: risk_assessments.weekly_report_id > weekly_health_reports.id
ALTER TABLE risk_assessments
  ADD CONSTRAINT fk_risk_assessments_weekly_report_id FOREIGN KEY (weekly_report_id) REFERENCES weekly_health_reports(id);

-- Ref: risk_warnings.patient_id > patients.id
ALTER TABLE risk_warnings
  ADD CONSTRAINT fk_risk_warnings_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: risk_warnings.risk_assessment_id > risk_assessments.id
ALTER TABLE risk_warnings
  ADD CONSTRAINT fk_risk_warnings_risk_assessment_id FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessments(id);

-- Ref: risk_warnings.daily_health_log_id > daily_health_logs.id
ALTER TABLE risk_warnings
  ADD CONSTRAINT fk_risk_warnings_daily_health_log_id FOREIGN KEY (daily_health_log_id) REFERENCES daily_health_logs(id);

-- Ref: threshold_rules.created_by > users.id
ALTER TABLE threshold_rules
  ADD CONSTRAINT fk_threshold_rules_created_by FOREIGN KEY (created_by) REFERENCES users(id);

-- Ref: weekly_health_reports.patient_id > patients.id
ALTER TABLE weekly_health_reports
  ADD CONSTRAINT fk_weekly_health_reports_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: weekly_health_reports.baseline_id > health_baselines.id
ALTER TABLE weekly_health_reports
  ADD CONSTRAINT fk_weekly_health_reports_baseline_id FOREIGN KEY (baseline_id) REFERENCES health_baselines(id);

-- Ref: weekly_health_reports.previous_report_id > weekly_health_reports.id
ALTER TABLE weekly_health_reports
  ADD CONSTRAINT fk_weekly_health_reports_prev_id FOREIGN KEY (previous_report_id) REFERENCES weekly_health_reports(id);

-- Ref: health_reminders.patient_id > patients.id
ALTER TABLE health_reminders
  ADD CONSTRAINT fk_health_reminders_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: google_integrations.user_id > users.id
ALTER TABLE google_integrations
  ADD CONSTRAINT fk_google_integrations_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: notification_logs.patient_id > patients.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: notification_logs.relative_id > relatives.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_relative_id FOREIGN KEY (relative_id) REFERENCES relatives(id);

-- Ref: notification_logs.user_id > users.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: notification_logs.warning_id > risk_warnings.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_warning_id FOREIGN KEY (warning_id) REFERENCES risk_warnings(id);

-- Ref: notification_logs.reminder_id > health_reminders.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_reminder_id FOREIGN KEY (reminder_id) REFERENCES health_reminders(id);

-- Ref: notification_logs.weekly_report_id > weekly_health_reports.id
ALTER TABLE notification_logs
  ADD CONSTRAINT fk_notification_logs_weekly_report_id FOREIGN KEY (weekly_report_id) REFERENCES weekly_health_reports(id);

-- Ref: doctor_recommendations.doctor_id > doctors.id
ALTER TABLE doctor_recommendations
  ADD CONSTRAINT fk_doctor_recommendations_doctor_id FOREIGN KEY (doctor_id) REFERENCES doctors(id);

-- Ref: doctor_recommendations.patient_id > patients.id
ALTER TABLE doctor_recommendations
  ADD CONSTRAINT fk_doctor_recommendations_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: doctor_recommendations.weekly_report_id > weekly_health_reports.id
ALTER TABLE doctor_recommendations
  ADD CONSTRAINT fk_doctor_recommendations_weekly_report_id FOREIGN KEY (weekly_report_id) REFERENCES weekly_health_reports(id);

-- Ref: doctor_recommendations.risk_warning_id > risk_warnings.id
ALTER TABLE doctor_recommendations
  ADD CONSTRAINT fk_doctor_recommendations_risk_warning_id FOREIGN KEY (risk_warning_id) REFERENCES risk_warnings(id);

-- Ref: ai_analysis_logs.patient_id > patients.id
ALTER TABLE ai_analysis_logs
  ADD CONSTRAINT fk_ai_analysis_logs_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: ai_analysis_logs.daily_health_log_id > daily_health_logs.id
ALTER TABLE ai_analysis_logs
  ADD CONSTRAINT fk_ai_analysis_logs_daily_log_id FOREIGN KEY (daily_health_log_id) REFERENCES daily_health_logs(id);

-- Ref: ai_analysis_logs.weekly_report_id > weekly_health_reports.id
ALTER TABLE ai_analysis_logs
  ADD CONSTRAINT fk_ai_analysis_logs_weekly_report_id FOREIGN KEY (weekly_report_id) REFERENCES weekly_health_reports(id);

-- Ref: notifications.user_id > users.id
ALTER TABLE notifications
  ADD CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(id);

-- Ref: notifications.related_warning_id > risk_warnings.id
ALTER TABLE notifications
  ADD CONSTRAINT fk_notifications_related_warning_id FOREIGN KEY (related_warning_id) REFERENCES risk_warnings(id);

-- Ref: notifications.related_report_id > weekly_health_reports.id
ALTER TABLE notifications
  ADD CONSTRAINT fk_notifications_related_report_id FOREIGN KEY (related_report_id) REFERENCES weekly_health_reports(id);

-- Ref: banners.created_by > users.id
ALTER TABLE banners
  ADD CONSTRAINT fk_banners_created_by FOREIGN KEY (created_by) REFERENCES users(id);

-- Ref: health_articles.created_by > users.id
ALTER TABLE health_articles
  ADD CONSTRAINT fk_health_articles_created_by FOREIGN KEY (created_by) REFERENCES users(id);

-- Ref: doctor_introductions.doctor_id > doctors.id
ALTER TABLE doctor_introductions
  ADD CONSTRAINT fk_doctor_introductions_doctor_id FOREIGN KEY (doctor_id) REFERENCES doctors(id);

-- Ref: contact_requests.handled_by > users.id
ALTER TABLE contact_requests
  ADD CONSTRAINT fk_contact_requests_handled_by FOREIGN KEY (handled_by) REFERENCES users(id);

-- Ref: export_requests.requested_by > users.id
ALTER TABLE export_requests
  ADD CONSTRAINT fk_export_requests_requested_by FOREIGN KEY (requested_by) REFERENCES users(id);

-- Ref: export_requests.patient_id > patients.id
ALTER TABLE export_requests
  ADD CONSTRAINT fk_export_requests_patient_id FOREIGN KEY (patient_id) REFERENCES patients(id);

-- Ref: export_requests.weekly_report_id > weekly_health_reports.id
ALTER TABLE export_requests
  ADD CONSTRAINT fk_export_requests_weekly_report_id FOREIGN KEY (weekly_report_id) REFERENCES weekly_health_reports(id);