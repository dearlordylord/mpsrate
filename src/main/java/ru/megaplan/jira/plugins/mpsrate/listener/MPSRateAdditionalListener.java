package ru.megaplan.jira.plugins.mpsrate.listener;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.util.collect.Function;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.megaplan.jira.plugins.history.search.HistorySearchManager;
import ru.megaplan.jira.plugins.workflow.mps.utils.service.WorkflowSettingsService;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 16.08.12
 * Time: 18:03
 * To change this template use File | Settings | File Templates.
 */
public class MPSRateAdditionalListener implements InitializingBean, DisposableBean {

    private final static Logger log = Logger.getLogger(MPSRateAdditionalListener.class);

    private final EventPublisher eventPublisher;
    private final CustomFieldManager customFieldManager;
    private final HistorySearchManager historySearchManager;
    private final UserManager userManager;
    private final IssueService issueService;
    private final GroupManager groupManager;
    private final long DONE_STATUS_ID = 10009L; //"Событие в статусе "сделано""
    private final String EVALUATED_USER_CUSTOMFIELD_NAME = "Оцениваемый";
    private final String STATUS = "Status";
    private final String ASSIGNEE = "Assignee";
    private final WorkflowSettingsService workflowSettingsService;
    private CustomField evaluatedUserCf;
    private final String MPSFIRSTLINEGROUP = "mps-support-1st";
    private final String DANI = "dani";

    private static final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();

    private final Function<HistorySearchManager.ChangeLogRequest, String> findChangeLogFunction;


    MPSRateAdditionalListener(EventPublisher eventPublisher, CustomFieldManager customFieldManager, HistorySearchManager historySearchManager, UserManager userManager, IssueService issueService, GroupManager groupManager, WorkflowSettingsService workflowSettingsService) throws Exception {
        this.eventPublisher = eventPublisher;
        this.customFieldManager = customFieldManager;
        this.historySearchManager = historySearchManager;
        this.userManager = userManager;
        this.issueService = issueService;
        this.groupManager = groupManager;
        this.workflowSettingsService = workflowSettingsService;
        evaluatedUserCf = customFieldManager.getCustomFieldObjectByName(EVALUATED_USER_CUSTOMFIELD_NAME);
        findChangeLogFunction = this.historySearchManager.getFindInChangeLogFunction();
        if (findChangeLogFunction==null) {
            String error = "cant' get findChangeLogFunction";
            log.error("error");
            throw new Exception(error);
        }
    }

    @EventListener
    public void issueEvent(IssueEvent issueEvent) {
        if (issueEvent.getEventTypeId() != DONE_STATUS_ID) return;
        String userResponseAwaitStatus = workflowSettingsService.getUserResponseAwaitStatus();
        boolean comeFromWaitingStatus = false;
        if (userResponseAwaitStatus == null || userResponseAwaitStatus.isEmpty()) {
            log.error("can't find user response await status so skip this check");
        } else {
            String statusFrom = getStatusFrom(issueEvent.getChangeLog());
            if (userResponseAwaitStatus.equals(statusFrom)) comeFromWaitingStatus = true;
        }
        if (evaluatedUserCf == null) {
            evaluatedUserCf = customFieldManager.getCustomFieldObjectByName(EVALUATED_USER_CUSTOMFIELD_NAME);
            if (evaluatedUserCf == null) {
                log.error("can't find customfield with name : " + EVALUATED_USER_CUSTOMFIELD_NAME);
                return;
            }
        }
        Issue issue = issueEvent.getIssue();
        final User initiator = issueEvent.getUser();
        User evaluatedUser;
        String lastAssignee = getLastAssignee(issueEvent.getChangeLog());
        User assignee = issue.getAssignee();
        if (lastAssignee == null) {
            if (assignee != null) {
                lastAssignee = assignee.getName();
            }
        }
        if (comeFromWaitingStatus) {
            log.warn("last assignee in history : " + lastAssignee);
            if (lastAssignee != null) {
                assignee = userManager.getUser(lastAssignee);
            }
            if (assignee == null) {
                log.error("cant'find assignee for issue : " + issue.getKey());
                return;
            }
            evaluatedUser = assignee;
            log.warn("setting assignee as evaluated user for issue : " + issue.getKey());
        } else {
            evaluatedUser = initiator;
        }

        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        issueInputParameters.setSkipScreenCheck(true);
        if (isLastAssigneeInFirstLine(lastAssignee)) {
            log.warn("last assignee is in first line");
            issueInputParameters.setAssigneeId(DANI);
        }
        issueInputParameters.addCustomFieldValue(evaluatedUserCf.getIdAsLong(), evaluatedUser.getName());
        final IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(initiator, issue.getId(), issueInputParameters);
        if (updateValidationResult.getErrorCollection().hasAnyErrors()) {
            log.error("can't update field " + EVALUATED_USER_CUSTOMFIELD_NAME + " by user : " + initiator.getName() + " for issue : " + issue.getKey());
            log.error(updateValidationResult.getErrorCollection().getErrorMessages());
            log.error(updateValidationResult.getErrorCollection().getErrors());
            return;
        }
        Runnable runnableUpdate = new Runnable() {
            @Override
            public void run() {
                issueService.update(initiator, updateValidationResult);
            }
        };
        worker.schedule(runnableUpdate, 2, TimeUnit.SECONDS);
    }

    private boolean isLastAssigneeInFirstLine(String user) {
        return groupManager.isUserInGroup(user, MPSFIRSTLINEGROUP);
    }

    private String getLastAssignee(GenericValue changeLog) {
        boolean searchKey = true;
        return findChangeLogFunction.get(new HistorySearchManager.ChangeLogRequest(changeLog, ASSIGNEE, HistorySearchManager.ChangeLogRequest.ISOLDDEFAULT, searchKey));
    }

    private String getStatusFrom(GenericValue changeLog) {
        return findChangeLogFunction.get(new HistorySearchManager.ChangeLogRequest(changeLog, STATUS));
    }


    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }
}
