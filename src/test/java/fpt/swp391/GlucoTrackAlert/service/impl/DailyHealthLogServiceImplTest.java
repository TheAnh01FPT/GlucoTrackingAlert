package fpt.swp391.GlucoTrackAlert.service.impl;

import fpt.swp391.GlucoTrackAlert.model.DailyHealthLog;
import fpt.swp391.GlucoTrackAlert.repository.DailyHealthLogRepository;
import fpt.swp391.GlucoTrackAlert.repository.patient.PatientRepository;
import fpt.swp391.GlucoTrackAlert.repository.risk.WeeklyHealthReportRepository;
import fpt.swp391.GlucoTrackAlert.service.ComplicationRiskService;
import fpt.swp391.GlucoTrackAlert.service.HealthThresholdService;
import fpt.swp391.GlucoTrackAlert.service.WeeklyReportService;
import fpt.swp391.GlucoTrackAlert.service.cardioai.WeeklyCardioAiService;
import fpt.swp391.GlucoTrackAlert.service.Duy_DangerAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyHealthLogServiceImplTest {

    @Mock
    private DailyHealthLogRepository dailyHealthLogRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private HealthThresholdService healthThresholdService;

    @Mock
    private ComplicationRiskService complicationRiskService;

    @Mock
    private WeeklyReportService weeklyReportService;

    @Mock
    private WeeklyHealthReportRepository weeklyHealthReportRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Duy_DangerAlertService duyDangerAlertService;

    @Mock
    private WeeklyCardioAiService weeklyCardioAiService;

    private DailyHealthLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DailyHealthLogServiceImpl(
                dailyHealthLogRepository,
                patientRepository,
                healthThresholdService,
                complicationRiskService,
                weeklyReportService,
                weeklyHealthReportRepository,
                jdbcTemplate,
                duyDangerAlertService,
                weeklyCardioAiService
        );
    }

    @Test
    void resolveCycleStart_whenAnchorIsFirstLog_andLogDateInSameCycle_returnsAnchorDate() {
        LocalDate anchor = LocalDate.of(2026, 1, 5);
        LocalDate logDate = anchor.plusDays(3);

        DailyHealthLog firstLog = new DailyHealthLog();
        firstLog.setLogDate(anchor);
        when(dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateAsc(1L)).thenReturn(firstLog);

        LocalDate cycleStart = service.resolveCycleStart(1L, logDate);

        assertThat(cycleStart).isEqualTo(anchor);
    }

    @Test
    void resolveCycleStart_whenLogDateFallsInLaterCycle_returnsCorrectCycleStart() {
        LocalDate anchor = LocalDate.of(2026, 1, 5);
        LocalDate logDate = anchor.plusDays(15); // third cycle

        DailyHealthLog firstLog = new DailyHealthLog();
        firstLog.setLogDate(anchor);
        when(dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateAsc(2L)).thenReturn(firstLog);

        LocalDate cycleStart = service.resolveCycleStart(2L, logDate);

        assertThat(cycleStart).isEqualTo(anchor.plusDays(14));
    }

    @Test
    void resolveCycleStart_whenNoAnchorLog_returnsLogDate() {
        LocalDate logDate = LocalDate.of(2026, 3, 10);
        when(dailyHealthLogRepository.findFirstByPatientIdOrderByLogDateAsc(3L)).thenReturn(null);

        LocalDate cycleStart = service.resolveCycleStart(3L, logDate);

        assertThat(cycleStart).isEqualTo(logDate);
    }
}
