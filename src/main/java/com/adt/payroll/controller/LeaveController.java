
package com.adt.payroll.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.adt.payroll.model.LeaveBalance;
import com.adt.payroll.repository.UserRepo;
import com.adt.payroll.service.EmailService;
import com.adt.payroll.service.LeaveBalanceService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.bind.annotation.*;

import com.adt.payroll.model.LeaveModel;
import com.adt.payroll.model.LeaveRequestModel;
import com.adt.payroll.repository.LeaveRequestRepo;
import com.adt.payroll.service.LeaveRequestService;
import com.adt.payroll.service.LeaveService;

import jakarta.mail.MessagingException;

@RestController
@RequestMapping("/leave")
public class LeaveController {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
@Autowired
    LeaveBalanceService leaveBalanceService;
    @Autowired
    public LeaveService leaveService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;


    @Autowired
    LeaveRequestRepo leaveRequestRepo;

    @Autowired
    private Configuration freemarkerConfig;

    @Value("${app.velocity.templates.location}")
    private String basePackagePath;

    @PreAuthorize("@auth.allow('GET_ALL_EMPLOYEE_LEAVE_BALANCE')")
    @GetMapping("/getAllEmpLeaves")
    public ResponseEntity<List<LeaveModel>> getAllLeaves() throws ParseException {
        LOGGER.info("Payroll service: leave:  getAllLeaves Info level log msg");
        return new ResponseEntity<>(leaveService.getAllEmpLeave(), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('GET_EMPLOYEE_LEAVES_BALANCE_BY_ID',T(java.util.Map).of('currentUser', #empId))")
    @GetMapping("/getById/{empId}")
    public ResponseEntity<LeaveModel> getEmpLeaves(@PathVariable("empId") int empId) {
        LOGGER.info("Payroll service: leave:  getEmpLeaves Info level log msg");
        return new ResponseEntity<>(leaveService.getLeaveById(empId), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('SAVE_LEAVE_REQUEST')")
    @PostMapping("/leaveRequest")
    public ResponseEntity<String> saveLeaveRequest(@RequestBody LeaveRequestModel lr) {
        LOGGER.info("Payroll service: leave:  saveLeaveRequest Info level log msg");
        return new ResponseEntity<>(leaveRequestService.saveLeaveRequest(lr), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('GET_LEAVE_DETAILS')")
    @GetMapping("/getLeaveDetails")
    public ResponseEntity<List<LeaveRequestModel>> getLeaveDetails() {
        LOGGER.info("Payroll service: leave:  getLeaveDetails Info level log msg");
        return new ResponseEntity<>(leaveRequestService.getLeaveDetails(), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('GET_LEAVE_REQUEST_DETAILS_BY_EMPLOYEE_ID',T(java.util.Map).of('currentUser', #empId))")
    @GetMapping("getAllLeaveByEmpId/{empId}")
    public ResponseEntity<Page<LeaveRequestModel>> getLeaveRequestDetailsByEmpId(@PathVariable("empId") int empId,
                                                                                 @RequestParam(value = "page", defaultValue = "0", required = false) int page,
                                                                                 @RequestParam(value = "size", defaultValue = "10", required = false) int size) {
        LOGGER.info("Payroll service: leave:  getLeaveRequestDetailsByEmpId Info level log msg");
        return new ResponseEntity<Page<LeaveRequestModel>>(leaveRequestService.getLeaveRequestDetailsByEmpId(page, size, empId), HttpStatus.OK);
    }


    @PreAuthorize("@auth.allow('ACCEPT_LEAVE_REQUEST')")
    @GetMapping("/leave/Accepted/{empid}/{leaveId}/{leaveType}/{leaveReason}")
    public ResponseEntity<?> AcceptLeaveRequest(@PathVariable("empid") Integer empid,
                                                @PathVariable("leaveId") Integer leaveId, @PathVariable("leaveType") String leaveType, @PathVariable("leaveReason") String leaveRecson) throws TemplateException, MessagingException, IOException {
        Optional<LeaveRequestModel> leaveRequest = leaveRequestRepo.findById(leaveId);
        freemarkerConfig.setClassForTemplateLoading(getClass(), basePackagePath);
        Template template = freemarkerConfig.getTemplate("message.ftl");
        Map<String, Object> model = new HashMap<>();
        String status = "rejected";
        if (leaveRequest.get().getStatus().equalsIgnoreCase("Pending")) {
            LOGGER.info("Payroll service: leave:  AcceptLeaveRequest Info level log msg");
            leaveRequestService.AcceptLeaveRequest(empid, leaveId, leaveType, leaveRecson);
            model.put("Message", " leave request has been successfully approved.!");
            model.put("Email", "");
            return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model), HttpStatus.OK);
        }
        if (leaveRequest.get().getStatus().equalsIgnoreCase("Accepted")) {
            status = "approved";
        }
        model.put("Message", " leave request has been already " + status + " by");
        model.put("Email", leaveRequest.get().getUpdatedBy());
        return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model), HttpStatus.OK);
    }


    @PreAuthorize("@auth.allow('REJECT_LEAVE_REQUEST')")
    @GetMapping("/leave/Rejected/{empid}/{leaveId}/{leaveType}/{leaveReason}")
    public ResponseEntity<String> RejectLeaveRequest(@PathVariable("empid") Integer empid,
                                                     @PathVariable("leaveId") Integer leaveId, @PathVariable("leaveType") String leaveType, @PathVariable("leaveReason") String leaveRecson) throws TemplateException, MessagingException, IOException {
        Optional<LeaveRequestModel> leaveRequest = leaveRequestRepo.findById(leaveId);
        freemarkerConfig.setClassForTemplateLoading(getClass(), basePackagePath);
        Template template = freemarkerConfig.getTemplate("message.ftl");
        Map<String, Object> model = new HashMap<>();
        String status = "rejected";
        if (leaveRequest.get().getStatus().equalsIgnoreCase("Pending")) {
            LOGGER.info("Payroll service: leave:  RejectLeaveRequest Info level log msg");
            leaveRequestService.RejectLeaveRequest(empid, leaveId, leaveType, leaveRecson);
            model.put("Message", " leave request has been successfully rejected!");
            model.put("Email", "");
            return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model),
                    HttpStatus.OK);
        }
        if (leaveRequest.get().getStatus().equalsIgnoreCase("Accepted")) {
            status = "approved";
        }
        model.put("Message", " leave request has been already " + status + " by");
        model.put("Email", leaveRequest.get().getUpdatedBy());
        return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('GET_ALL_EMPLOYEE_LEAVE_DETAILS')")
    @GetMapping("/getAllEmployeeLeaves")
    public ResponseEntity<List<LeaveRequestModel>> getAllEmployeeLeaveDetails() {
        LOGGER.info("Payroll service: leave:  RejectLeaveRequest Info level log msg");
        return new ResponseEntity<>(leaveRequestService.getAllEmployeeLeaveDetails(), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('RESEND_LEAVE_REQUEST')")
    @PutMapping("/reSendLeaveRequest/{leaveId}")
    public ResponseEntity<String> reSendLeaveRequest(@PathVariable("leaveId") int leaveId) {
        LOGGER.info("Payroll service: leave:  RejectLeaveRequest Info level log msg");
        return new ResponseEntity<>(leaveRequestService.reSendLeaveRequest(leaveId), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('GET_ALL_LEAVE_BY_EMP_ID_AND_LEAVE_ID')")
    @GetMapping("getAllLeaveByEmpIdAndLeaveId/{empId}/{leaveId}")
    public ResponseEntity<LeaveRequestModel> getLeaveRequestDetailsByEmpIdAndLeaveId(
            @PathVariable("empId") int empId,
            @PathVariable("leaveId") int leaveId) {
        LOGGER.info("Payroll service: leave: getLeaveRequestDetailsByEmpIdAndLeaveId Info level log msg");
        return new ResponseEntity<>(leaveRequestService.getLeaveRequestDetailsByEmpIdAndLeaveId(empId, leaveId), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('CANCEL_APPROVED_LEAVE_BY_LEAVE_ID')")
    @PostMapping("/cancelApprovedLeaveByLeaveId/{leaveId}/{empId}")
    public ResponseEntity<String> cancelApprovedLeaveByLeaveId(@PathVariable Integer leaveId, @PathVariable Integer empId,
                                                               @RequestParam String cancelReason) {
        LOGGER.info("Payroll service: leave: cancelApprovedLeaveByLeaveId Info level log msg");
        return new ResponseEntity<>(leaveRequestService.cancelApprovedLeaveByLeaveId(leaveId, cancelReason, empId), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('CHANGE_LEAVE_REQ_STATUS_TO_CANCELLED')")
    @GetMapping("/leave/cancel/{empid}/{leaveId}/{leaveType}/{cancelReason}")
    public ResponseEntity<?> CancelLeaveRequest(@PathVariable("empid") Integer empid,
                                                @PathVariable("leaveId") Integer leaveId,
                                                @PathVariable("leaveType") String leaveType,
                                                @PathVariable("cancelReason") String cancelReason) throws TemplateException, MessagingException, IOException {

        Optional<LeaveRequestModel> leaveRequestOpt = leaveRequestRepo.findById(leaveId);
        if (!leaveRequestOpt.isPresent()) {
            return new ResponseEntity<>("Leave request not found", HttpStatus.NOT_FOUND);
        }
        LeaveRequestModel leaveRequest = leaveRequestOpt.get();
        freemarkerConfig.setClassForTemplateLoading(getClass(), basePackagePath);
        Template template = freemarkerConfig.getTemplate("message1.ftl");
        Map<String, Object> model = new HashMap<>();
        String status = "Cancelled";
        if (leaveRequest.getStatus().equalsIgnoreCase("Pending") || leaveRequest.getStatus().equalsIgnoreCase("Accepted")||leaveRequest.getStatus().equalsIgnoreCase("Approved")) {
            LOGGER.info("Payroll service: leave: CancelLeaveRequest Info level log msg");
            leaveRequestService.cancelLeaveRequest(empid, leaveId, leaveType, cancelReason);
            model.put("Message", "Leave Request has been Successfully Cancelled.");
            model.put("Email", "");
            return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model), HttpStatus.OK);
        } else if (leaveRequest.getStatus().equalsIgnoreCase("Accepted")) {
            status = "approved";
        }
        model.put("Message", "Leave request has already been " + status + " by");
        model.put("Email", leaveRequest.getUpdatedBy());
        return new ResponseEntity<>(FreeMarkerTemplateUtils.processTemplateIntoString(template, model), HttpStatus.OK);
    }

    @PreAuthorize("@auth.allow('SAVE_LEAVE_BALANCE')")
    @PostMapping("/saveLeaveBalance")
    public ResponseEntity<LeaveBalance> saveLeaveBalance(@RequestBody LeaveBalance leaveBalance) {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        leaveBalance.setUpdatedWhen(currentTimestamp);
        Optional<LeaveBalance> existingLeaveBalanceOpt = leaveBalanceService.findByEmpId(leaveBalance.getEmp_id());
        if (existingLeaveBalanceOpt.isPresent()) {
            LeaveBalance existingLeaveBalance = existingLeaveBalanceOpt.get();
            existingLeaveBalance.setLeaveBalance(leaveBalance.getLeaveBalance());
            existingLeaveBalance.setName(leaveBalance.getName());
            existingLeaveBalance.setUpdatedWhen(currentTimestamp);
            LeaveBalance updatedLeaveBalance = leaveBalanceService.saveLeaveBalance(existingLeaveBalance);
            return ResponseEntity.ok(updatedLeaveBalance);
        } else {
            LeaveBalance savedLeaveBalance = leaveBalanceService.saveLeaveBalance(leaveBalance);
            return ResponseEntity.status(201).body(savedLeaveBalance);
        }
    }

     @PreAuthorize("@auth.allow('GET_ALL_EMPLOYEE_LEAVE_BALANCE')")
    @GetMapping("/getAllEmployeeLeave")
    public ResponseEntity<List<LeaveBalance>> getAllEmployeeLeaves() {
        List<LeaveBalance> leaveBalances = leaveBalanceService.getAllEmployeeLeaves();
        return ResponseEntity.ok(leaveBalances);
    }
    @PreAuthorize("@auth.allow('GET_LEAVE_BALANCE_BY_ID')")
    @GetMapping("/getLeaveBalanceById/{leaveBalanceId}")
    public ResponseEntity<LeaveBalance> getLeaveBalanceById(@PathVariable Integer leaveBalanceId) {
        Optional<LeaveBalance> leaveBalance = leaveBalanceService.getLeaveBalanceById(leaveBalanceId);
        return leaveBalance.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PreAuthorize("@auth.allow('UPDATE_LEAVE_BALANCE_BY_BALANCE_ID')")
    @PutMapping("/updateLeaveBalance/{leaveBalanceId}")
    public ResponseEntity<LeaveBalance> updateLeaveBalance(@PathVariable Integer leaveBalanceId, @RequestBody LeaveBalance leaveBalance) {
        Optional<LeaveBalance> updatedLeaveBalance = leaveBalanceService.updateLeaveBalance(leaveBalanceId, leaveBalance);
        return updatedLeaveBalance.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @PreAuthorize("@auth.allow('DELETE_LEAVE_BALANCE_BY_BALANCE_ID')")
    @DeleteMapping("/deleteLeaveBalance/{leaveBalanceId}")
    public ResponseEntity<Void> deleteLeaveBalance(@PathVariable Integer leaveBalanceId) {
        boolean isDeleted = leaveBalanceService.deleteLeaveBalance(leaveBalanceId);
        return isDeleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
