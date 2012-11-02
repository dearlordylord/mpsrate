package ru.megaplan.jira.plugins.mpsrate.rest;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.charts.Chart;
import com.atlassian.jira.charts.ChartFactory;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.jql.parser.JqlQueryParser;
import com.atlassian.jira.rest.api.util.ValidationError;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.NotNull;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableDouble;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;
import ru.megaplan.jira.plugins.mpsrate.chart.MpsRateChart;
import ru.megaplan.jira.plugins.mpsrate.customfield.RateCFType;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 30.07.12
 * Time: 13:00
 * To change this template use File | Settings | File Templates.
 */
@Path("/mpsrategadget")
public class MpsRateGadgetResource {

    private final static Logger log = Logger.getLogger(MpsRateGadgetResource.class);

    private final static List<String> defaultReasons = new ArrayList<String>();

    private static final String USERS = "users";
    private static final String DAYSBEFORE = "daysBefore";
    private static final String DATE_START = "dateStart";
    private static final String DATE_END = "dateEnd";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String IS_CUMULATIVE = "isCumulative";
    private static final String SHOW_OLD_RATES = "showOldRates";
    private static final String REASONS = "reasons";

    private final UserManager userManager;
    private final RateService rateService;
    private final TimeZoneManager timeZoneManager;
    private final OptionsManager optionsManager;
    private final IssueManager issueManager;

    private final static DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
    private static final String RATECFNAME = "MPS Rate";


    public MpsRateGadgetResource(UserManager userManager,
                                 RateService rateService, TimeZoneManager timeZoneManager, OptionsManager optionsManager, IssueManager issueManager) {
        this.userManager = userManager;
        this.rateService = rateService;
        this.timeZoneManager = timeZoneManager;
        this.optionsManager = optionsManager;
        this.issueManager = issueManager;
    }


    @GET
    @Path ("/generate")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getOppression(@Context HttpServletRequest request,
                                  @QueryParam(USERS) final String usersInString,
                                  @QueryParam(DAYSBEFORE) final String daysBefore,
                                  @QueryParam(DATE_START) final String dateStart,
                                  @QueryParam(DATE_END) final String dateEnd,
                                  @QueryParam (WIDTH) @DefaultValue ("450") final int width,
                                  @QueryParam (HEIGHT) @DefaultValue ("300") final int height,
                                  @QueryParam (IS_CUMULATIVE) @DefaultValue ("true") final boolean isCumulative,
                                  @QueryParam (SHOW_OLD_RATES) @DefaultValue("true") final boolean showOldRates,
                                  @QueryParam (REASONS) final String reasonIdsString
                                  ) throws SearchException, ParseException, IOException {
        List<String> reasonIds;
        if (!reasonIdsString.isEmpty()) {
            reasonIds = Arrays.asList(reasonIdsString.split("\\|"));
        } else reasonIds = new ArrayList<String>();
        List<String> userNamesList = validateUsers(usersInString);
        Date start;
        Date end;
        boolean useDates = false;
        if (!StringUtils.isEmpty(dateStart) && !StringUtils.isEmpty(dateEnd)) {
            useDates = true;
        }
        if (useDates) {
            start = validateDate(dateStart);
            end = validateDate(dateEnd);
        } else {
            int days = validateDays(daysBefore);
            Calendar cal = Calendar.getInstance();
            end = cal.getTime();
            cal.add(Calendar.DAY_OF_MONTH, -days);
            toStartOfDay(cal);
            start = cal.getTime();
        }

        List<String> reasons = new ArrayList<String>();
        if (reasonIds != null && !reasonIds.isEmpty() && !(reasonIds.get(0).equals("-1"))) {
            reasons.addAll(reasonIds);
        }

        List<RatesResult> ratesResults = new ArrayList<RatesResult>();
        if (userNamesList.size() > 1)
            ratesResults.add(createRatesResult(userNamesList, start, end, width, height, isCumulative, showOldRates, reasons));
        for (String userLogin : userNamesList) {
            RatesResult ratesResult = createRatesResult(Arrays.asList(new String[]{userLogin}), start, end, width, height, isCumulative, showOldRates, reasons);
            if (ratesResult == null) continue;
            ratesResults.add(ratesResult);
        }
        return Response.ok(ratesResults).cacheControl(noCache()).build();
    }

