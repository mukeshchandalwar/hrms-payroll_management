package com.adt.payroll.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.adt.payroll.event.OnLeaveCancelEvent;
import com.adt.payroll.model.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.adt.payroll.config.Auth;
import com.adt.payroll.event.OnLeaveAcceptOrRejectEvent;
import com.adt.payroll.repository.LeaveBalanceRepository;
import com.adt.payroll.repository.LeaveRepository;
import com.adt.payroll.repository.LeaveRequestRepo;
import com.adt.payroll.repository.UserRepo;

import freemarker.template.TemplateException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Service
public class LeaveRequestServiceImpl implements LeaveRequestService ,LeaveBalanceService{
	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private LeaveRequestRepo leaveRequestRepo;

	@Autowired
	private LeaveRepository leaveRepository;

	@Autowired
	private JavaMailSender javaMailSender;

	@Autowired
	private UserRepo userRepo;

	@Value("${spring.mail.username}")
	private String sender;


    @Value("${-Dmy.port}")
	private String serverPort;

	@Value("${-Dmy.property}")
	private String ipaddress;

	@Value("${-UI.scheme}")
	private String scheme;

	@Value("${-UI.context}")
	private String context;

	@Autowired
	private Auth auth;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	private LeaveBalanceRepository leaveBalanceRepo;

	@Autowired
	private TableDataExtractor dataExtractor;
	@Autowired
	private CommonEmailService mailService;

	@Autowired
	private MailService mailService1;

	public LeaveRequestServiceImpl() {

	}

	public LeaveRequestServiceImpl(LeaveRequestRepo leaveReqRepo) {
		this.leaveRequestRepo = leaveReqRepo;
	}

