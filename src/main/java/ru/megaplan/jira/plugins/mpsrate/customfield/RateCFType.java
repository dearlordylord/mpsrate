package ru.megaplan.jira.plugins.mpsrate.customfield;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.imports.project.customfield.ProjectCustomFieldImporter;
import com.atlassian.jira.imports.project.customfield.ProjectImportableCustomField;
import com.atlassian.jira.imports.project.customfield.SelectCustomFieldImporter;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.context.IssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.GroupSelectorField;
import com.atlassian.jira.issue.customfields.MultipleSettableCustomFieldType;
import com.atlassian.jira.issue.customfields.SortableCustomField;
import com.atlassian.jira.issue.customfields.config.item.SettableOptionsConfigItem;
import com.atlassian.jira.issue.customfields.impl.AbstractCustomFieldType;
import com.atlassian.jira.issue.customfields.impl.AbstractSingleFieldType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.OptionUtils;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.customfields.persistence.PersistenceFieldType;
import com.atlassian.jira.issue.customfields.statistics.SelectStatisticsMapper;
import com.atlassian.jira.issue.customfields.view.CustomFieldParams;
import com.atlassian.jira.issue.customfields.view.CustomFieldParamsImpl;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigItemType;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.rest.FieldJsonRepresentation;
import com.atlassian.jira.issue.fields.rest.FieldTypeInfo;
import com.atlassian.jira.issue.fields.rest.FieldTypeInfoContext;
import com.atlassian.jira.issue.fields.rest.RestAwareCustomFieldType;
import com.atlassian.jira.issue.fields.rest.RestCustomFieldTypeOperations;
import com.atlassian.jira.issue.fields.rest.RestFieldOperationsHandler;
import com.atlassian.jira.issue.fields.rest.json.JsonData;
import com.atlassian.jira.issue.fields.rest.json.JsonType;
import com.atlassian.jira.issue.fields.rest.json.JsonTypeBuilder;
import com.atlassian.jira.issue.fields.rest.json.beans.CustomFieldOptionJsonBean;
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.ErrorCollection.Reason;
import com.atlassian.jira.util.NotNull;
import com.atlassian.jira.util.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import webwork.action.ServletActionContext;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Select Custom Field Type allows selecting of a single {@link Option}.
 * <em>Transport Object</em> is {@link Option}
 *
 * dl>
 * <dt><strong>Transport Object Type</strong></dt>
 * <dd>{@link Option}</dd>
 * <dt><Strong>Database Storage Type</Strong></dt>
 * <dd>{@link String} of Option ID</dd>
 * </dl>
 */