    private RatesResult createRatesResult(List<String> userLogins, Date start, Date end, int width, int height, boolean isCumulative, boolean showOldRates, List<String> reasonIds) {
        Issue issueForConfig = null;
        CustomField rateCustomField = null;
        List<XMLRate> ratesForUser = getRatesForUser(userLogins, start, end);
        if (ratesForUser.size() > 0) {
            for (XMLRate rate : ratesForUser) {
                Issue issue = issueManager.getIssueObject(rate.getIssueKey());
                if (issue == null) continue;
                Collection<CustomField> rateCustomFields = getCustomFieldsByIssueAndType(RateCFType.class, issue);
                if (rateCustomFields.isEmpty()) continue;
                rateCustomField = rateCustomFields.iterator().next();
                issueForConfig = issue;
                break;
            }
        } else {
            return null;
        }
        if (issueForConfig == null) {
            return null;
        }
        Map<Integer, String> typesMapping = getTypesMapping(rateCustomField, issueForConfig, reasonIds);

        Map<Integer, List<XMLRate>> mapByRating;
        long summarySum = 0;
        final Map<String, Long> reasonPriorities = new HashMap<String, Long>();
        if (reasonIds.isEmpty()) {
            mapByRating = getMapByRating(ratesForUser);
            summarySum = ratesForUser.size();
        }
        else {
            List<XMLRate> rs = new ArrayList<XMLRate>();
            for (String rid : reasonIds) {
                Long id = Long.parseLong(rid);
                Option o = optionsManager.findByOptionId(id);
                if (o != null) {
                    reasonPriorities.put(rid, o.getSequence());
                }
            }
            //ratesForUser.clear();
            for (XMLRate r : ratesForUser) {                     // sort of fuckin filter
                if (reasonPriorities.get(r.getComment()) != null) {
                    rs.add(r);
                    //ratesForUser.add(r);
                }

            }
            Collections.sort(rs, new Comparator<XMLRate>() {
                @Override
                public int compare(XMLRate xmlRate, XMLRate xmlRate1) {
                    Long p1 = reasonPriorities.get(xmlRate.getComment());
                    Long p2 = reasonPriorities.get(xmlRate1.getComment());
                    if (p1 == null) p1 = -100500L;
                    if (p2 == null) p2 = -100500L;
                    int r = p1.compareTo(p2);
                    if (r == 0) r = Long.valueOf(xmlRate.getWhen()).compareTo(xmlRate1.getWhen());
                    if (r == 0) r = xmlRate.getIssueKey().compareTo(xmlRate1.getIssueKey());
                    return r;
                }
            });
            mapByRating = getMapByReason(rs, reasonPriorities);
            summarySum = rs.size();
        }
        Map<Integer, ? extends  Number> oldRatesSums = null;
        if (showOldRates) oldRatesSums = getOldRatesSums(userLogins, start, reasonIds, reasonPriorities);
        Chart chart;
        try {
            chart = getMpsRateChart(mapByRating, oldRatesSums, width, height, start, end, typesMapping, isCumulative);
        } catch (IOException e) {
            String error = "some I/O exception in chart generation";
            log.error(error);
            throw new RuntimeException(error);
        }
        MpsRateChartXml xmlChart = new MpsRateChartXml(chart.getLocation(), width, height);
        LinkedHashMap<Integer, List<Map<String, Integer>>> detailsMap = getMapByRatingAndCount(mapByRating);
        RatesDetails ratesDetails = new RatesDetails(detailsMap, (List) chart.getParameters().get("colorList"));
        List<User> users = new ArrayList<User>();
        for (String userLogin : userLogins) {
            User user = userManager.getUser(userLogin);
            if (user != null) {
                users.add(user);
            }
        }
        List<String> userDisplayNames = getUserDisplayNames(users);
        RatesResult ratesResult = new RatesResult(userDisplayNames,
                userLogins,
                new RatesSummary(dateFormat.format(start), dateFormat.format(end), (int) summarySum),
                ratesDetails,
                xmlChart, typesMapping
        );
        return ratesResult;
    }



    private List<String> getUserDisplayNames(List<User> users) {
        List<String> userDisplayNames = new ArrayList<String>();
        for (User user : users) {
            userDisplayNames.add(user.getDisplayName());
        }
        return userDisplayNames;

    }