	public LeaveRequestServiceImpl(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public String saveLeaveRequest(LeaveRequestModel lr) {

		LOGGER.info("Payroll service: LeaveRequestServiceImpl:  saveLeaveRequest Info level log msg");
		List<LeaveRequestModel> opt = leaveRequestRepo.findByempid(lr.getEmpid());
		int counter = 0;
		for (LeaveRequestModel lrm : opt) {
			List<String> dbld = lrm.getLeavedate();
			List<String> uild = lr.getLeavedate();
			for (String tempLd : uild) {
				if (dbld.contains(tempLd)) {
					counter++;
				}
			}
		}
		if (counter == 0) {
			lr.setStatus("Pending");
			long millisecondsInFiveDays = TimeUnit.DAYS.toMillis(5);
			long currentTime=System.currentTimeMillis();
			lr.setExpiryTime(currentTime+millisecondsInFiveDays);
			leaveRequestRepo.save(lr);

			int id = lr.getEmpid();
			int leaveId = lr.getLeaveid();
			 UriComponentsBuilder urlBuilder = ServletUriComponentsBuilder.newInstance()
						.scheme(scheme)
						.host(ipaddress)
						.port(serverPort)
						.path(context+"/payroll/leave/leave/Accepted/"+ id + "/" + leaveId + "/"+lr.getLeaveType()+"/"+lr.getLeaveReason());


			 UriComponentsBuilder urlBuilder1 = ServletUriComponentsBuilder.newInstance()
						.scheme(scheme)
						.host(ipaddress)
						.port(serverPort)
						.path(context+"/payroll/leave/leave/Rejected/"+ id + "/" + leaveId+"/"+lr.getLeaveType()+"/"+lr.getLeaveReason());

				OnLeaveRequestSaveEvent onLeaveRequestSaveEvent = new OnLeaveRequestSaveEvent(urlBuilder, urlBuilder1, lr);
			applicationEventPublisher.publishEvent(onLeaveRequestSaveEvent);

		} else {
			return "you have selected wrong date OR already requested for selected date";
		}

		return lr.getLeaveid() + " Leave Request is saved & mail Sent Successfully";
	}

	@Override
	public List<LeaveRequestModel> getLeaveDetails() {
		LOGGER.info("Payroll service: LeaveRequestServiceImpl:  getLeaveDetails Info level log msg");
		List<LeaveRequestModel> leavelist = leaveRequestRepo.findAll();
		return leavelist;
	}

	@Override
	public Page<LeaveRequestModel> getLeaveRequestDetailsByEmpId(int page, int size,Integer empid) {
		LOGGER.info("Payroll service: LeaveRequestServiceImpl:  getLeaveRequestDetailsByEmpId Info level log msg");
		long currentTime1=System.currentTimeMillis();
		long millisecondsInFiveDays1 = TimeUnit.DAYS.toMillis(5);
		Pageable pageable = PageRequest.of(page, size);
		Page<LeaveRequestModel> leaveResponse = leaveRequestRepo.findByempid(empid,pageable);
		for(LeaveRequestModel leave : leaveResponse) {
			if(leave.getStatus().equalsIgnoreCase("Pending")) {
				long millisecondsInFiveDays = TimeUnit.DAYS.toMillis(5);
				long currentTime=System.currentTimeMillis();
				if(leave.getExpiryTime()<currentTime) {
					leave.setStatus("Resend");
					leaveRequestRepo.save(leave);
				}
			}

		}
		if (!leaveResponse.isEmpty()) {
			return leaveResponse;
		} else {
			return null;
		}

	}

	@Override
	public String AcceptLeaveRequest(Integer empid, Integer leaveId,String leaveType,String leaveReason)
			throws TemplateException, MessagingException, IOException {
		Optional<LeaveRequestModel> leaveRequest = Optional.of(new LeaveRequestModel());
		LeaveRequestModel leaveR = leaveRequestRepo.search(empid, leaveId);
		Optional<User> user = Optional.ofNullable(userRepo.findById(empid)
				.orElseThrow(() -> new EntityNotFoundException("employee not found :" + empid)));
		String email = user.get().getEmail();
		if (leaveR != null && leaveR.getStatus().equalsIgnoreCase("Pending")) {
			String message = "Accepted";
			List<String> leaveDatelist = new ArrayList<>();
			String sql = "select leavedate from payroll_schema.leave_dates where leave_id=" + leaveId;
			List<Map<String, Object>> leaveData = dataExtractor.extractDataFromTable(sql);
			for (Map<String, Object> leaveMap : leaveData) {
				leaveDatelist.add((String.valueOf(leaveMap.get("leavedate"))));
			}
			leaveRequest.get().setEmpid(empid);
			leaveRequest.get().setLeaveid(leaveId);
			leaveRequest.get().setName(user.get().getFirstName()+" "+user.get().getLastName());
			leaveRequest.get().setLeaveType(leaveType);
			leaveRequest.get().setLeaveReason(leaveReason);
			leaveRequest.get().setLeavedate(leaveDatelist);
			leaveRequest.get().setStatus(message);
			leaveRequest.get().setEmail(email);
			leaveRequest.get().setMessage("Your leave request has been approved. Find blow leave request approved details");
			OnLeaveAcceptOrRejectEvent onLeaveAcceptOrRejectEvent = new OnLeaveAcceptOrRejectEvent(leaveRequest);
			applicationEventPublisher.publishEvent(onLeaveAcceptOrRejectEvent);
			leaveR.setStatus("Accepted");
			leaveR.setUpdatedBy(auth.getEmail());
			leaveRequestRepo.save(leaveR);
			return leaveR.getLeaveid() + " leave Request Accepted";
		} else {
			return empid + "leave request status already updated";
		}

	}

	@Override
	public String RejectLeaveRequest(Integer empid, Integer leaveId,String leaveType,String leaveReason)
			throws TemplateException, MessagingException, IOException {
		Optional<LeaveRequestModel> leaveRequest = Optional.of(new LeaveRequestModel());
		LeaveRequestModel leaveR = leaveRequestRepo.search(empid, leaveId);
		Optional<User> user = Optional.ofNullable(userRepo.findById(empid)
				.orElseThrow(() -> new EntityNotFoundException("employee not found :" + empid)));
		String email = user.get().getEmail();
		if (leaveR != null && leaveR.getStatus().equalsIgnoreCase("Pending")) {
			List<String> leaveDatelist = new ArrayList<>();
			String sql1 = "select leavedate from payroll_schema.leave_dates where leave_id=" + leaveId;
			List<Map<String, Object>> leaveData = dataExtractor.extractDataFromTable(sql1);
			for (Map<String, Object> leaveMap : leaveData) {
				leaveDatelist.add((String.valueOf(leaveMap.get("leavedate"))));
			}
			String message = "Rejected";
			String sql = "delete from payroll_schema.leave_dates where leave_id=" + leaveId;
			dataExtractor.insertDataFromTable(sql);
			leaveRequest.get().setLeaveid(leaveId);
			leaveRequest.get().setEmpid(empid);
			leaveRequest.get().setName(user.get().getFirstName()+" "+user.get().getLastName());
			leaveRequest.get().setLeaveType(leaveType);
			leaveRequest.get().setLeaveReason(leaveReason);
			leaveRequest.get().setLeavedate(leaveDatelist);
			leaveRequest.get().setStatus(message);
			leaveRequest.get().setEmail(email);
			leaveRequest.get().setMessage("Your leave request has been rejected. Find blow leave request rejected details");
			leaveR.setStatus("Rejected");
			leaveR.setUpdatedBy(auth.getEmail());
			leaveRequestRepo.save(leaveR);
			OnLeaveAcceptOrRejectEvent onLeaveAcceptOrRejectEvent = new OnLeaveAcceptOrRejectEvent(leaveRequest);
			applicationEventPublisher.publishEvent(onLeaveAcceptOrRejectEvent);
			return leaveR.getLeaveid() + " leave Request Rejected";
		} else {
			return empid + " leave request status already updated";
		}
	}

	public void sendEmail(String to, String messages) throws IOException, TemplateException, MessagingException {
		Mail mail = new Mail();
		mail.setSubject("Leave Request");
		mail.setFrom(sender);
		mail.setTo(to);
		String emailContent;
		if (messages.equals("Accepted")) {
			emailContent = "<html><body><h1>Leave Request Accepted</h1><p>Your leave request has been accepted.</p></body></html>";
		} else {
			emailContent = "<html><body><h1>Leave Request Rejected</h1><p>Sorry, your leave request has been rejected.</p></body></html>";
		}
		mail.setContent(emailContent);
		mail.getModel().put("LeaveStatus", messages);

		mail.setContent(emailContent);

		MimeMessage message = javaMailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
				StandardCharsets.UTF_8.name());

		helper.setTo(mail.getTo());
		helper.setText(mail.getContent(), true);
		helper.setSubject(mail.getSubject());
		helper.setFrom(mail.getFrom());
		javaMailSender.send(message);

		LOGGER.info("Mail send Successfully");

	}

