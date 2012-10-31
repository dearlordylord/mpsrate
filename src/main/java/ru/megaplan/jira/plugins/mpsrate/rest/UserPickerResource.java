package ru.megaplan.jira.plugins.mpsrate.rest;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.util.UserManager;
import org.apache.log4j.Logger;

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

@Path("/who")
public class UserPickerResource {

    private final static Logger log = Logger.getLogger(UserPickerResource.class);

    private final GroupManager groupManager;

    public UserPickerResource(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    @GET
    @Path ("/group/{group}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAllStatuses(@Context HttpServletRequest request,
                                   @PathParam("group") final String group) {
        Collection<User> users = groupManager.getUsersInGroup(group);
        if (users == null) {
            log.error("group : " + group + " is not found");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Collection<UserResult> result = new ArrayList<UserResult>();
        for (User user : users) {
            if (user.isActive()) {
                StringBuilder stringBuilder = new StringBuilder(user.getDisplayName());
                stringBuilder.append(' ').append('[').append(user.getName()).append(']');
                result.add(new UserResult(user.getName(),stringBuilder.toString()));
            }
        }
        return Response.ok(result).build();
    }

    @XmlRootElement(name = "user")
    @XmlAccessorType(XmlAccessType.FIELD)
    public class UserResult {
        @XmlAttribute
        private String value;
        @XmlAttribute
        private String label;

        public UserResult(String login, String fullname) {
            this.value = login;
            this.label = fullname;
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