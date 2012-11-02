package ru.megaplan.jira.plugins.mpsrate.action;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.jira.exception.DataAccessException;
import com.atlassian.jira.issue.IssueFactory;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.converters.DatePickerConverter;
import com.atlassian.jira.issue.customfields.view.CustomFieldParams;
import com.atlassian.jira.issue.customfields.view.CustomFieldParamsImpl;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigSchemeManager;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import org.ofbiz.core.entity.GenericValue;
import webwork.action.ActionContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for handling the update of a custom field's values.
 */
@WebSudoRequired
public class EditCustomFieldDefaults extends AbstractEditConfigurationItemAction
{
    private Map<String, CustomFieldParams> fieldValuesHolder = new HashMap<String, CustomFieldParams>();
    private Long fieldConfigSchemeId;

    private final FieldConfigSchemeManager fieldConfigSchemeManager;
    private final FieldLayoutManager fieldLayoutManager;
    private final IssueFactory issueFactory;

    public EditCustomFieldDefaults(IssueFactory issueFactory, FieldLayoutManager fieldLayoutManager,
                                   FieldConfigSchemeManager fieldConfigSchemeManager)
    {
        this.issueFactory = issueFactory;
        this.fieldLayoutManager = fieldLayoutManager;
        this.fieldConfigSchemeManager = fieldConfigSchemeManager;
    }

    public String doDefault() throws Exception
    {
        populateDefaults(this.getFieldConfig(), fieldValuesHolder);

        return super.doDefault();
    }

    public static void populateDefaults(FieldConfig config, Map<String, CustomFieldParams> customFieldValuesHolder)
    {
        CustomField cf = config.getCustomField();
        Object defaultValues = cf.getCustomFieldType().getDefaultValue(config);

        CustomFieldParams paramsFromIssue;
        if (defaultValues != null)
        {
            paramsFromIssue = new CustomFieldParamsImpl(cf, defaultValues);
            paramsFromIssue.transformObjectsToStrings();
            customFieldValuesHolder.put(cf.getId(), paramsFromIssue);
        }
    }

    protected void doValidation()
    {
        final CustomField customField = getCustomField();
        final Map params = ActionContext.getParameters();

        customField.validateFromActionParams(params, this, getFieldConfig());
        if (hasAnyErrors())
        {
            customField.populateFromParams(fieldValuesHolder, ActionContext.getParameters());
        }
    }

    @RequiresXsrfCheck
    protected String doExecute() throws Exception
    {
        final CustomField customField = getCustomField();

        final Map map = new HashMap();
        Map actionParams = ActionContext.getContext().getParameters();

        // Nasty nasty hack to get "useCurrentDate" to work
        if ("true".equals(ParameterUtils.getStringParam(actionParams, "useCurrentDate")))
        {
            customField.getCustomFieldType().setDefaultValue(getFieldConfig(), DatePickerConverter.USE_NOW_DATE);
        }
        else
        {
            customField.populateFromParams(map, actionParams);
            final Object customFieldDefault = customField.getValueFromParams(map);
            customField.getCustomFieldType().setDefaultValue(getFieldConfig(), customFieldDefault);
        }

        return getRedirect("ViewCustomFields.jspa");
    }

    public String getCustomFieldHtml()
    {
        final CustomField customField = getCustomField();
        FieldConfig config = getFieldConfig();
        Map displayParameters = EasyMap.build("defaultScreen", "true",
                "objectValue", customField.getCustomFieldType().getDefaultValue(config));

        // Sees if we can set some values in the issue
        MutableIssue dummyIssue = getDummyIssue();

        FieldLayoutItem fieldLayoutItem = null;
        // See if we can get a field layout item using the dummy issue
        try
        {
            if(dummyIssue.getProjectObject() != null && dummyIssue.getIssueTypeObject() != null)
            {
                FieldLayout fieldLayout = fieldLayoutManager.getFieldLayout(dummyIssue.getProjectObject(), dummyIssue.getIssueTypeObject().getId());
                fieldLayoutItem = fieldLayout.getFieldLayoutItem(config.getCustomField().getId());
            }
        }
        catch(DataAccessException ex)
        {
            // warn the user and move on with a null FieldLayoutItem
            log.warn("Unable to resolve a field layout item when setting the default value of custom field with id: "
                    + config.getCustomField().getId(), ex);
        }

        return customField.getCustomFieldType().getDescriptor().getEditDefaultHtml(config, fieldValuesHolder, dummyIssue, this, displayParameters, fieldLayoutItem);
    }

    public Long getFieldConfigSchemeId()
    {
        return fieldConfigSchemeId;
    }

    public void setFieldConfigSchemeId(Long fieldConfigSchemeId)
    {
        this.fieldConfigSchemeId = fieldConfigSchemeId;
    }

    private MutableIssue getDummyIssue()
    {
        MutableIssue dummyIssue = issueFactory.getIssue();
        FieldConfigScheme fieldConfigScheme = fieldConfigSchemeManager.getFieldConfigScheme(getFieldConfigSchemeId());
        if (fieldConfigScheme != null)
        {
            Collection associatedProjects = fieldConfigScheme.getAssociatedProjects();
            if (associatedProjects != null && associatedProjects.size() == 1)
            {
                dummyIssue.setProject((GenericValue) associatedProjects.iterator().next());
            }

            Collection associatedIssueTypes = fieldConfigScheme.getAssociatedIssueTypes();
            if (associatedIssueTypes != null && associatedIssueTypes.size() == 1)
            {
                dummyIssue.setIssueType((GenericValue) associatedIssueTypes.iterator().next());
            }
        }
        return dummyIssue;
    }
}