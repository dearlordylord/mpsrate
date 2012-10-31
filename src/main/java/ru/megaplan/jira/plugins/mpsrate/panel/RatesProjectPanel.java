package ru.megaplan.jira.plugins.mpsrate.panel;

import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.plugin.issuetabpanel.ShowPanelReply;
import com.atlassian.jira.plugin.projectpanel.impl.AbstractProjectTabPanel;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.browse.BrowseContext;
import com.atlassian.jira.util.BaseUrlSwapper;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 5:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class RatesProjectPanel extends AbstractProjectTabPanel {

    private final static Logger log = Logger.getLogger(RatesProjectPanel.class);

    private final RateService rateService;
    private final JiraBaseUrls jiraBaseUrls;

    public RatesProjectPanel(RateService rateService, JiraBaseUrls jiraBaseUrls) {
        this.rateService = rateService;
        this.jiraBaseUrls = jiraBaseUrls;
    }

    @Override
    public boolean showPanel(BrowseContext browseContext) {
        boolean show = false;
        if ("MPS".equals(browseContext.getProject().getKey())) show = true;
        return show;
    }

    @Override
    public String getHtml(BrowseContext ctx) {
        Map params = ctx.createParameterMap(); //project and user here
        populateVelocityParams(params);
        return descriptor.getHtml("view", params);
    }

    private void populateVelocityParams(Map params) {
        Rate[] allRates = rateService.getAll();
        Map<String, ? extends Map<String, Long>> ratesForIssues = _getRatesByUsers(allRates);
        params.put("baseUrl", jiraBaseUrls.baseUrl());
        params.put("ratesForIssues", ratesForIssues);
    }

    private Map<String, ? extends Map<String, Long>> _getRatesByUsers(Rate[] allRates) {
        String prevUser = null;
        String prevIssue = null;

        Map<String, Map<String, Long>> result = new HashMap<String, Map<String, Long>>();
        long sum = 0;
        Map<String, Long> issuesBuffer = new HashMap<String, Long>();
        for (Rate rate : allRates) {
            if (prevUser == null) prevUser = rate.getWhom();
            if (prevIssue == null) prevIssue = rate.getIssueKey();
            if (!prevIssue.equals(rate.getIssueKey()) && prevUser.equals(rate.getWhom())) {
                issuesBuffer.put(prevIssue, sum);
                sum = 0;
            }
            if (!prevUser.equals(rate.getWhom())) {
                issuesBuffer.put(prevIssue, sum);
                result.put(prevUser, new HashMap<String, Long>(issuesBuffer));
                issuesBuffer.clear();
                sum = 0;
            }
            sum += rate.getRating();
            prevUser = rate.getWhom();
            prevIssue = rate.getIssueKey();
        }
        issuesBuffer.put(prevIssue, sum);
        result.put(prevUser, issuesBuffer);

        return result;
    }

}
