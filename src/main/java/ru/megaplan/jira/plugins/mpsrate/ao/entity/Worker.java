package ru.megaplan.jira.plugins.mpsrate.ao.entity;

import net.java.ao.Entity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.08.12
 * Time: 18:37
 * To change this template use File | Settings | File Templates.
 */
public interface Worker extends Entity  {
    @AutoIncrement
    @NotNull
    @PrimaryKey("ID")
    public int getID();

    @Indexed
    @NotNull
    String getIssueKey();
    void setIssueKey(String key);

    @NotNull
    String getWorkerUser();
    void setWorkerUser(String workerUser);

}
