package fpt.swp391.GlucoTrackAlert.service.impl.export;

import fpt.swp391.GlucoTrackAlert.model.healthlog.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.model.patient.Patient;
import fpt.swp391.GlucoTrackAlert.repository.healthlog.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.service.export.ExportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final DailyHealthLogRepository dailyHealthLogRepository;
    private final PatientRepository patientRepository;

    @Override
    public ByteArrayInputStream exportDailyLogsToExcel(Long patientId, LocalDate fromDate, LocalDate toDate) {
        Patient patient = patientRepository.findById(patientId).orElse(null);
        List<DailyHealthLog> logs = dailyHealthLogRepository.findByPatientIdAndLogDateBetweenOrderByLogDate(patientId, fromDate, toDate);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Nhật Ký Đo");

            // --- Styles Configuration ---
            // Title Style
            Font titleFont = workbook.createFont();
            titleFont.setFontName("Arial");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.INDIGO.getIndex());
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            // Subtitle Style (Patient info & date range)
            Font subtitleFont = workbook.createFont();
            subtitleFont.setFontName("Arial");
            subtitleFont.setFontHeightInPoints((short) 11);
            subtitleFont.setItalic(true);
            CellStyle subtitleStyle = workbook.createCellStyle();
            subtitleStyle.setFont(subtitleFont);
            subtitleStyle.setAlignment(HorizontalAlignment.CENTER);

            // Table Header Style (Indigo background, white bold text)
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Arial");
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorder(headerStyle);

            // Data Style (Standard text with thin borders)
            Font dataFont = workbook.createFont();
            dataFont.setFontName("Arial");
            dataFont.setFontHeightInPoints((short) 11);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setFont(dataFont);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            setBorder(dataStyle);

            CellStyle noteStyle = workbook.createCellStyle();
            noteStyle.setFont(dataFont);
            noteStyle.setAlignment(HorizontalAlignment.LEFT);
            setBorder(noteStyle);

            // --- Writing Content ---
            int rowIdx = 0;

            // Row 0: Big Title
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO NHẬT KÝ SỨC KHỎE CHI TIẾT");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            // Row 1: Patient Details
            Row patientRow = sheet.createRow(rowIdx++);
            Cell patientCell = patientRow.createCell(0);
            String patientInfo = "Chưa tìm thấy hồ sơ bệnh nhân";
            if (patient != null) {
                String gender = patient.getGender() != null ? patient.getGender() : "N/A";
                Integer age = patient.getAge() != null ? patient.getAge() : 0;
                patientInfo = String.format("Bệnh nhân: %s  |  Tuổi: %d  |  Giới tính: %s", patient.getFullName(), age, gender);
            }
            patientCell.setCellValue(patientInfo);
            patientCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));

            // Row 2: Date Range
            Row dateRow = sheet.createRow(rowIdx++);
            Cell dateCell = dateRow.createCell(0);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            LocalDate displayFromDate = fromDate;
            if (fromDate.equals(LocalDate.of(2000, 1, 1)) && logs != null && !logs.isEmpty()) {
                displayFromDate = logs.get(0).getLogDate();
            }

            dateCell.setCellValue(String.format("Thời gian báo cáo: Từ ngày %s đến ngày %s", displayFromDate.format(dtf), toDate.format(dtf)));
            dateCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 8));

            // Row 3: Spacing
            sheet.createRow(rowIdx++);

            // Row 4: Table Headers
            Row headerRow = sheet.createRow(rowIdx++);
            String[] headers = {
                    "Ngày đo",
                    "Đường huyết (mmol/L)",
                    "Huyết áp tâm thu (mmHg)",
                    "Huyết áp tâm trương (mmHg)",
                    "Giờ ngủ (giờ)",
                    "Lượng nước (ml)",
                    "Mức tiêu thụ đường",
                    "Triệu chứng",
                    "Ghi chú"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Row 5+: Data rows
            if (logs != null && !logs.isEmpty()) {
                for (DailyHealthLog log : logs) {
                    Row row = sheet.createRow(rowIdx++);

                    // Column 0: Log Date
                    Cell cell0 = row.createCell(0);
                    cell0.setCellValue(log.getLogDate().format(dtf));
                    cell0.setCellStyle(dataStyle);

                    // Column 1: Blood Sugar
                    Cell cell1 = row.createCell(1);
                    if (log.getBloodSugar() != null) {
                        cell1.setCellValue(log.getBloodSugar().doubleValue());
                    } else {
                        cell1.setCellValue("--");
                    }
                    cell1.setCellStyle(dataStyle);

                    // Column 2: Systolic
                    Cell cell2 = row.createCell(2);
                    if (log.getSystolic() != null) {
                        cell2.setCellValue(log.getSystolic());
                    } else {
                        cell2.setCellValue("--");
                    }
                    cell2.setCellStyle(dataStyle);

                    // Column 3: Diastolic
                    Cell cell3 = row.createCell(3);
                    if (log.getDiastolic() != null) {
                        cell3.setCellValue(log.getDiastolic());
                    } else {
                        cell3.setCellValue("--");
                    }
                    cell3.setCellStyle(dataStyle);

                    // Column 4: Sleep Hours
                    Cell cell4 = row.createCell(4);
                    if (log.getSleepHours() != null) {
                        cell4.setCellValue(log.getSleepHours().doubleValue());
                    } else {
                        cell4.setCellValue("--");
                    }
                    cell4.setCellStyle(dataStyle);

                    // Column 5: Water ml
                    Cell cell5 = row.createCell(5);
                    if (log.getWaterMl() != null) {
                        cell5.setCellValue(log.getWaterMl());
                    } else {
                        cell5.setCellValue("--");
                    }
                    cell5.setCellStyle(dataStyle);

                    // Column 6: Sugar Consumption Level
                    Cell cell6 = row.createCell(6);
                    cell6.setCellValue(log.getSugarConsumptionLevel() != null ? log.getSugarConsumptionLevel() : "--");
                    cell6.setCellStyle(noteStyle);

                    // Column 7: Symptoms
                    Cell cell7 = row.createCell(7);
                    cell7.setCellValue(log.getSymptoms() != null ? log.getSymptoms() : "--");
                    cell7.setCellStyle(noteStyle);

                    // Column 8: Note
                    Cell cell8 = row.createCell(8);
                    cell8.setCellValue(log.getNote() != null ? log.getNote() : "");
                    cell8.setCellStyle(noteStyle);
                }
            } else {
                Row emptyRow = sheet.createRow(rowIdx++);
                Cell emptyCell = emptyRow.createCell(0);
                emptyCell.setCellValue("Không có dữ liệu nhật ký sức khỏe trong khoảng thời gian này.");
                emptyCell.setCellStyle(dataStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 8));
            }

            // Adjust column widths automatically
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Lỗi sinh file Excel nhật ký sức khỏe: " + e.getMessage(), e);
        }
    }

    private void setBorder(CellStyle cellStyle) {
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