    private LinkedHashMap<Integer, List<Map<String, Integer>>> getMapByRatingAndCount(Map<Integer, List<XMLRate>> mapByRating) {
        LinkedHashMap<Integer, List<Map<String, Integer>>> result = new LinkedHashMap<Integer, List<Map<String, Integer>>>();
        for (Map.Entry<Integer, List<XMLRate>> e : mapByRating.entrySet()) {
            List<Map<String, Integer>> mapByCount = getMapByCount(e.getValue());
            result.put(e.getKey(), mapByCount);
        }
        return result;
    }

    private List<Map<String, Integer>> getMapByCount(List<XMLRate> arg) {
        List<XMLRate> value = new ArrayList<XMLRate>(arg); //just shallow copy, we dont'change elements only sequence
        Collections.sort(value, new Comparator<XMLRate>() {
            @Override
            public int compare(XMLRate xmlRate, XMLRate xmlRate1) {
                return xmlRate.getIssueKey().compareTo(xmlRate1.getIssueKey());
            }
        });
        Map<String, Integer> preresult = new HashMap<String, Integer>();
        String previousIssueKey = null;
        List<XMLRate> ratesWithSameIssueKey = new ArrayList<XMLRate>();
        LinkedHashMap<String, List<XMLRate>> ratesByIssueKey = new LinkedHashMap<String, java.util.List<XMLRate>>();
        int count = 0;
        for (XMLRate rate : value) {
            if (previousIssueKey == null) previousIssueKey = rate.getIssueKey();
            if (!rate.getIssueKey().equals(previousIssueKey)) {
                preresult.put(previousIssueKey, count);
                count = 0;
                ratesByIssueKey.put(previousIssueKey, new ArrayList<XMLRate>(ratesWithSameIssueKey));
                ratesWithSameIssueKey.clear();
                previousIssueKey = rate.getIssueKey();
            }
            ratesWithSameIssueKey.add(rate);
            count++;
        }
        preresult.put(previousIssueKey, count);
        if (count != 0) {
            ratesByIssueKey.put(previousIssueKey, ratesWithSameIssueKey);
        }
        sortByMostFreshRate(ratesByIssueKey);
        List<Map<String, Integer>> result = new ArrayList<Map<String, Integer>>();
        for (Map.Entry<String, List<XMLRate>> e : ratesByIssueKey.entrySet()) {
            HashMap<String, Integer> rateHolder = new HashMap<String, Integer>();
            Integer rateSum = e.getValue().size();
            rateHolder.put(e.getKey(), rateSum);
            result.add(rateHolder);
        }
        return result;  //To change body of created methods use File | Settings | File Templates.
    }


    private void sortByMostFreshRate(LinkedHashMap<String, List<XMLRate>> ratesByIssueKey) {
        SortedSet<Map.Entry<String,List<XMLRate>>> sortedEntries = new TreeSet<Map.Entry<String,List<XMLRate>>>(
                new Comparator<Map.Entry<String, List<XMLRate>>>() {
                    @Override
                    public int compare(Map.Entry<String, List<XMLRate>> stringListEntry, Map.Entry<String, List<XMLRate>> stringListEntry1) {
                        List<XMLRate> list1 = stringListEntry.getValue();
                        List<XMLRate> list2 = stringListEntry1.getValue();
                        if (list1.isEmpty() && list2.isEmpty()) return 0;
                        if (list1.isEmpty() && !list2.isEmpty()) return -1;
                        if (!list1.isEmpty() && list2.isEmpty()) return 1;
                        Comparator<XMLRate> freshComparator = new Comparator<XMLRate>() {
                            @Override
                            public int compare(XMLRate xmlRate, XMLRate xmlRate1) {
                                long t1 = xmlRate.getWhen();
                                long t2 = xmlRate1.getWhen();
                                return t1>t2?-1:t1<t2?1:0;
                            }
                        };
                        Collections.sort(list1, freshComparator);
                        Collections.sort(list2, freshComparator);
                        long f1 = list1.get(list1.size()-1).getWhen();
                        long f2 = list2.get(list2.size()-1).getWhen();
                        return f1>f2?-1:f1<f2?1:0;
                    }
                }
        );
        sortedEntries.addAll(ratesByIssueKey.entrySet());
        ratesByIssueKey.clear();
        for (Map.Entry<String,List<XMLRate>> e : sortedEntries) {
            ratesByIssueKey.put(e.getKey(), e.getValue());
        }
    }

