package fr.openent.moodle.filters;

import fr.wseduc.webutils.http.Binding;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.user.UserInfos;

/**
 * Created by lugana on 01/03/2019.
 */
public class canShareResourceFilter implements ResourcesProvider {
    @Override
    public void authorize(HttpServerRequest request, Binding binding, UserInfos userInfos, Handler<Boolean> handler) {
        //TODO recuperer autorisations sur la resource (roles + propriétaire)
        handler.handle(true);
    }
}
