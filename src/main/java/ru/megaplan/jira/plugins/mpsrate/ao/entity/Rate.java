package ru.megaplan.jira.plugins.mpsrate.ao.entity;

import net.java.ao.Entity;
import net.java.ao.schema.AutoIncrement;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.PrimaryKey;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 3:02 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Rate extends Entity {

    @AutoIncrement
    @NotNull
    @PrimaryKey("ID")
    public int getID();

    @Indexed
    @NotNull
    String getIssueKey();
    void setIssueKey(String key);

    @Indexed
    @NotNull
    String getWho();
    void setWho(String who);

    @Indexed
    @NotNull
    String getWhom();
    void setWhom(String whom);

    @NotNull
    int getRating();
    void setRating(int rating);

    String getComment();
    void setComment(String comment);

    @NotNull
    Date getWhen();
    void setWhen(Date date);

}
