package ru.megaplan.jira.plugins.mpsrate.action;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueTabPanelModuleDescriptor;
import com.atlassian.jira.user.util.UserManager;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;
import ru.megaplan.jira.plugins.mpsrate.customfield.RateCFType;
import ru.megaplan.jira.plugins.mpsrate.rest.MpsRateGadgetResource;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 4:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class RatesAction extends AbstractIssueAction {

    private final static Logger log = Logger.getLogger(RatesAction.class);

    private final RateService rateService;
    private final Issue issue;
    private final User user;

    private final DateFormat dateFormat = new SimpleDateFormat();

    public RatesAction(IssueTabPanelModuleDescriptor descriptor, RateService rateService, Issue issue, User user) {
        super(descriptor);
        this.rateService = rateService;
        this.issue = issue;
        this.user = user;
    }

    @Override
    public Date getTimePerformed() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void populateVelocityParams(Map map) {
        UserManager userManager = ComponentAccessor.getUserManager();
        Collection<CustomField> rateCustomFields = MpsRateGadgetResource.getCustomFieldsByIssueAndType(RateCFType.class, issue);
        Map<Integer, String> rateNames = MpsRateGadgetResource.getTypesMapping(rateCustomFields.iterator().next(), issue);
        List<Rate> rates = rateService.getRatesForIssue(issue.getKey());
        List<RateBean> rateBeans = new ArrayList<RateBean>();
        for (Rate rate : rates) {
            String who = rate.getWho();
            User whoUser = userManager.getUser(who);
            if (whoUser != null) {
                who = whoUser.getDisplayName() + " [" + who + "]";
            }
            String whom = rate.getWhom();
            User whomUser = userManager.getUser(whom);
            if (whomUser != null) {
                whom = whomUser.getDisplayName() + " [" + whom + "]";
            }
            rateBeans.add(new RateBean(who, whom, rateNames.get(rate.getRating()), dateFormat.format(rate.getWhen())));
        }
        if (rates.isEmpty()) return;
        double fullRating = getFullRating(rates);
        map.put("fullRating", fullRating);
        map.put("rates", rateBeans);
        double averageRating = fullRating/rates.size();
        map.put("averageRating", averageRating);
    }

    private double getFullRating(List<Rate> rates) {
        double result = 0;
        for (Rate rate : rates) {
            result += rate.getRating();
        }
        return result;
    }

    public static class RateBean {

        private String who;
        private String whom;
        private String rating;
        private String when;

        public RateBean(String who, String whom, String rating, String when) {
            this.who = who;
            this.whom = whom;
            this.rating = rating;
            this.when = when;
        }

        public String getWho() {
            return who;
        }

        public void setWho(String who) {
            this.who = who;
        }

        public String getWhom() {
            return whom;
        }

        public void setWhom(String whom) {
            this.whom = whom;
        }

        public String getRating() {
            return rating;
        }

        public void setRating(String rating) {
            this.rating = rating;
        }

        public String getWhen() {
            return when;
        }

        public void setWhen(String when) {
            this.when = when;
        }
    }

}
