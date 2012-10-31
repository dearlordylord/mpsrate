package ru.megaplan.jira.plugins.mpsrate.ao;

import com.atlassian.activeobjects.tx.Transactional;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 3:23 PM
 * To change this template use File | Settings | File Templates.
 */
@Transactional
public interface RateService {

    void addRating(String issueKey, String who, String whom, int rating, String comment);

    Rate[] getAll();

    List<Rate> getRatesForIssue(String issueKey);

    void setWorker(String issueKey, String username);

    String getWorker(String issueKey);

    List<Rate> getRatesForUser(List<String> userNames, Date start, Date end, String... order);
}
