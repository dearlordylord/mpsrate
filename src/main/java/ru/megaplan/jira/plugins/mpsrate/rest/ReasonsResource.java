package ru.megaplan.jira.plugins.mpsrate.rest;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.util.UserManager;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mpsrate.customfield.RateCFType;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Path("/reasons")
public class ReasonsResource {

    private final static Logger log = Logger.getLogger(UserPickerResource.class);

    private final OptionsManager optionsManager;

    public ReasonsResource(OptionsManager optionsManager) {
        this.optionsManager = optionsManager;
    }

    @GET
    @Path ("/all")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAllStatuses(@Context HttpServletRequest request) {
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        List<CustomField> cfs = customFieldManager.getCustomFieldObjects();
        CustomField ratecf = null;
        for (CustomField c : cfs) {
            if (c.getCustomFieldType() instanceof RateCFType) {
                ratecf = c;
                break;
            }
        }
        if (ratecf == null) {
            log.error("can't find mps rate cf");
            return Response.ok().build();
        }
        Options options = optionsManager.getOptions(ratecf.getConfigurationSchemes().iterator().next().getOneAndOnlyConfig());
        List<Option> rootOptions = options.getRootOptions();
        List<ReasonResult> childOptions = new ArrayList<ReasonResult>();
        childOptions.add(new ReasonResult("-1", "Все причины"));
        for (Option o : rootOptions) {
            List<Option> cops = o.getChildOptions();
            if (!cops.isEmpty()) {
                for (Option co : cops)
                    childOptions.add(new ReasonResult(co.getOptionId().toString(), o.getValue()+": "+co.getValue()));
            }
        }

        return Response.ok(childOptions).build();
    }

    @XmlRootElement(name = "user")
    @XmlAccessorType(XmlAccessType.FIELD)
    public class ReasonResult {
        @XmlAttribute
        private String value;
        @XmlAttribute
        private String label;

        public ReasonResult(String id, String desc) {
            this.value = id;
            this.label = desc;
        }

        public String getKey() {
            return value;
        }

        public void setKey(String key) {
            this.value = key;
        }

        public String getName() {
            return label;
        }

        public void setName(String name) {
            this.label = name;
        }
    }

}