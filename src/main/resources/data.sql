INSERT IGNORE INTO health_thresholds 
(patient_type, metric_type, normal_min, normal_max, warning_min, warning_max, description, patient_id)
VALUES
('adult',    'BLOOD_SUGAR', 3.9, 5.6, 5.7, 7.0,  'Đường huyết người lớn (mmol/L)',          NULL),
('elderly',  'BLOOD_SUGAR', 4.0, 6.0, 6.1, 8.0,  'Đường huyết người cao tuổi (mmol/L)',     NULL),
('pregnant', 'BLOOD_SUGAR', 3.3, 5.1, 5.2, 6.7,  'Đường huyết thai kỳ (mmol/L)',            NULL),
('child',    'BLOOD_SUGAR', 3.9, 5.6, 5.7, 7.8,  'Đường huyết trẻ em (mmol/L)',             NULL),
('adult',    'SYSTOLIC',    90,  120, 121, 140,   'Huyết áp tâm thu người lớn (mmHg)',       NULL),
('adult',    'DIASTOLIC',   60,  80,  81,  90,    'Huyết áp tâm trương người lớn (mmHg)',    NULL),
('elderly',  'SYSTOLIC',    90,  130, 131, 150,   'Huyết áp tâm thu người cao tuổi (mmHg)', NULL),
('elderly',  'DIASTOLIC',   60,  85,  86,  95,    'Huyết áp tâm trương người cao tuổi (mmHg)', NULL),
('pregnant', 'SYSTOLIC',    90,  120, 121, 140,   'Huyết áp tâm thu thai kỳ (mmHg)',         NULL),
('pregnant', 'DIASTOLIC',   60,  80,  81,  90,    'Huyết áp tâm trương thai kỳ (mmHg)',      NULL);

UPDATE health_thresholds SET metric_type = 'BLOOD_SUGAR' WHERE metric_type = 'blood_sugar';

