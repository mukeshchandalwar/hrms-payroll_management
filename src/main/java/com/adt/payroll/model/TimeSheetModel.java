package com.adt.payroll.model;

import java.sql.Time;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

@Entity
@Table(catalog = "EmployeeDB", schema = "payroll_schema", name = "Time_sheet")
@Data
public class TimeSheetModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "timeSheet_id", columnDefinition = "serial")
	private int timeSheetId;

	@Column(name = "employee_id")
	private int employeeId;

	@Column(name = "checkOut")
	private String checkOut;

	@Column(name = "checkIn")
	private String checkIn;

	@Column(name = "workingHour")
	private String workingHour;

	@Column(name = "date")
	private String date;

	@Column(name = "status")
	private String status;

	@Column(name = "month")
	private String month;

	@Column(name = "year")
	private String year;

	@Column(name = "leaveInterval")
	private String leaveInterval;

	@Column(name = "intervalStatus")
	private Boolean intervalStatus;

	@Column(name = "checkInLatitude")
	private String checkInLatitude;

	@Column(name = "checkInLongitude")
	private String checkInLongitude;

	@Column(name = "checkInDistance")
	private String checkInDistance;

	@Column(name = "checkOutLatitude")
	private String checkOutLatitude;

	@Column(name = "checkOutLongitude")
	private String checkOutLongitude;

	@Column(name = "checkOutDistance")
	private String checkOutDistance;

	@Column(name = "reason")
	private String reason;

	@Column(name = "earlyCheckOutStatus")
	private Boolean earlyCheckOutStatus;

	@Column(name = "reasonType")
	private String reasonType;

	@Column(name = "day")
	private String day;

	@Column(name = "check_in_distance_status")
	private String checkInDistanceStatus;

	@Column(name = "check_out_distance_status")
	private String checkOutDistanceStatus;

	@Column(name = "total_working_hours")
	private Time totalWorkingHours;

	@Transient
	private String employeeName;

	@Transient
	private String dayOfWeek;

	public TimeSheetModel(int timeSheetId, int employeeId, String checkOut, String checkIn, String workingHour,
			String date, String status, String month, String year, String reason, Boolean earlyCheckOutStatus,
			String reasonType, String day, String checkInDistanceStatus, String checkOutDistanceStatus) {

		super();
		this.timeSheetId = timeSheetId;
		this.employeeId = employeeId;
		this.checkOut = checkOut;
		this.checkIn = checkIn;
		this.workingHour = workingHour;
		this.date = date;
		this.status = status;
		this.month = month;
		this.year = year;
		this.reason = reason;
		this.earlyCheckOutStatus = earlyCheckOutStatus;
		this.reasonType = reasonType;
		this.day = day;
		this.checkInDistanceStatus = checkInDistanceStatus;
		this.checkOutDistanceStatus = checkOutDistanceStatus;
	}

	public TimeSheetModel() {
		super();
		// TODO Auto-generated constructor stub
	}

	@Override
	public String toString() {
		return "TimeSheetModel [timeSheetId=" + timeSheetId + ", employeeId=" + employeeId + ", checkOut=" + checkOut
				+ ", checkIn=" + checkIn + ", workingHour=" + workingHour + ", date=" + date + ", status=" + status
				+ ", month=" + month + ", year=" + year + ", reason=" + reason + ", earlyCheckOutStatus="
				+ earlyCheckOutStatus + ", reasonType=" + reasonType + ", day='" + day + ", checkInDistanceStatus="
				+ checkInDistanceStatus + ", checkOutDistanceStatus=" + checkOutDistanceStatus + "]";
	}

	public String getDayOfWeek() {
		if (this.date != null && !this.date.isEmpty()) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
			LocalDate localDate = LocalDate.parse(this.date, formatter);
			return localDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		}
		return null;
	}

	public void setDayOfWeek(String dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

}
