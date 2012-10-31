package ru.megaplan.jira.plugins.mpsrate.panel;

import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.plugin.issuetabpanel.*;
import com.atlassian.jira.project.Project;
import com.atlassian.velocity.VelocityManager;
import ru.megaplan.jira.plugins.mpsrate.action.RatesAction;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;

import java.util.Date;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 4:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class IssueRatesPanel extends AbstractIssueTabPanel2 {

    private final RateService rateService;

    IssueRatesPanel(RateService rateService) {
        this.rateService = rateService;
    }

    @Override
    public ShowPanelReply showPanel(ShowPanelRequest showPanelRequest) {
        Project project = showPanelRequest.issue().getProjectObject();
        boolean show = false;
        if ("MPS".equals(project.getKey())) show = true;
        return ShowPanelReply.create(show);
    }

    @Override
    public GetActionsReply getActions(final GetActionsRequest getActionsRequest) {
        return GetActionsReply.create(new RatesAction(descriptor(), rateService, getActionsRequest.issue(), getActionsRequest.remoteUser()));
    }


}
