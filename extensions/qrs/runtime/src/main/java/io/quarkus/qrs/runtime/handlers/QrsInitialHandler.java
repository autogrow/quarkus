package io.quarkus.qrs.runtime.handlers;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.qrs.runtime.core.QrsDeployment;
import io.quarkus.qrs.runtime.core.QrsRequestContext;
import io.quarkus.qrs.runtime.mapping.RequestMapper;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class QrsInitialHandler implements Handler<RoutingContext>, RestHandler {

    final RequestMapper<InitialMatch> mappers;
    final QrsDeployment deployment;
    final ResourceRequestInterceptorHandler preMappingHandler;
    final RestHandler[] initialChain;

    final CurrentVertxRequest currentVertxRequest;
    final ManagedContext requestContext;

    public QrsInitialHandler(RequestMapper<InitialMatch> mappers, QrsDeployment deployment,
            ResourceRequestInterceptorHandler preMappingHandler) {
        this.mappers = mappers;
        this.deployment = deployment;
        this.preMappingHandler = preMappingHandler;
        if (preMappingHandler == null) {
            initialChain = new RestHandler[] { this };
        } else {
            initialChain = new RestHandler[] { preMappingHandler, this };
        }
        this.requestContext = Arc.container().requestContext();
        this.currentVertxRequest = Arc.container().instance(CurrentVertxRequest.class).get();
    }

    @Override
    public void handle(RoutingContext event) {
        QrsRequestContext rq = new QrsRequestContext(deployment, event, requestContext, currentVertxRequest, initialChain);
        event.data().put(QrsRequestContext.CURRENT_REQUEST_KEY, rq);
        rq.run();
    }

    @Override
    public void handle(QrsRequestContext requestContext) throws Exception {
        RoutingContext event = requestContext.getContext();
        RequestMapper.RequestMatch<InitialMatch> target = mappers.map(requestContext.getPath());
        if (target == null) {
            event.next();
            return;
        }
        requestContext.restart(target.value.handlers);
        requestContext.setMaxPathParams(target.value.maxPathParams);
        requestContext.setRemaining(target.remaining);
        for (int i = 0; i < target.pathParamValues.length; ++i) {
            String pathParamValue = target.pathParamValues[i];
            if (pathParamValue == null) {
                break;
            }
            requestContext.setPathParamValue(i, target.pathParamValues[i]);
        }
    }

    public static class InitialMatch {
        public final RestHandler[] handlers;
        public final int maxPathParams;

        public InitialMatch(RestHandler[] handlers, int maxPathParams) {
            this.handlers = handlers;
            this.maxPathParams = maxPathParams;
        }
    }
}
