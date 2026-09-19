package com.fashionrental.reporting;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.reporting.model.response.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportingService reportingService;

    @MockitoBean
    private JwtConfig jwtConfig;

    // ─── GET /api/reports/daily-revenue ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_use_explicit_date_when_provided() throws Exception {
        LocalDate date = LocalDate.of(2026, 4, 18);
        DailyRevenueResponse response = new DailyRevenueResponse(date, 1000, 500, 0, 1500, 0, 0, 0, 1500, 2, 1);
        when(reportingService.getDailyRevenue(date)).thenReturn(response);

        mockMvc.perform(get("/api/reports/daily-revenue").param("date", "2026-04-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-04-18"))
                .andExpect(jsonPath("$.data.rentCollected").value(1000));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_default_to_today_when_no_date_provided() throws Exception {
        LocalDate today = LocalDate.now();
        when(reportingService.getDailyRevenue(today))
                .thenReturn(new DailyRevenueResponse(today, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/reports/daily-revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today.toString()));
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/reports/daily-revenue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_when_executive_requests_reports() throws Exception {
        mockMvc.perform(get("/api/reports/daily-revenue"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /api/reports/outstanding-deposits ───────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_outstanding_deposits() throws Exception {
        when(reportingService.getOutstandingDeposits()).thenReturn(new OutstandingDepositsResponse(500, List.of()));

        mockMvc.perform(get("/api/reports/outstanding-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOutstanding").value(500));
    }

    // ─── GET /api/reports/monthly-revenue ────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_use_explicit_year_and_month_when_provided() throws Exception {
        when(reportingService.getMonthlyRevenue(2026, 3))
                .thenReturn(new MonthlyRevenueResponse(2026, 3, 5000, 2000, 1000, 6000, 0, 0, 0, 6000, List.of()));

        mockMvc.perform(get("/api/reports/monthly-revenue").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(3));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_default_to_current_year_and_month_when_not_provided() throws Exception {
        var now = java.time.YearMonth.now();
        when(reportingService.getMonthlyRevenue(now.getYear(), now.getMonthValue()))
                .thenReturn(new MonthlyRevenueResponse(now.getYear(), now.getMonthValue(), 0, 0, 0, 0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(get("/api/reports/monthly-revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(now.getYear()));
    }

    // ─── GET /api/reports/discounts-given ────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_use_explicit_date_range_when_provided() throws Exception {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 18);
        when(reportingService.getDiscountsGiven(from, to))
                .thenReturn(new DiscountsGivenResponse(from, to, 200, 3, List.of()));

        mockMvc.perform(get("/api/reports/discounts-given")
                        .param("from", "2026-04-01").param("to", "2026-04-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDiscountGiven").value(200));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_default_discounts_range_to_current_month_when_not_provided() throws Exception {
        when(reportingService.getDiscountsGiven(any(), any()))
                .thenReturn(new DiscountsGivenResponse(LocalDate.now(), LocalDate.now(), 0, 0, List.of()));

        mockMvc.perform(get("/api/reports/discounts-given"))
                .andExpect(status().isOk());
    }

    // ─── GET /api/reports/overdue-rentals ────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_overdue_rentals() throws Exception {
        when(reportingService.getOverdueRentals()).thenReturn(new OverdueRentalsResponse(2, List.of()));

        mockMvc.perform(get("/api/reports/overdue-rentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overdueCount").value(2));
    }
}
