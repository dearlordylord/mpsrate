package ru.megaplan.jira.plugins.mpsrate.customfield;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserPickerSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.imports.project.customfield.ProjectCustomFieldImporter;
import com.atlassian.jira.imports.project.customfield.ProjectImportableCustomField;
import com.atlassian.jira.imports.project.customfield.UserCustomFieldImporter;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comparator.UserBestNameComparator;
import com.atlassian.jira.issue.customfields.SortableCustomField;
import com.atlassian.jira.issue.customfields.converters.UserConverter;
import com.atlassian.jira.issue.customfields.impl.AbstractSingleFieldType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.customfields.persistence.PersistenceFieldType;
import com.atlassian.jira.issue.customfields.view.CustomFieldParams;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.UserField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
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
import com.atlassian.jira.issue.fields.rest.json.beans.JiraBaseUrls;
import com.atlassian.jira.issue.fields.rest.json.beans.UserJsonBean;
import com.atlassian.jira.notification.type.UserCFNotificationTypeAware;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.ErrorCollection.Reason;
import com.atlassian.jira.util.NotNull;
import com.atlassian.plugin.webresource.WebResourceManager;
import ru.megaplan.jira.plugins.mpsrate.ao.RateService;
import ru.megaplan.jira.plugins.permission.manager.ao.MegaPermissionGroupManager;
import ru.megaplan.jira.plugins.permission.manager.ao.bean.mock.IPermissionGroupMock;
import ru.megaplan.jira.plugins.permission.manager.ao.bean.mock.IPermissionMock;

import javax.annotation.Nullable;
import java.util.*;

/**
 * This field extends standart Userpicker cf but uses Active Objects. It is necessary cause we can't read one issue customfield within another customfield's getter.
 *
 */
public class WorkerCfType extends AbstractSingleFieldType<User> implements SortableCustomField<User>, UserCFNotificationTypeAware, ProjectImportableCustomField, UserField, RestAwareCustomFieldType, RestCustomFieldTypeOperations
{
    private final ProjectCustomFieldImporter userCustomFieldImporter = new UserCustomFieldImporter();
    private final UserConverter userConverter;
    private final ApplicationProperties applicationProperties;
    private final JiraAuthenticationContext authenticationContext;
    private final UserPickerSearchService searchService;
    private final JiraBaseUrls jiraBaseUrls;
    private final RateService rateService;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final ProjectRoleManager projectRoleManager;
    private final HashSet<String> allowedProjectRoles;

    private final static String WORKERCFTYPEPERMISSIONGROUP = "ru.megaplan.jira.plugins.mpsrate.workerCfType.ACCEPTED";

    public WorkerCfType(final CustomFieldValuePersister customFieldValuePersister, final UserConverter userConverter, final GenericConfigManager genericConfigManager, final ApplicationProperties applicationProperties, final JiraAuthenticationContext authenticationContext, final UserPickerSearchService searchService, JiraBaseUrls jiraBaseUrls, RateService rateService, JiraAuthenticationContext jiraAuthenticationContext, MegaPermissionGroupManager megaPermissionGroupManager, ProjectRoleManager projectRoleManager)
    {
        super(customFieldValuePersister, genericConfigManager);
        this.userConverter = userConverter;
        this.applicationProperties = applicationProperties;
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.jiraBaseUrls = jiraBaseUrls;
        this.rateService = rateService;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.projectRoleManager = projectRoleManager;
        List<IPermissionMock> mocks = megaPermissionGroupManager.getPermissionGroup(WORKERCFTYPEPERMISSIONGROUP).getPermissions();
        allowedProjectRoles = new HashSet<String>();
        if (mocks != null) {
            Iterator<IPermissionMock> it = mocks.iterator();
            while (it.hasNext()) {
                IPermissionMock iPermissionMock = it.next();
                if (iPermissionMock.getProjectRoleName() != null) {
                    allowedProjectRoles.add(iPermissionMock.getProjectRoleName());
                }
            }
        }
    }

    @Override
    public void createValue(CustomField field, Issue issue, User value) {
        super.createValue(field, issue, value);
        if (value != null)
            rateService.setWorker(issue.getKey(), value.getName());

    }

    @Override
    public void updateValue(CustomField customField, Issue issue, User value) {
        super.updateValue(customField, issue, value);
        if (value != null)
            rateService.setWorker(issue.getKey(), value.getName());
    }

    @Override
    protected Object getDbValueFromObject(final User customFieldObject)
    {
        return getStringFromSingularObject(customFieldObject);
    }

    @Override
    protected User getObjectFromDbValue(@NotNull final Object databaseValue) throws FieldValidationException
    {
        return userConverter.getUserEvenWhenUnknown((String) databaseValue);
    }

