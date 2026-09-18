package com.fashionrental.reporting;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
import com.fashionrental.reporting.model.response.DiscountsGivenResponse;
import com.fashionrental.reporting.model.response.MonthlyRevenueResponse;
import com.fashionrental.reporting.model.response.OutstandingDepositsResponse;
import com.fashionrental.reporting.model.response.OverdueRentalsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reporting")
public class ReportingController {

    // Mirrors ReportingService's IST constant. LocalDate.now() with no zone reads the JVM's
    // default zone, which is wrong for "today" in a shop that operates on IST regardless of
    // where the server happens to run.
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/daily-revenue")
    @Operation(summary = "Daily revenue summary for a given date")
    public ResponseEntity<ApiResponse<DailyRevenueResponse>> getDailyRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getDailyRevenue(effectiveDate)));
    }

    @GetMapping("/outstanding-deposits")
    @Operation(summary = "All active rentals with outstanding deposit")
    public ResponseEntity<ApiResponse<OutstandingDepositsResponse>> getOutstandingDeposits() {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getOutstandingDeposits()));
    }

    @GetMapping("/monthly-revenue")
    @Operation(summary = "Monthly revenue with daily breakdown for charting")
    public ResponseEntity<ApiResponse<MonthlyRevenueResponse>> getMonthlyRevenue(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        java.time.YearMonth ym = (year != null && month != null)
                ? java.time.YearMonth.of(year, month)
                : java.time.YearMonth.now();
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getMonthlyRevenue(ym.getYear(), ym.getMonthValue())));
    }

    @GetMapping("/discounts-given")
    @Operation(summary = "Coupon discounts given within a date range")
    public ResponseEntity<ApiResponse<DiscountsGivenResponse>> getDiscountsGiven(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(IST);
        LocalDate effectiveFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate effectiveTo = to != null ? to : today;
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getDiscountsGiven(effectiveFrom, effectiveTo)));
    }

    @GetMapping("/overdue-rentals")
    @Operation(summary = "All rentals past their return deadline")
    public ResponseEntity<ApiResponse<OverdueRentalsResponse>> getOverdueRentals() {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getOverdueRentals()));
    }
}
