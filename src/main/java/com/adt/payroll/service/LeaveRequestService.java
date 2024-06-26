package com.adt.payroll.service;

import java.io.IOException;
import java.util.List;

import com.adt.payroll.model.LeaveRequestModel;
import freemarker.template.TemplateException;

import jakarta.mail.MessagingException;


public interface LeaveRequestService {

	public String saveLeaveRequest(LeaveRequestModel lr);

	public List<LeaveRequestModel> getLeaveDetails();

	public List<LeaveRequestModel> getLeaveRequestDetailsByEmpId(Integer empid);

	public String AcceptLeaveRequest(Integer empid, Integer leaveId,Integer leaveDate ,String leavetype, String leaveReason) throws TemplateException, MessagingException, IOException;
	
	public String RejectLeaveRequest(Integer empid, Integer leaveId,String leavetype, String leaveReason) throws TemplateException, MessagingException, IOException;

	public List<LeaveRequestModel> getAllEmployeeLeaveDetails();
     

}