    public String getStringFromSingularObject(final User value)
    {
        return userConverter.getString(value);
    }

    public User getSingularObjectFromString(final String string) throws FieldValidationException
    {
        return userConverter.getUser(string);
    }

    @Override
    public void validateFromParams(final CustomFieldParams relevantParams, final ErrorCollection errorCollectionToAddTo, final FieldConfig config)
    {
        try
        {
            super.getValueFromCustomFieldParams(relevantParams);
        }
        catch (final FieldValidationException e)
        {
            errorCollectionToAddTo.addError(config.getCustomField().getId(), e.getMessage(), Reason.VALIDATION_FAILED);
        }
    }

    @Override
    public User getValueFromCustomFieldParams(final CustomFieldParams relevantParams) throws FieldValidationException
    {
        User value = null;
        try
        {
            value = super.getValueFromCustomFieldParams(relevantParams);
        }
        catch (final FieldValidationException e)
        {
            //ignore
        }

        return value;

    }

    public int compare(@NotNull final User customFieldObjectValue1, @NotNull final User customFieldObjectValue2, final FieldConfig fieldConfig)
    {
        return new UserBestNameComparator(authenticationContext.getLocale()).compare(customFieldObjectValue1, customFieldObjectValue2);
    }

    @NotNull
    @Override
    protected PersistenceFieldType getDatabaseType()
    {
        return PersistenceFieldType.TYPE_LIMITED_TEXT;
    }

    @NotNull
    @Override
    public Map<String, Object> getVelocityParameters(final Issue issue, final CustomField field, final FieldLayoutItem fieldLayoutItem)
    {
        final Map<String, Object> velocityParams = super.getVelocityParameters(issue, field, fieldLayoutItem);

        final JiraServiceContext ctx = new JiraServiceContextImpl(authenticationContext.getLoggedInUser());

        final boolean canPerformAjaxSearch = searchService.canPerformAjaxSearch(ctx);
        if (canPerformAjaxSearch)
        {
            velocityParams.put("canPerformAjaxSearch", "true");
        }
        final WebResourceManager webResourceManager = ComponentAccessor.getComponent(WebResourceManager.class);
        webResourceManager.requireResource("jira.webresources:autocomplete");
        velocityParams.put("ajaxLimit", applicationProperties.getDefaultBackedString(APKeys.JIRA_AJAX_AUTOCOMPLETE_LIMIT));

        User initiator = jiraAuthenticationContext.getLoggedInUser();
        if (initiator != null && hasPermissions(initiator, issue)) {
            velocityParams.put("canEdit", "true");
        }
        return velocityParams;
    }

    private boolean hasPermissions(User initiator, Issue issue) {
        Collection<ProjectRole> userProjectRoles = projectRoleManager.getProjectRoles(initiator, issue.getProjectObject());
        for (ProjectRole projectRole : userProjectRoles) {
            if (allowedProjectRoles.contains(projectRole.getName())) return true;
        }
        return false;
    }

    public ProjectCustomFieldImporter getProjectImporter()
    {
        return userCustomFieldImporter;
    }

    @Override
    public Object accept(VisitorBase visitor)
    {
        if (visitor instanceof Visitor) {
            return ((Visitor) visitor).visitUser(this);
        }

        return super.accept(visitor);
    }

    public interface Visitor<T> extends VisitorBase<T>
    {
        T visitUser(WorkerCfType userCustomFieldType);
    }

    @Override
    public FieldTypeInfo getFieldTypeInfo(FieldTypeInfoContext fieldTypeInfoContext)
    {
        final String userPickerAutoCompleteUrl = String.format("%s/rest/api/1.0/users/picker?fieldName=%s&query=", jiraBaseUrls.baseUrl(), fieldTypeInfoContext.getOderableField().getId());
        return new FieldTypeInfo(null, userPickerAutoCompleteUrl);
    }

    @Override
    public JsonType getJsonSchema(CustomField customField)
    {
        return JsonTypeBuilder.custom(JsonType.USER_TYPE, getKey(), customField.getIdAsLong());
    }

    @Override
    public FieldJsonRepresentation getJsonFromIssue(CustomField field, Issue issue, boolean renderedVersionRequested, @Nullable FieldLayoutItem fieldLayoutItem)
    {
        return new FieldJsonRepresentation(new JsonData(UserJsonBean.shortBean(getValueFromIssue(field, issue), jiraBaseUrls)));
    }

    @Override
    public RestFieldOperationsHandler getRestFieldOperation(CustomField field)
    {
        return new UserCustomFieldOperationsHandler(field, getI18nBean());
    }
}