    private Chart getMpsRateChart(Map<Integer, List<XMLRate>> mapByRating, Map<Integer, ? extends Number> oldRatesSums, int width, int height, Date dateStart, Date dateEnd, Map<Integer, String> typesMapping, boolean isCumulative) throws IOException {
        Chart chart = new MpsRateChart(timeZoneManager).generateChart(mapByRating, oldRatesSums, ChartFactory.PeriodName.daily, width, height, dateStart, dateEnd, typesMapping, isCumulative);
        return chart;
    }



    // input : rates should be sorted by rating, when // but later they isn't nihuya ne sorted so sort it here
    private Map<Integer, List<XMLRate>> getMapByRating(List<XMLRate> ratesForUser) {
        Collections.sort(ratesForUser, new Comparator<XMLRate>() {
            @Override
            public int compare(XMLRate xmlRate, XMLRate xmlRate1) {
                int r = xmlRate.getRate().compareTo(xmlRate1.getRate());
                if (r == 0) r = Long.valueOf(xmlRate.getWhen()).compareTo(xmlRate1.getWhen());
                if (r == 0) r = xmlRate.getIssueKey().compareTo(xmlRate1.getIssueKey());
                return r;
            }
        });
        Map<Integer, List<XMLRate>> result = new LinkedHashMap<Integer, java.util.List<XMLRate>>();
        List<XMLRate> ratesForRating = new ArrayList<XMLRate>();
        Integer currentRating = null;
        for (XMLRate rate : ratesForUser) {
            Integer rating = rate.getRate();
            if (currentRating == null) currentRating = rating;
            if (!currentRating.equals(rating)) {
                result.put(currentRating, Lists.newArrayList(ratesForRating));
                currentRating = rating;
                ratesForRating.clear();
            }
            ratesForRating.add(rate);

        }
        if (!ratesForRating.isEmpty()) {
            result.put(currentRating, ratesForRating);
        }

        return result;  //To change body of created methods use File | Settings | File Templates.
    }

    private Map<Integer, List<XMLRate>> getMapByReason(List<XMLRate> ratesForUser, Map<String, Long> reasonPriorities) {

        Map<Integer, List<XMLRate>> result = new LinkedHashMap<Integer, java.util.List<XMLRate>>();
        List<XMLRate> ratesForRating = new ArrayList<XMLRate>();
        String currentCt = null;
        for (XMLRate rate : ratesForUser) {
            String ct = rate.getComment();
            if (currentCt == null) currentCt = ct;
            if (!currentCt.equals(ct)) {
                result.put(reasonPriorities.get(currentCt).intValue(), Lists.newArrayList(ratesForRating));
                currentCt = ct;
                ratesForRating.clear();
            }
            ratesForRating.add(rate);

        }
        if (!ratesForRating.isEmpty()) {
            result.put(reasonPriorities.get(currentCt).intValue(), ratesForRating);
        }

        return result;  //To change body of created methods use File | Settings | File Templates.
    }

    //returns rates sorted by rating, when
    private List<XMLRate> getRatesForUser(List<String> userNames, Date start, Date end) {
        List<XMLRate> xmlRates = new ArrayList<XMLRate>();
        Collection<Rate> sqlRates = rateService.getRatesForUser(userNames, start, end, "RATING", "WHEN" ,"ISSUE_KEY");
        for (Rate sqlRate : sqlRates) {
            XMLRate rate = new XMLRate(sqlRate);
            xmlRates.add(rate);
        }
        return xmlRates;
    }

    private Map<Integer, ? extends Number> getOldRatesSums(List<String> userLogins, Date start, List<String> reasonIds, Map<String, Long> reasonPriorities) {
        Map<Integer, MutableDouble> result = new HashMap<Integer, MutableDouble>();
        Collection<Rate> sqlRates = rateService.getRatesForUser(userLogins, new Date(0), start);
        for (Rate sqlRate : sqlRates) {
            Integer groupField = 0;
            if (reasonIds.isEmpty()) {
                Integer rating = sqlRate.getRating();
                groupField = rating;
            } else {
                Long reasonPrior = reasonPriorities.get(sqlRate.getComment());
                if (reasonPrior != null) {
                    groupField = reasonPrior.intValue();
                } else {
                    continue;
                }
            }

            MutableDouble currentSum = result.get(groupField);
            if (currentSum == null) {
                currentSum = new MutableDouble();
                result.put(groupField, currentSum);
            }
            currentSum.add(1);
        }
        return result;
    }

