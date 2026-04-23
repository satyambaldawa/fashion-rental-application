package com.fashionrental.reporting;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
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

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reporting")
public class ReportingController {

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

    @GetMapping("/overdue-rentals")
    @Operation(summary = "All rentals past their return deadline")
    public ResponseEntity<ApiResponse<OverdueRentalsResponse>> getOverdueRentals() {
        return ResponseEntity.ok(ApiResponse.ok(reportingService.getOverdueRentals()));
    }
}