	@Override
	public List<LeaveRequestModel> getAllEmployeeLeaveDetails() {
		return leaveRequestRepo.findAll();
	}

     public  String reSendLeaveRequest(int leaveId) {
    	 Optional<LeaveRequestModel>  leaveRequest= leaveRequestRepo.findById(leaveId);
		 Optional<User> user = userRepo.findById(leaveRequest.get().getEmpid());
    	if(leaveRequest.isPresent()) {
    		int id=leaveRequest.get().getEmpid();
    		LeaveRequestModel leaveReq=	leaveRequest.get();
    		leaveReq.setStatus("Pending");
    		leaveReq.setName(user.get().getFirstName()+" "+user.get().getLastName());
    		long millisecondsInFiveDays = TimeUnit.DAYS.toMillis(5);
    		long currentTime=System.currentTimeMillis();
    		leaveReq.setExpiryTime(currentTime+millisecondsInFiveDays);
    		 UriComponentsBuilder urlBuilder = ServletUriComponentsBuilder.newInstance()
						.scheme(scheme)
						.host(ipaddress)
						.port(serverPort)
						.path(context+"/payroll/leave/leave/Accepted/"+ id + "/" + leaveId + "/" + leaveRequest.get().getLeavedate().size()+"/"+leaveRequest.get().getLeaveType()+"/"+leaveRequest.get().getLeaveReason());

			 UriComponentsBuilder urlBuilder1 = ServletUriComponentsBuilder.newInstance()
						.scheme(scheme)
						.host(ipaddress)
						.port(serverPort)
						.path(context+"/payroll/leave/leave/Rejected/"+ id + "/" + leaveId+"/"+leaveRequest.get().getLeaveType()+"/"+leaveRequest.get().getLeaveReason());

				OnLeaveRequestSaveEvent onLeaveRequestSaveEvent = new OnLeaveRequestSaveEvent(urlBuilder, urlBuilder1, leaveReq);
			applicationEventPublisher.publishEvent(onLeaveRequestSaveEvent);
			leaveRequestRepo.save(leaveReq);
			return "reSend email successfully";
    	}
    	return "this records  not persent ";

     }
		@Override
		public LeaveRequestModel getLeaveRequestDetailsByEmpIdAndLeaveId(int empId, int leaveId) {
			return (LeaveRequestModel) leaveRequestRepo.findByEmpidAndLeaveid(empId, leaveId)
					.orElseThrow(() -> new IllegalArgumentException("Leave request not found for empId: " + empId + " and leaveId: " + leaveId));
		}