    private void toStartOfDay(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
    }

    private int validateDays(String daysBefore) {
        return Integer.parseInt(daysBefore);
    }

    private List<String> validateUsers(String usersInString) {
        return Arrays.asList(usersInString.split("\\|"));
    }

    private Date validateDate(String date) throws ParseException {
        return dateFormat.parse(date);
    }


    @GET
    @Path ("/validate")
    public Response validate(@QueryParam(DAYSBEFORE) @DefaultValue("30") final String daysBefore) {
        final Collection<ValidationError> errors = new ArrayList<ValidationError>();
        if (!errors.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errors).build();
        }
        return Response.ok().cacheControl(noCache()).build();
    }

    public static Map<Integer,String> getTypesMapping(CustomField ratesCustomField, Issue issueForConfig, List<String> reasonIds) {
        IssueManager issueManager = ComponentAccessor.getIssueManager();
        OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
        Map<Integer, String> typesMapping = new LinkedHashMap<Integer, String>();
        Options optionz = optionsManager.getOptions(ratesCustomField.getRelevantConfig(issueForConfig));
        List<Option> optionList = new ArrayList<Option>();
        if (reasonIds.isEmpty()) {
            optionList.addAll(optionz.getRootOptions());
            for (Option o : optionList) {
                int denormalizedOption = RateCFType.denormalizeRating(o.getSequence().intValue(), optionList.size());
                typesMapping.put(denormalizedOption, o.getValue());
            }
        } else {
            for (String sid : reasonIds) {
                Long id = Long.parseLong(sid);
                Option child = optionz.getOptionById(id);
                if (child != null) optionList.add(child);
            }
            for (Option o : optionList) {
                typesMapping.put(o.getSequence().intValue(), o.getValue());
            }
        }

        return typesMapping;
    }

    @NotNull
    public static Collection<CustomField> getCustomFieldsByIssueAndType(@NotNull Class<?> type, @Nullable Issue issue) {
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        Set<CustomField> result = new TreeSet<CustomField>();
        Collection<CustomField> fields = (null == issue) ?
                customFieldManager.getCustomFieldObjects() : customFieldManager.getCustomFieldObjects(issue);

        for (CustomField cf : fields) {
            if (type.isAssignableFrom(cf.getCustomFieldType().getClass())) {
                result.add(cf);
            }
        }
        return result;
    }


    @XmlRootElement(name = "ratesResult")
    @XmlAccessorType(XmlAccessType.FIELD)
    static public class RatesResult {

        @XmlAttribute
        private List<String> usernames;
        @XmlAttribute
        private List<String> userLogins;
        @XmlAttribute
        private RatesSummary ratesSummary;
        @XmlAttribute
        private RatesDetails ratesDetails;
        @XmlAttribute
        private MpsRateChartXml ratesGraph;
        @XmlAttribute
        private Map<Integer, String> rateNames;

        public RatesResult(List<String> usernames, List<String> userLogins, RatesSummary ratesSummary, RatesDetails ratesDetails, MpsRateChartXml ratesGraph, Map<Integer, String> rateNames) {
            this.usernames = usernames;
            this.userLogins = userLogins;
            this.ratesSummary = ratesSummary;
            this.ratesDetails = ratesDetails;
            this.ratesGraph = ratesGraph;
            this.rateNames = rateNames;
        }

        public List<String> getUsernames() {
            return usernames;
        }

        public void setUsernames(List<String> usernames) {
            this.usernames = usernames;
        }

        public List<String> getUserLogins() {
            return userLogins;
        }

        public void setUserLogins(List<String> userLogins) {
            this.userLogins = userLogins;
        }

        public RatesSummary getRatesSummary() {
            return ratesSummary;
        }

        public void setRatesSummary(RatesSummary ratesSummary) {
            this.ratesSummary = ratesSummary;
        }

        public RatesDetails getRatesDetails() {
            return ratesDetails;
        }

        public void setRatesDetails(RatesDetails ratesDetails) {
            this.ratesDetails = ratesDetails;
        }

        public MpsRateChartXml getRatesGraph() {
            return ratesGraph;
        }

        public void setRatesGraph(MpsRateChartXml ratesGraph) {
            this.ratesGraph = ratesGraph;
        }

        public Map<Integer, String> getRateNames() {
            return rateNames;
        }

        public void setRateNames(Map<Integer, String> rateNames) {
            this.rateNames = rateNames;
        }

        @Override
        public String toString() {
            return "RatesResult{" +
                    "usernames='" + usernames + '\'' +
                    ", ratesSummary=" + ratesSummary +
                    ", ratesDetails=" + ratesDetails +
                    ", ratesGraph=" + ratesGraph +
                    '}';
        }
    }

    @XmlRootElement(name = "ratesSummary")
    @XmlAccessorType(XmlAccessType.FIELD)
    static public class RatesSummary {

        private String dateStart;
        private String dateEnd;

        private Integer summaryFound;

        public RatesSummary(String dateStart, String dateEnd, Integer summaryFound) {
            this.dateStart = dateStart;
            this.dateEnd = dateEnd;
            this.summaryFound = summaryFound;
        }

        public String getDateStart() {
            return dateStart;
        }

        public void setDateStart(String dateStart) {
            this.dateStart = dateStart;
        }

        public String getDateEnd() {
            return dateEnd;
        }

        public void setDateEnd(String dateEnd) {
            this.dateEnd = dateEnd;
        }

        public Integer getSummaryFound() {
            return summaryFound;
        }

        public void setSummaryFound(Integer summaryFound) {
            this.summaryFound = summaryFound;
        }

    }

    @XmlRootElement(name = "ratesDetails")
    @XmlAccessorType(XmlAccessType.FIELD)
    static public class RatesDetails {

        private LinkedHashMap<Integer, List<Map<String, Integer>>>  rates;
        private LinkedHashMap<Integer, String> colorMap;

        public RatesDetails(LinkedHashMap<Integer, List<Map<String, Integer>>>  rates, List<String> colorList) {
            this.rates = rates;
            colorMap = new LinkedHashMap<Integer, String>();
            int j = 0;
            for (Integer i : rates.keySet()) {
                colorMap.put(i, colorList.get(j));
                ++j;
            }

        }

        public LinkedHashMap<Integer, List<Map<String, Integer>>> getRates() {
            return rates;
        }

        public void setRates(LinkedHashMap<Integer, List<Map<String, Integer>>> rates) {
            this.rates = rates;
        }

        public LinkedHashMap<Integer, String> getColorMap() {
            return colorMap;
        }

        public void setColorMap(LinkedHashMap<Integer, String> colorMap) {
            this.colorMap = colorMap;
        }

        @Override
        public String toString() {
            return "RatesDetails{" +
                    "rates=" + rates +
                    '}';
        }
    }

    @XmlRootElement(name = "rate")
    @XmlAccessorType(XmlAccessType.FIELD)
    static public class XMLRate {

        private String issueKey;
        private String who;
        private String whom;
        private Integer rate;
        private long when;
        private String comment;

        public XMLRate(Rate sqlRate) {
            this.issueKey = sqlRate.getIssueKey();
            this.who = sqlRate.getWho();
            this.whom = sqlRate.getWhom();
            this.rate = sqlRate.getRating();
            this.when = sqlRate.getWhen().getTime();
            this.comment = sqlRate.getComment();
        }

        public String getIssueKey() {
            return issueKey;
        }

        public void setIssueKey(String issueKey) {
            this.issueKey = issueKey;
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

        public Integer getRate() {
            return rate;
        }

        public void setRate(Integer rate) {
            this.rate = rate;
        }

        public long getWhen() {
            return when;
        }

        public void setWhen(long when) {
            this.when = when;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @Override
        public String toString() {
            return "XMLRate{" +
                    "issueKey='" + issueKey + '\'' +
                    ", who='" + who + '\'' +
                    ", whom='" + whom + '\'' +
                    ", rate=" + rate +
                    ", when=" + when +
                    ", comment='" + comment + '\'' +
                    '}';
        }
    }



    @XmlRootElement
    public static class MpsRateChartXml
    {
        // The URL where the chart image is available from.  The image is once of image that can only be accessed once.
        @XmlElement
        private String location;
        @XmlElement
        private int width;
        @XmlElement
        private int height;

        public MpsRateChartXml(String location, int width, int height) {
            this.location = location;
            this.width = width;
            this.height = height;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        @Override
        public String toString() {
            return "MpsRateChartXml{" +
                    "location='" + location + '\'' +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }
    public static CacheControl noCache() {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        cacheControl.setNoStore(true);
        return cacheControl;
    }

}
