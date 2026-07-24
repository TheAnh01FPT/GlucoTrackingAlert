const fs = require('fs');
const strokePath = 'src/main/resources/templates/healthlog/stroke-risk.html';
const heartPath = 'src/main/resources/templates/healthlog/ai-report-heart.html';

let content = fs.readFileSync(strokePath, 'utf8');

content = content.replace(/Sàng Lọc Nguy Cơ Biến Chứng Đột Quỵ/g, 'Dự Báo Nguy Cơ Biến Chứng Tim Mạch');
content = content.replace(/🧠/g, '🫀');
content = content.replace(/đột quỵ/g, 'tim mạch');
content = content.replace(/Đột quỵ/g, 'Tim mạch');
content = content.replace(/Đột Quỵ/g, 'Tim Mạch');
content = content.replace(/ti-brain/g, 'ti-heart-broken');
content = content.replace(/WEEKLY_STROKE_RISK/g, 'WEEKLY_HEART_RISK');
content = content.replace(/stroke-risk/g, 'heart-risk');
content = content.replace(/ti-shield-checkered/g, 'ti-heart-rate-monitor');
content = content.replace(/chụp MRI\/CT não/g, 'đo điện tâm đồ (ECG) / siêu âm tim');
content = content.replace(/Lịch Sử Tim Mạch Tuần/g, 'Lịch Sử Tim Mạch Tuần'); // To keep it clean, let's fix any "Lịch Sử Tim mạch Tuần" capitalization
content = content.replace(/Lịch Sử Tim mạch Tuần/g, 'Lịch Sử Tim Mạch Tuần');
content = content.replace(/ai-report-heart/g, 'ai-report-heart');

fs.writeFileSync(heartPath, content, 'utf8');
console.log('Done replacing.');