	@Override
	public String cancelApprovedLeaveByLeaveId(Integer leaveId, String cancelReason, Integer empId) {
		LOGGER.info("Payroll service: LeaveRequestServiceImpl: cancelApprovedLeaveByLeaveId Info level log msg");
		// Fetch leave request by leaveId
		LeaveRequestModel lr = leaveRequestRepo.findById(leaveId)
				.orElseThrow(() -> new RuntimeException("Leave Request not found with ID: " + leaveId));
		// Check if the leave status is "Approved" or "Pending"
		if ("Accepted".equals(lr.getStatus()) || "Pending".equals(lr.getStatus())) {
			lr.setCancelReason(cancelReason);
			leaveRequestRepo.save(lr);
			// Build the cancellation URL
			UriComponentsBuilder urlBuilderCancelled = ServletUriComponentsBuilder.newInstance()
					.scheme(scheme)
					.host(ipaddress)
					.port(serverPort)
					.path(context + "/payroll/leave/leave/cancel/" + lr.getEmpid() + "/" + leaveId + "/" + lr.getLeaveType() + "/" + lr.getCancelReason());
			// Publish the cancellation event
			OnLeaveRequestCancelEvent onLeaveRequestCancelEvent = new OnLeaveRequestCancelEvent(urlBuilderCancelled, lr);
			applicationEventPublisher.publishEvent(onLeaveRequestCancelEvent);
			return "Leave  Cancellation request with ID " + leaveId + " has been Submitted Successfully.";
		} else {
			return "Cannot request cancellation with ID " + leaveId + " because its status is not Approved or Pending.";
		}
	}

	@Override
	public String cancelLeaveRequest(Integer empid, Integer leaveId, String leaveType, String cancelReason) {
		// Fetch the leave request model
		LeaveRequestModel leaveR = leaveRequestRepo.search(empid, leaveId);
		User user = userRepo.findById(empid).orElseThrow(() -> new EntityNotFoundException("employee not found :" + empid));
		String email = user.getEmail();
		String name = user.getFirstName() + " " + user.getLastName();
		if (leaveR != null && ("Pending".equalsIgnoreCase(leaveR.getStatus()) ||
				"Accepted".equalsIgnoreCase(leaveR.getStatus())
		)) {
			// Fetch leave dates before deletion
			// SQL query to get leave dates for the given leave_id
			String sql = "SELECT leavedate FROM payroll_schema.leave_dates WHERE leave_id = ?";
			List<Map<String, Object>> leaveData = dataExtractor.extractDataFromTable1(sql, leaveId);
			List<String> leaveDatelist = new ArrayList<>();
			for (Map<String, Object> leaveMap : leaveData) {
				leaveDatelist.add((String) leaveMap.get("leavedate"));
			}
			String sql1 = "DELETE FROM payroll_schema.leave_dates WHERE leave_id = ?";
			dataExtractor.insertDataFromTable1(sql1, leaveId);
			leaveR.setCancelReason(cancelReason);
			leaveR.setEmail(email);
			leaveR.setName(name);
			leaveR.setMessage("Your Leave Cancellation Request has been approved. Find below leave request approved details");
			leaveR.setStatus("Cancelled"); // Update status to 'Cancelled'

			leaveR.setLeavedate(leaveDatelist); // Include leave dates in the model if needed for email
			leaveRequestRepo.save(leaveR);
			OnLeaveCancelEvent onLeaveCancelEvent = new OnLeaveCancelEvent(Optional.of(leaveR));
			applicationEventPublisher.publishEvent(onLeaveCancelEvent);
			return leaveR.getLeaveid() + " leave Request Cancelled";
		} else {
			return empid + " leave request status already updated";
		}
	}

	@Override
	public LeaveBalance saveLeaveBalance(LeaveBalance leaveBalance) {
		return leaveBalanceRepo.save(leaveBalance);
	}

    @Override
	public Optional<LeaveBalance> findByEmpId(Integer empId) {
		return leaveBalanceRepo.findByEmpId(empId);
	}

	@Override
	public List<LeaveBalance> getAllEmployeeLeaves() {
		return leaveBalanceRepo.findAll();
	}

	@Override
	public Optional<LeaveBalance> getLeaveBalanceById(Integer leaveBalanceId) {
		return leaveBalanceRepo.findByLeaveBalanceId(leaveBalanceId);
	}

	@Override
	public Optional<LeaveBalance> updateLeaveBalance(Integer leaveBalanceId, LeaveBalance leaveBalance) {
		return leaveBalanceRepo.findById(leaveBalanceId).map(existingLeaveBalance -> {
			existingLeaveBalance.setLeaveBalance(leaveBalance.getLeaveBalance());
			existingLeaveBalance.setName(leaveBalance.getName());
			existingLeaveBalance.setEmp_id(leaveBalance.getEmp_id());
			existingLeaveBalance.setUpdatedWhen(new Timestamp(System.currentTimeMillis()));
			return leaveBalanceRepo.save(existingLeaveBalance);
		});
	}

	@Override
	public boolean deleteLeaveBalance(Integer leaveBalanceId) {
		if (leaveBalanceRepo.existsById(leaveBalanceId)) {
			leaveBalanceRepo.deleteById(leaveBalanceId);
			return true;
		}
		return false;
	}

}





