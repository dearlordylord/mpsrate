package ru.megaplan.jira.plugins.mpsrate.action;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 01.11.12
 * Time: 13:49
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractEditConfigurationItemAction extends JiraWebActionSupport
{
    private Long fieldConfigId;
    private FieldConfig fieldConfig;

    public void setFieldConfigId(Long fieldConfigId)
    {
        this.fieldConfigId = fieldConfigId;
    }

    public Long getFieldConfigId()
    {
        return fieldConfigId;
    }

    public FieldConfig getFieldConfig()
    {
        if (fieldConfig == null && fieldConfigId != null)
        {
            final FieldConfigManager fieldConfigManager = ComponentAccessor.getComponentOfType(FieldConfigManager.class);
            fieldConfig = fieldConfigManager.getFieldConfig(fieldConfigId);
        }

        return fieldConfig;
    }

    public CustomField getCustomField()
    {
        return getFieldConfig().getCustomField();
    }
}
