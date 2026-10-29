package com.clinic.enums;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum PatientStatus implements Serializable {

    REGISTERED(
            100,
            "Admitted & Registered",
            StatusCategory.INTAKE,
            "#17a2b8",
            "badge-info"
    ),
    NEW_ARRIVAL(
            100,
            "New Arrival / Admitted",
            StatusCategory.INTAKE,
            "#17a2b8",
            "badge-info"
    ),
    WAITING_FOR_DOCTOR(
            200,
            "Awaiting Physician Consultation",
            StatusCategory.CLINICAL,
            "#ffc107",
            "badge-warning"
    ),
    IN_VISIT(
            250,
            "Under Clinical Examination",
            StatusCategory.CLINICAL,
            "#007bff",
            "badge-primary"
    ),
    IN_TREATMENT(
            250,
            "In Clinical Treatment",
            StatusCategory.CLINICAL,
            "#007bff",
            "badge-primary"
    ),
    WAITING_FOR_PAYMENT(
            300,
            "Awaiting Fee Settlement",
            StatusCategory.FINANCIAL,
            "#fd7e14",
            "badge-secondary"
    ),
    PAYMENT_COMPLETED(
            350,
            "Settlement Finalized",
            StatusCategory.FINANCIAL,
            "#20c997",
            "badge-teal"
    ),
    DISCHARGED(
            400,
            "Formally Discharged",
            StatusCategory.TERMINAL,
            "#28a745",
            "badge-success"
    ),
    CLEARED_AND_FINISHED(
            400,
            "Cleared & Concluded",
            StatusCategory.TERMINAL,
            "#28a745",
            "badge-success"
    ),
    CANCELLED(
            500,
            "Encounter Cancelled",
            StatusCategory.TERMINAL,
            "#dc3545",
            "badge-danger"
    );

    public enum StatusCategory {
        INTAKE("Front-Desk Intake & Triage"),
        CLINICAL("Physician & Nursing Care"),
        FINANCIAL("Billing & Cashier Register"),
        TERMINAL("Archived & Concluded");

        private final String displayTitle;

        StatusCategory(String displayTitle) {
            this.displayTitle = displayTitle;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        @Override
        public String toString() {
            return displayTitle;
        }
    }

    private final int statusCode;
    private final String label;
    private final StatusCategory category;
    private final String badgeColorHex;
    private final String cssClass;
    private Set<PatientStatus> allowedTransitions;

    PatientStatus(int statusCode, String label, StatusCategory category, String badgeColorHex, String cssClass) {
        this.statusCode = statusCode;
        this.label = label;
        this.category = category;
        this.badgeColorHex = badgeColorHex;
        this.cssClass = cssClass;
    }

    static {
        // ۱. مرحله پذیرش اولیه
        REGISTERED.allowedTransitions = EnumSet.of(WAITING_FOR_DOCTOR, IN_VISIT, IN_TREATMENT, CANCELLED);
        NEW_ARRIVAL.allowedTransitions = EnumSet.of(WAITING_FOR_DOCTOR, IN_VISIT, IN_TREATMENT, WAITING_FOR_PAYMENT, CANCELLED);

        // ۲. مرحله مراقبت‌های بالینی
        WAITING_FOR_DOCTOR.allowedTransitions = EnumSet.of(IN_VISIT, IN_TREATMENT, WAITING_FOR_PAYMENT, CANCELLED);
        IN_VISIT.allowedTransitions = EnumSet.of(WAITING_FOR_PAYMENT, PAYMENT_COMPLETED, CLEARED_AND_FINISHED, DISCHARGED, CANCELLED);
        IN_TREATMENT.allowedTransitions = EnumSet.of(WAITING_FOR_PAYMENT, PAYMENT_COMPLETED, CLEARED_AND_FINISHED, DISCHARGED, CANCELLED);

        // ۳. مرحله امور مالی، تسویه صندوق و بازگشت وضعیت در صورت استرداد وجه (Refund)
        WAITING_FOR_PAYMENT.allowedTransitions = EnumSet.of(PAYMENT_COMPLETED, WAITING_FOR_DOCTOR, CLEARED_AND_FINISHED, DISCHARGED, CANCELLED);
        PAYMENT_COMPLETED.allowedTransitions = EnumSet.of(WAITING_FOR_PAYMENT, WAITING_FOR_DOCTOR, DISCHARGED, CLEARED_AND_FINISHED);

        // ۴. مرحله پایانی و امکان پذیرش مجدد یا استرداد
        DISCHARGED.allowedTransitions = EnumSet.of(REGISTERED, NEW_ARRIVAL, WAITING_FOR_DOCTOR, WAITING_FOR_PAYMENT);
        CLEARED_AND_FINISHED.allowedTransitions = EnumSet.of(REGISTERED, NEW_ARRIVAL, WAITING_FOR_DOCTOR, WAITING_FOR_PAYMENT);
        CANCELLED.allowedTransitions = EnumSet.of(REGISTERED, NEW_ARRIVAL, WAITING_FOR_DOCTOR);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayTitle() {
        return label;
    }

    public StatusCategory getCategory() {
        return category;
    }

    public String getBadgeColorHex() {
        return badgeColorHex;
    }

    public String getCssClass() {
        return cssClass;
    }

    public boolean isIntake() {
        return this.category == StatusCategory.INTAKE;
    }

    public boolean isClinical() {
        return this.category == StatusCategory.CLINICAL;
    }

    public boolean isFinancial() {
        return this.category == StatusCategory.FINANCIAL;
    }

    public boolean isTerminal() {
        return this.category == StatusCategory.TERMINAL;
    }

    public boolean isEquivalentTo(PatientStatus other) {
        if (other == null) {
            return false;
        }
        return this == other || this.statusCode == other.statusCode;
    }

    public boolean canTransitionTo(PatientStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        if (this == targetStatus || this.isEquivalentTo(targetStatus)) {
            return true;
        }
        if (allowedTransitions == null) {
            return false;
        }
        if (allowedTransitions.contains(targetStatus)) {
            return true;
        }
        for (PatientStatus allowed : allowedTransitions) {
            if (allowed.statusCode == targetStatus.statusCode) {
                return true;
            }
        }
        return false;
    }

    public Set<PatientStatus> getLegalTransitions() {
        return (allowedTransitions != null)
                ? Collections.unmodifiableSet(allowedTransitions)
                : Collections.emptySet();
    }

    public static PatientStatus fromCode(int code) {
        for (PatientStatus status : values()) {
            if (status.statusCode == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown patient status code: " + code);
    }

    public static PatientStatus fromLabel(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Patient status input cannot be null or empty.");
        }
        String normalized = input.trim().toUpperCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("/", "_");
        for (PatientStatus status : values()) {
            if (status.name().equals(normalized)
                    || status.label.equalsIgnoreCase(input.trim())
                    || status.name().replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown patient status descriptor: " + input);
    }

    @Override
    public String toString() {
        return label;
    }
}