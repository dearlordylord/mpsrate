package ru.megaplan.jira.plugins.mpsrate.ao.impl;

import com.atlassian.activeobjects.ao.ActiveObjectsTableNameConverter;
import com.atlassian.activeobjects.config.ActiveObjectsConfiguration;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.internal.EntityManagedActiveObjects;
import com.atlassian.activeobjects.tx.Transactional;
import com.atlassian.core.util.map.EasyMap;
import net.java.ao.EntityManager;
import net.java.ao.Query;
import net.java.ao.schema.TableNameConverter;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Worker;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 3:24 PM
 * To change this template use File | Settings | File Templates.
 */

@Transactional
public class RateServiceImpl implements RateService {

    private final static Logger log = Logger.getLogger(RateServiceImpl.class);

    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");

    private final ActiveObjects ao;

    RateServiceImpl(ActiveObjects ao) {

        this.ao = ao;
    }

    @Override
    public List<Rate> getRatesForIssue(String issueKey) {
        Rate[] rates = _getRatesForIssue(issueKey);
        return Arrays.asList(rates);
    }


    @Override
    public String getWorker(String issueKey) {
        Worker[] workers = ao.find(Worker.class, Query.select().where("ISSUE_KEY = ?", issueKey));
        if (workers.length == 0) return null;
        if (workers.length > 1) log.error("found more than one worker for one issue");
        return workers[0].getWorkerUser();
    }

    @Override
    public void setWorker(String issueKey, String username) {
        Worker[] workers = ao.find(Worker.class, Query.select().where("ISSUE_KEY = ?", issueKey));
        if (workers.length > 1) log.error("found more than one worker for one issue");
        if (workers.length == 0) {
            ao.create(Worker.class, EasyMap.build("ISSUE_KEY", issueKey, "WORKER_USER", username));
        } else {
            Worker worker = workers[0];
            String oldUserName = worker.getWorkerUser();
            if (oldUserName.equals(username)) return;
            worker.setWorkerUser(username);
            worker.save();
        }
    }



    @Override
    public Rate[] getAll() {
       // try {
            return ao.find(Rate.class, Query.select().order("WHOM, \"ISSUE_KEY\""));
       // } catch ()
           // this \"ISSUE_KEY\" because fucking bug that causes query like this with normal style :
        //SELECT "ID" FROM public."AO_A15A5A_RATE" ORDER BY "WHOM", ISSUE_KEY
    }

    @Override
    public List<Rate> getRatesForUser(List<String> userNames, Date start, Date end, String... order) {
        List<Rate> result = new ArrayList<Rate>();
        for (String userName : userNames) {
            Query q = Query.select().where("WHOM IN (?) AND WHEN BETWEEN ? AND ?", userName , start, end);
            if (order.length != 0) {
                q = q.order(mkOrderString(order));// oh mein gott **** me harder
            }
            Rate[] rates = ao.find(Rate.class, q);
            Collections.addAll(result, rates);
        }
        return result;
    }

    private String mkOrderString(String[] order) {
        boolean first = true;
        StringBuilder res = new StringBuilder();
        for (String ord : order) {
            if (!first) {
                res.append(",\"");
            }
                res.append(ord);
            if (!first) {
                res.append("\"");
            }
            first = false;
        }
        return res.toString();
    }

    private String getOrderByClause(String[] order) {
        if (order == null || order.length == 0) return "";
        StringBuilder sb = new StringBuilder("ORDER BY ");
        for (String field : order) {
            sb.append("\"").append(StringEscapeUtils.escapeSql(field)).append("\"").append(',');
        }
        sb.deleteCharAt(sb.length()-1);
        return sb.toString();
    }

    private List<String> filterNames(List<String> userName) {
        List<String> result = new ArrayList<String>();
        for (String name : userName) {
            result.add("'" + StringEscapeUtils.escapeSql(name) + "'");
        }
        return result;
    }

    private String buildOrderClause(String[] order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.length; ++i) {
            String orderItem = order[i];
            if (i != 0) sb.append(",\"");
            sb.append(orderItem);
            if (i != 0) sb.append("\"");
        }
        return sb.toString();
    }

    private Rate[] _getRatesForIssue(String issueKey) {
        return ao.find(Rate.class, Query.select().where("ISSUE_KEY = ?", issueKey));
    }

    @Override
    public void addRating(String issueKey, String who, String whom, int rating, String comment) {
        ao.create(Rate.class, EasyMap.build("ISSUE_KEY", issueKey, "WHO", who, "WHOM", whom, "RATING", rating, "COMMENT", comment, "WHEN", new Date()));
    }
}
