package ru.megaplan.jira.plugins.mpsrate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 30.08.12
 * Time: 17:24
 * To change this template use File | Settings | File Templates.
 */
public class TestMatcher {

    public static final Pattern WHERE_CLAUSE = Pattern.compile("(\\w+)(?=\\s*((=|!=|>|<|<>|<=|>=|(?<!(NOT\\s{1,10}))LIKE|(?<!(NOT\\s{1,10}))like|(?<!(NOT\\s{1,10}))BETWEEN|(?<!(NOT\\s{1,10}))between|IS|is|(?<!((IS|AND)\\s{1,10}))NOT|(?<!(NOT\\s{1,10}))IN|(?<!(is\\s{1,10}))not|(?<!(not\\s{1,10}))in)(\\s|\\()))");

    public static void main(String... args) {
        String where = "pepepe in (?)";
        final Matcher matcher = WHERE_CLAUSE.matcher(where);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find())
        {
            matcher.appendReplacement(sb, matcher.group());
        }
        matcher.appendTail(sb);
        System.out.println(sb);
    }
}
