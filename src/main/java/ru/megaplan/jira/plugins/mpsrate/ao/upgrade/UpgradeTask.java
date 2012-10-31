package ru.megaplan.jira.plugins.mpsrate.ao.upgrade;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.activeobjects.external.ActiveObjectsUpgradeTask;
import com.atlassian.activeobjects.external.ModelVersion;
import ru.megaplan.jira.plugins.mpsrate.ao.entity.Rate;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 7/28/12
 * Time: 5:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpgradeTask implements ActiveObjectsUpgradeTask {
    @Override
    public ModelVersion getModelVersion() {
        return ModelVersion.valueOf("12");
    }

    @Override
    public void upgrade(ModelVersion modelVersion, ActiveObjects activeObjects) {
        for (Rate rate : activeObjects.find(Rate.class)) {
            activeObjects.delete(rate);
        }
    }
}
