package fpt.swp391.GlucoTrackAlert.util;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import fpt.swp391.GlucoTrackAlert.GlucoTrackAlertApplication;
import fpt.swp391.GlucoTrackAlert.service.healthlog.WeeklyReportService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class WeeklyReportRegenRunner {

    public static void main(String[] args) {
        if (args == null || args.length < 2) {
            System.err.println("Usage: java -jar app.jar <patientId> <weekStart(YYYY-MM-DD)>");
            System.exit(2);
        }

        Long patientId;
        LocalDate weekStart;
        try {
            patientId = Long.parseLong(args[0]);
            weekStart = LocalDate.parse(args[1]);
        } catch (NumberFormatException | DateTimeParseException ex) {
            System.err.println("Invalid arguments: " + ex.getMessage());
            System.exit(3);
            return;
        }

        ApplicationContext ctx = SpringApplication.run(GlucoTrackAlertApplication.class);
        WeeklyReportService weeklyReportService = ctx.getBean(WeeklyReportService.class);

        try {
            System.out.println("Starting weekly report regen for patient=" + patientId + " weekStart=" + weekStart);
            // `recalculateIfExists` in the service returns void in this codebase.
            // Call it and rely on logs/exceptions for outcome.
            weeklyReportService.recalculateIfExists(patientId, weekStart);
            System.out.println("recalculateIfExists completed (check logs for details).");
        } catch (Exception ex) {
            System.err.println("Error during regen: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        } finally {
            SpringApplication.exit(ctx);
        }

        System.out.println("Done.");
    }
}
