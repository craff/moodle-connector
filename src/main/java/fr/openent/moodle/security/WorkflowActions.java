package fr.openent.moodle.security;

import static fr.openent.moodle.Moodle.*;

public enum WorkflowActions {
    ACCESS_RIGHT (workflow_view),
    DUPLICATE_RIGHT (workflow_duplicate),
    CREATE_FOLDER_RIGHT (workflow_createFolder),
    PUBLICATE_RIGHT (workflow_publish),
    SYNCHRO_RIGHT (workflow_synchro);

    private final String actionName;

    WorkflowActions(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public String toString () {
        return this.actionName;
    }
}