public class RateCFType extends AbstractCustomFieldType<Map<String,Option>,Option>
        implements MultipleSettableCustomFieldType<Map<String,Option>,Option>, SortableCustomField<Map<String,Option>>
{


    public static class RateOption {

        final Option rate;
        final Option reason;

        RateOption(Option rate, Option reason) {
            this.rate = rate;
            this.reason = reason;
        }

        public Option getRate() {
            return rate;
        }

        public Option getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return "RateOption{" +
                    "rate=" + rate +
                    ", reason=" + reason +
                    '}';
        }
    }

    private final OptionsManager optionsManager;
    private final ProjectCustomFieldImporter projectCustomFieldImporter;
    private final JiraBaseUrls jiraBaseUrls;
    private final RateService rateService;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final CustomFieldManager customFieldManager;
    private final UserManager userManager;
    private final GroupManager groupManager;
    private final GenericConfigManager genericConfigManager;

    private CustomField mpsLastWorkerCf;

    public static final String PARENT_KEY = null;
    public static final String CHILD_KEY = "1";
    public static final PersistenceFieldType CASCADE_VALUE_TYPE = PersistenceFieldType.TYPE_LIMITED_TEXT;

    private static final Logger log = Logger.getLogger(RateCFType.class);

    public RateCFType(CustomFieldValuePersister customFieldValuePersister, OptionsManager optionsManager, GenericConfigManager genericConfigManager, JiraBaseUrls jiraBaseUrls, RateService rateService, JiraAuthenticationContext jiraAuthenticationContext, CustomFieldManager customFieldManager, UserManager userManager, GroupManager groupManager, GenericConfigManager genericConfigManager1)
    {
        this.optionsManager = optionsManager;
        this.genericConfigManager = genericConfigManager;
        this.jiraBaseUrls = jiraBaseUrls;
        this.rateService = rateService;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.customFieldManager = customFieldManager;
        this.userManager = userManager;
        this.groupManager = groupManager;
        projectCustomFieldImporter = new SelectCustomFieldImporter();
       // mpsLastWorkerCf = customFieldManager.getCustomFieldObjectByName(MPSLASTWORKERCFNAME);
    }

    public void removeValue(CustomField field, Issue issue, Option option)
    {
        //TODO remove value
    }

    public Set<Long> remove(final CustomField field)
    {
        optionsManager.removeCustomFieldOptions(field);
        return new HashSet<Long>();
    }

    public Options getOptions(final FieldConfig fieldConfig, final JiraContextNode jiraContextNode)
    {
        return optionsManager.getOptions(fieldConfig);
    }

    /**
     * Returns a list of Issue Ids matching the "value" note that the value in this instance is the single object
     */
    public Set<Long> getIssueIdsWithValue(CustomField field, Option option)
    {
        //TODO do it
        return new HashSet<Long>();
    }
    // -----------------------------------------------------------------------------------------------------  Validation

    public void validateFromParams(CustomFieldParams relevantParams, ErrorCollection errorCollectionToAddTo, FieldConfig config)
    {
        if (relevantParams == null || relevantParams.isEmpty())
        {
            return;
        }

        String customFieldId = config.getCustomField().getId();

        Option parentOption;
        try
        {
            // Get the parent option
            parentOption = extractOptionFromParams(PARENT_KEY, relevantParams);
        }
        catch (FieldValidationException e)
        {
            parentOption = null;
        }

        // If the selected parent option does not resolve to a value in the DB we should throw an error
        if(parentOption == null)
        {
            List params = new ArrayList(relevantParams.getValuesForKey(null));
            // If there was no value selected for the parent or the 'None/All' option was selected we let them pass
            // and in this case we do not care about what the child values are since the parent is none.
            if (!params.isEmpty() && !isNoneOptionSelected(params))
            {
                errorCollectionToAddTo.addError(customFieldId, getI18nBean().getText("admin.errors.option.invalid.parent", "'" + params.get(0).toString() + "'"), Reason.VALIDATION_FAILED);
            }
        }
        else
        {
            // Since we are sure that the parent value is non-null and resovles to a valid option lets make sure that
            // it is valid in the FieldConfig for where we are.
            if(!parentOptionValidForConfig(config, parentOption))
            {
                errorCollectionToAddTo.addError(customFieldId, getI18nBean().getText("admin.errors.option.invalid.for.context",
                        "'" + parentOption.getValue() + "'", "'" + config.getName() + "'"), Reason.VALIDATION_FAILED);
            }
            else
            {
                try
                {
                    // Get the param for this current option
                    Collection valuesForChild = relevantParams.getValuesForKey(CHILD_KEY);
                    if (valuesForChild != null)
                    {
                        List<String> params = new ArrayList<String>(valuesForChild);

                        // Get the option object from the params only if they have not selected the "None/All" option
                        Option currentOption = null;

                        // If the user has not selected 'None/All' then we should try to resolve the option into an
                        // object and then check that the object is valid in the FieldConfig for where we are.
                        if(!isNoneOptionSelected(params))
                        {
                            // get the option from the params
                            currentOption = extractOptionFromParams(CHILD_KEY, relevantParams);

                            // check that the supplied option is valid in the config supplied
                            if(!currentOptionValidForConfig(config, currentOption))
                            {
                                String optionValue = (currentOption == null) ?  params.get(0).toString() : currentOption.getValue();
                                errorCollectionToAddTo.addError(customFieldId, getI18nBean().getText("admin.errors.option.invalid.for.context",
                                        "'" + optionValue + "'", "'" + config.getName() + "'"), Reason.VALIDATION_FAILED);
                                return;
                            }
                        }

                        // make certain that the current option (if it exists) has a parent, that the parent is what we
                        // expect it to be (the parent that was submitted as a param)
                        if (currentOption != null && currentOption.getParentOption() != null
                                && !parentOption.equals(currentOption.getParentOption()) )
                        {
                            errorCollectionToAddTo.addError(customFieldId, getI18nBean().getText("admin.errors.option.invalid.for.parent","'" + currentOption.getValue() + "'", "'" + parentOption.getValue() + "'"), Reason.VALIDATION_FAILED);
                        }
                    } else {
                        if (parentOption.getSequence() == 0) {
                            errorCollectionToAddTo.addError(customFieldId, "Give me a reason", Reason.VALIDATION_FAILED); //TODO check for child options non-existence
                        }
                    }
                }
                catch (FieldValidationException e)
                {
                    errorCollectionToAddTo.addError(customFieldId, e.getMessage(), Reason.VALIDATION_FAILED);
                }
            }
        }
    }

    private boolean isNoneOptionSelected(List<String> params)
    {
        String parentOptionParam = params.iterator().next();
        return "-1".equals(parentOptionParam);
    }

    private boolean parentOptionValidForConfig(FieldConfig config, Option parentOption)
    {
        final Options options = optionsManager.getOptions(config);
        if(options != null)
        {
            Collection rootOptions = options.getRootOptions();
            if(rootOptions != null)
            {
                return rootOptions.contains(parentOption);
            }
        }
        return false;
    }

    private boolean currentOptionValidForConfig(FieldConfig config, Option currentOption)
    {
        final Options options = optionsManager.getOptions(config);
        if(options != null)
        {
            Collection rootOptions = options.getRootOptions();
            if(rootOptions != null)
            {
                if (currentOption != null)
                {
                    return options.getOptionById(currentOption.getOptionId()) != null;
                }
            }
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------- Persistance Methods

    //these methods all operate on the object level

    /**
     * Create a cascading select-list instance for an issue.
     *
     * @param cascadingOptions
     */
    public void createValue(CustomField field, Issue issue, Map<String, Option> cascadingOptions)
    {
        updateValue(field, issue, cascadingOptions);
    }

    public static int denormalizeRating(int rating, int optionsSize) {
        return rating - optionsSize/2;
    }

    public static int normalizeRating(int rating, int optionsSize) {
        return rating + optionsSize/2;
    }

    @Override
    public void updateValue(com.atlassian.jira.issue.fields.CustomField customField, com.atlassian.jira.issue.Issue issue, Map<String, Option> cascadingOptions) {
        Option parent = cascadingOptions.get(PARENT_KEY);
        Option child = cascadingOptions.get(CHILD_KEY);
        if (parent == null) return;
        Long rating = parent.getSequence();
        Options options = optionsManager.getOptions(customField.getRelevantConfig(issue));
        int resultRating = denormalizeRating(rating.intValue(), options.size());
        //if (resultRating >= 0) resultRating++;    // it is for exclude 0
        User worker = getWorkerFromIssue(issue);
        if (worker == null) return;
        String reasonId = null;
        if (child != null) {
            reasonId = child.getOptionId().toString();
        }
        rateService.addRating(issue.getKey(), jiraAuthenticationContext.getLoggedInUser().getName(), worker.getName(), resultRating, reasonId);
    }

    // --------------------------------------------------------------------------------------  CustomFieldParams methods

    public Map<String, Option> getValueFromIssue(CustomField field, Issue issue) {
        return null;
    }

    public Map<String, Option> getValueFromCustomFieldParams(CustomFieldParams relevantParams) throws FieldValidationException
    {
        if (relevantParams != null && !relevantParams.isEmpty())
        {
            return getOptionMapFromCustomFieldParams(relevantParams);
        }
        else
        {
            return null;
        }

    }

    public Object getStringValueFromCustomFieldParams(CustomFieldParams parameters)
    {
        return parameters;
    }

    // -------------------------------------------------------------------------------------------------------- Defaults

    @Nullable
    public Map<String, Option> getDefaultValue(FieldConfig fieldConfig)
    {
        final Object o = genericConfigManager.retrieve(DEFAULT_VALUE_TYPE, fieldConfig.getId().toString());
        if (o != null)
        {
            final CustomFieldParams params = new CustomFieldParamsImpl(fieldConfig.getCustomField(), o);
            return getOptionMapFromCustomFieldParams(params);
        }
        else
        {
            return null;
        }
    }

    public void setDefaultValue(FieldConfig fieldConfig, Map<String, Option> cascadingOptions)
    {
        if (cascadingOptions != null)
        {
            final CustomFieldParams customFieldParams = new CustomFieldParamsImpl(fieldConfig.getCustomField(), cascadingOptions);
            customFieldParams.transformObjectsToStrings();
            customFieldParams.setCustomField(null);

            genericConfigManager.update(DEFAULT_VALUE_TYPE, fieldConfig.getId().toString(), customFieldParams);
        }
        else
        {
            genericConfigManager.update(DEFAULT_VALUE_TYPE, fieldConfig.getId().toString(), null);
        }
    }

    // --------------------------------------------------------------------------------------------------  Miscellaneous

    public String getChangelogValue(CustomField field, Map<String, Option> cascadingOptions)
    {
        if (cascadingOptions != null)
        {
            StringBuilder sb = new StringBuilder();
            sb.append("Parent values: ");
            Option parent = cascadingOptions.get(PARENT_KEY);
            sb.append(parent.getValue()).append("(").append(parent.getOptionId()).append(")");
            Option child = cascadingOptions.get(CHILD_KEY);
            if (child != null)
            {
                sb.append("Level ").append(CHILD_KEY).append(" values: ");
                sb.append(child.getValue()).append("(").append(child.getOptionId()).append(")");
            }
            return sb.toString();
        }
        else
        {
            return "";
        }
    }

    public String getStringFromSingularObject(Option optionObject)
    {
        if (optionObject != null)
        {
            return optionObject.getOptionId().toString();
        }
        else
        {
            log.warn("Object passed '" + optionObject + "' is not an Option but is null");
            return null;
        }
    }

    public Option getSingularObjectFromString(String string) throws FieldValidationException
    {
        return getOptionFromStringValue(string);
    }

    @NotNull
    public List<FieldConfigItemType> getConfigurationItemTypes()
    {
        final List<FieldConfigItemType> configurationItemTypes = super.getConfigurationItemTypes();
        configurationItemTypes.add(new SettableOptionsConfigItem(this, optionsManager) {
            @Override
            public String getBaseEditUrl()
            {
                return "EditRateCustomFieldOptions!default.jspa";
            }
        });
        return configurationItemTypes;
    }

    @NotNull
    public Map<String, Object> getVelocityParameters(Issue issue, CustomField field, FieldLayoutItem fieldLayoutItem)
    {
        Map<String, Object> result = super.getVelocityParameters(issue, field, fieldLayoutItem);
        final HttpServletRequest request = ServletActionContext.getRequest();
        result.put("request", request);
        User worker = getWorkerFromIssue(issue);
        String fieldDescription = fieldLayoutItem.getFieldDescription();
        if (worker != null)
            fieldDescription += "<br>Оценка будет выставлена пользователю : " + worker.getDisplayName();
        result.put("fieldDescription", fieldDescription);
        return result;
    }

    //----------------------------------------------------------------------------------------- - Private Helper Methods

    private Map<String, Option> getOptionMapFromCustomFieldParams(CustomFieldParams params) throws FieldValidationException
    {
        Option parentOption = extractOptionFromParams(PARENT_KEY, params);
        Option childOption = extractOptionFromParams(CHILD_KEY, params);

        Map<String, Option> options = new HashMap<String, Option>();
        options.put(PARENT_KEY, parentOption);
        if (childOption != null)
        {
            options.put(CHILD_KEY, childOption);
        }

        return options;
    }

    @Nullable
    private Option extractOptionFromParams(String key, CustomFieldParams relevantParams) throws FieldValidationException
    {
        Collection<String> params = relevantParams.getValuesForKey(key);
        if (params != null && !params.isEmpty())
        {
            String selectValue = params.iterator().next();
            if (ObjectUtils.isValueSelected(selectValue) && selectValue != null)
            {
                return getOptionFromStringValue(selectValue);
            }
        }

        return null;
    }

    @Nullable
    private Option getOptionFromStringValue(String selectValue) throws FieldValidationException
    {
        final Long aLong = OptionUtils.safeParseLong(selectValue);
        if (aLong != null)
        {
            final Option option = optionsManager.findByOptionId(aLong);
            if (option != null)
            {
                return option;
            }
            else
            {
                throw new FieldValidationException("'" + aLong + "' is an invalid Option");
            }
        }
        else
        {
            throw new FieldValidationException("Value: '" + selectValue + "' is an invalid Option");
        }
    }

    @Nullable
    private Option getOptionValueForParentId(CustomField field, @Nullable String sParentOptionId, Issue issue)
    {
        //TODO pretty one
        /* Collection values;

        values = customFieldValuePersister.getValues(field, issue.getId(), CASCADE_VALUE_TYPE, sParentOptionId);


        if (values != null && !values.isEmpty())
        {
            String optionId = (String) values.iterator().next();
            return optionsManager.findByOptionId(OptionUtils.safeParseLong(optionId));
        }
        else
        {
            return null;
        }  */
        return null;
    }

    // -------------------------------------------------------------------------------------------------- Compare
    public int compare(@NotNull Map<String, Option> o1, @NotNull Map<String, Option> o2, FieldConfig fieldConfig)
    {
        Option option1 = o1.get(PARENT_KEY);
        Option option2 = o2.get(PARENT_KEY);

        int parentCompare = compareOption(option1, option2);
        if (parentCompare == 0)
        {
            // Compare child Options, if parents are the same
            Option childOption1 = o1.get(CHILD_KEY);
            Option childOption2 = o2.get(CHILD_KEY);

            return compareOption(childOption1, childOption2);
        }
        else
        {
            return parentCompare;
        }
    }

    public int compareOption(@Nullable Option option1, @Nullable Option option2)
    {
        if (option1 == null && option2 == null) return 0;
        else if (option1 == null) return -1;
        else if (option2 == null) return 1;
        else return option1.getSequence().compareTo(option2.getSequence());
    }

    public ProjectCustomFieldImporter getProjectImporter()
    {
        return this.projectCustomFieldImporter;
    }

    @Override
    public Object accept(VisitorBase visitor)
    {
        if (visitor instanceof Visitor)
        {
            return ((Visitor) visitor).visitCascadingSelect(this);
        }

        return super.accept(visitor);
    }

    public interface Visitor<T> extends VisitorBase<T>
    {
        T visitCascadingSelect(RateCFType cascadingSelectCustomFieldType);
    }

    private User getWorkerFromIssue(Issue issue) {
        String workerName = rateService.getWorker(issue.getKey());
        User worker = null;
        if (workerName != null) {
            worker = userManager.getUser(workerName);
        }
        if (worker == null) {
            worker = issue.getReporter();
            if (worker == null) {
                log.error("issue dont'have reporter : " + issue.getKey());
            }
        }
        return worker;
    }

    /* @Override
    public RateOption getSingularObjectFromString(final String string) throws FieldValidationException
    {
        List<String> vals = Arrays.asList(string.split(":"));
        Iterator<String> i = vals.iterator();
        String rate = i.next();
        if ("-1".equals(vals.iterator().next()))
        {
            return null;
        }
        Option reason = null;
        if (i.hasNext()) {
            reason = getOptionFromStringValue(i.next());
        }
        return new RateOption(getOptionFromStringValue(rate), reason);
    }    */

    public void setDefaultValue(final FieldConfig fieldConfig, final Option option)
    {
        Long id = null;
        if (option != null)
        {
            id = option.getOptionId();
        }
        genericConfigManager.update(CustomFieldType.DEFAULT_VALUE_TYPE, fieldConfig.getId().toString(), id);
    }


    private String createValidOptionsString(final Options options)
    {
        final List<Option> rootOptions = options.getRootOptions();
        final StringBuilder validOptions = new StringBuilder();

        for (Iterator<Option> optionIterator = rootOptions.iterator(); optionIterator.hasNext();)
        {
            Option option = optionIterator.next();
            validOptions.append(option.getOptionId()).append("[").append(option.getValue()).append("]");

            if (optionIterator.hasNext())
            {
                validOptions.append(", ");
            }
        }
        validOptions.append(", -1");
        return validOptions.toString();
    }

    public Query getQueryForGroup(final String fieldID, String groupName)
    {
        return new TermQuery(new Term(fieldID + SelectStatisticsMapper.RAW_VALUE_SUFFIX, groupName));
    }


}
